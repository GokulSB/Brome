package com.ripple.chat.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.util.LruCache
import com.ripple.chat.receiver.SmsReceiver
import com.ripple.chat.sms.AddressNormalizer
import com.ripple.chat.utils.CategoryClassifier
import com.ripple.chat.utils.MessageParser

data class IngestResult(
    val messageId: Long,
    val threadId: Long,
    val canonicalKey: String,
    val address: String,
    val category: SmsCategory
)

object MessageIngest {
    private const val TAG = "MessageIngest"
    private val dedupeCache = LruCache<String, Long>(200)

    /**
     * Unified message ingestion pipeline:
     * 1. Sanitizes address, removes unicode directional markers, formats canonical key.
     * 2. Resolves or creates thread ID.
     * 3. De-duplicates incoming messages.
     * 4. Inserts into Telephony provider (Inbox or Outbox).
     * 5. Revives conversation if soft-deleted in TrashManager (isDeleted = false).
     * 6. Classifies category (outgoing is always PERSONAL; incoming is PERSONAL if contact name resolved or phone number).
     * 7. Broadcasts NEW_SMS to update UI.
     */
    fun ingestMessage(
        context: Context,
        rawAddress: String,
        body: String,
        messageDate: Long = System.currentTimeMillis(),
        isFromMe: Boolean,
        subId: Int? = null,
        predefinedThreadId: Long? = null
    ): IngestResult? {
        val sanitized = AddressNormalizer.cleanAndSanitize(rawAddress)
        if (sanitized.isBlank()) {
            Log.w(TAG, "Ingest aborted: blank address")
            return null
        }

        val cleanAddress = MessageParser.cleanAddress(sanitized) ?: sanitized
        val canonicalKey = AddressNormalizer.canonicalKey(cleanAddress)

        // De-duplication for incoming messages
        if (!isFromMe) {
            val dedupeKey = "${canonicalKey}_${body.hashCode()}_${messageDate / 3000}"
            synchronized(dedupeCache) {
                if (dedupeCache.get(dedupeKey) != null) {
                    Log.d(TAG, "Duplicate incoming message ignored: $dedupeKey")
                    return null
                }
                dedupeCache.put(dedupeKey, messageDate)
            }
        }

        // Resolve thread ID
        var threadId = if (predefinedThreadId != null && predefinedThreadId > 0) predefinedThreadId else 0L
        if (threadId <= 0L) {
            threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, setOf(cleanAddress))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve thread ID for $cleanAddress", e)
                0L
            }
        }

        val destinationAddress = if (isFromMe) {
            AddressNormalizer.normalize(context, cleanAddress) ?: cleanAddress
        } else {
            cleanAddress
        }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, destinationAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, messageDate)
            if (isFromMe) {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                if (subId != null && subId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                }
            } else {
                put(Telephony.Sms.DATE_SENT, messageDate)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            if (threadId > 0) {
                put(Telephony.Sms.THREAD_ID, threadId)
            }
        }

        val uriToInsert = if (isFromMe) Telephony.Sms.Outbox.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
        var insertedUri = try {
            context.contentResolver.insert(uriToInsert, values)
        } catch (e: Exception) {
            Log.w(TAG, "Direct insert to $uriToInsert failed, retrying without THREAD_ID", e)
            null
        }

        if (insertedUri == null) {
            values.remove(Telephony.Sms.THREAD_ID)
            insertedUri = try {
                context.contentResolver.insert(uriToInsert, values)
                    ?: context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert message into provider", e)
                null
            }
        }

        val messageId = insertedUri?.lastPathSegment?.toLongOrNull() ?: messageDate

        // If thread ID was not known before, re-fetch it from provider or generate it
        if (threadId <= 0L) {
            threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, setOf(cleanAddress))
            } catch (_: Exception) {
                messageId
            }
        }

        // Revive conversation if soft-deleted in TrashManager
        if (threadId > 0L) {
            val trashManager = TrashManager(context)
            if (trashManager.isDeleted(threadId)) {
                Log.d(TAG, "Reviving soft-deleted thread $threadId due to new message")
                trashManager.restore(threadId)
            }
        }

        // Resolve Contact Name & Categorization
        val contactName = if (!isFromMe) SmsReceiver.getContactName(context, cleanAddress) else null
        val isPhoneNum = !cleanAddress.any { it.isLetter() } && cleanAddress.filter { it.isDigit() }.length >= 7
        val hasResolvedContact = !contactName.isNullOrBlank() && contactName != cleanAddress && contactName != cleanAddress.filter { it.isDigit() }

        val category = if (isFromMe) {
            SmsCategory.PERSONAL
        } else if (hasResolvedContact) {
            SmsCategory.PERSONAL
        } else {
            val nonSpamPrefs = context.getSharedPreferences("non_spam_prefs", Context.MODE_PRIVATE)
            val nonSpamAddresses = nonSpamPrefs.getStringSet("non_spam_addresses", emptySet()) ?: emptySet()
            val nonSpamThreads = nonSpamPrefs.getStringSet("non_spam_threads", emptySet()) ?: emptySet()
            val isExplicitlyNotSpam = cleanAddress in nonSpamAddresses || threadId.toString() in nonSpamThreads

            val classified = CategoryClassifier.classify(
                cleanAddress,
                body,
                contactName,
                CategoryKeywordManager.getAllKeywords(context),
                isExplicitlyNotSpam = isExplicitlyNotSpam
            )
            if (classified == SmsCategory.OTHERS && isPhoneNum) {
                SmsCategory.PERSONAL
            } else {
                classified
            }
        }

        // Fire NEW_SMS broadcast to notify all active ViewModels
        try {
            context.sendBroadcast(Intent("com.ripple.chat.NEW_SMS").apply {
                setPackage(context.packageName)
                putExtra("address", cleanAddress)
                putExtra("thread_id", threadId)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting NEW_SMS", e)
        }

        return IngestResult(
            messageId = messageId,
            threadId = threadId,
            canonicalKey = canonicalKey,
            address = cleanAddress,
            category = category
        )
    }
}
