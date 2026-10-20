package com.ripple.chat.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.ContactsContract
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SmsCategory {
    PERSONAL, PROMOTIONAL, FINANCE, OTP, TICKETS, REMINDERS, SUBSCRIPTIONS, SHOPPING, IMPORTANT, SPAM, OTHERS
}

data class ChatThread(
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    var contactName: String? = null,
    var contactPhotoUri: String? = null,
    var category: SmsCategory = SmsCategory.PERSONAL,
    var unreadCount: Int = 0,
    var deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    var lastMessageIsFromMe: Boolean = false,
    var hasOutgoing: Boolean = false
)

class SmsRepository(private val context: Context) {

    private val nonSpamPrefs = context.getSharedPreferences("non_spam_prefs", Context.MODE_PRIVATE)
    private val readStatusPrefs = context.getSharedPreferences("brome_read_status_prefs", Context.MODE_PRIVATE)

    suspend fun getConversations(filterThreadIds: Set<Long>? = null): List<ChatThread> = withContext(Dispatchers.IO) {
        val threads = mutableListOf<ChatThread>()
        
        // Find all thread IDs and canonical address keys that have outgoing messages
        val outgoingThreads = mutableSetOf<Long>()
        val outgoingCanonicalKeys = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.TYPE} != ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                null
            )?.use { oCursor ->
                val otIdIdx = oCursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val oAddrIdx = oCursor.getColumnIndex(Telephony.Sms.ADDRESS)
                while (oCursor.moveToNext()) {
                    if (otIdIdx != -1) {
                        val tid = oCursor.getLong(otIdIdx)
                        if (tid > 0) outgoingThreads.add(tid)
                    }
                    if (oAddrIdx != -1) {
                        val addr = oCursor.getString(oAddrIdx)
                        if (!addr.isNullOrBlank()) {
                            val cKey = com.ripple.chat.sms.AddressNormalizer.canonicalKey(addr)
                            if (cKey.isNotBlank()) outgoingCanonicalKeys.add(cKey)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Query all SMS ordered by date DESC so the first row per thread is the latest message
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS
        )
        
        val customKeywords = CategoryKeywordManager.getAllKeywords(context)
        val nonSpamThreadIds = nonSpamPrefs.getStringSet("non_spam_threads", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        val nonSpamAddresses = nonSpamPrefs.getStringSet("non_spam_addresses", emptySet()) ?: emptySet()

        val unreadCountsByThread = mutableMapOf<Long, Int>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.READ} = 0",
                null,
                null
            )?.use { uCursor ->
                val tidIdx = uCursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                if (tidIdx != -1) {
                    while (uCursor.moveToNext()) {
                        val tId = uCursor.getLong(tidIdx)
                        unreadCountsByThread[tId] = (unreadCountsByThread[tId] ?: 0) + 1
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal, unread counts will fallback to 1 if unread
        }

        val seenThreadIds = mutableSetOf<Long>()
        val threadsByCanonicalKey = mutableMapOf<String, ChatThread>()

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS)

                while (cursor.moveToNext()) {
                    val rawAddress = if (addressIdx != -1) cursor.getString(addressIdx) else null
                    if (rawAddress.isNullOrBlank()) continue
                    val cleanAddress = com.ripple.chat.utils.MessageParser.cleanAddress(rawAddress) ?: rawAddress
                    val canonicalKey = com.ripple.chat.sms.AddressNormalizer.canonicalKey(cleanAddress)

                    var threadId = if (threadIdIdx != -1) cursor.getLong(threadIdIdx) else 0L
                    if (threadId <= 0L) {
                        threadId = try {
                            Telephony.Threads.getOrCreateThreadId(context, setOf(cleanAddress))
                        } catch (_: Exception) {
                            if (idIdx != -1) cursor.getLong(idIdx) else 0L
                        }
                    }

                    if (filterThreadIds != null && threadId !in filterThreadIds) continue

                    val typeVal = if (typeIdx != -1) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                    val isSent = typeVal != Telephony.Sms.MESSAGE_TYPE_INBOX
                    val statusVal = if (statusIdx != -1 && !cursor.isNull(statusIdx)) cursor.getInt(statusIdx) else -1
                    val deliveryStatus = when {
                        !isSent -> MessageDeliveryStatus.DELIVERED
                        statusVal == Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryStatus.DELIVERED
                        statusVal == Telephony.Sms.STATUS_PENDING || typeVal == Telephony.Sms.MESSAGE_TYPE_OUTBOX -> MessageDeliveryStatus.PENDING
                        statusVal == Telephony.Sms.STATUS_FAILED || typeVal == Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageDeliveryStatus.FAILED
                        else -> MessageDeliveryStatus.SENT
                    }

                    val hasOutgoing = isSent || threadId in outgoingThreads || (canonicalKey.isNotBlank() && canonicalKey in outgoingCanonicalKeys)

                    // Merge threads with matching canonicalKey (e.g. +91 98765... vs 98765...)
                    if (canonicalKey.isNotBlank() && threadsByCanonicalKey.containsKey(canonicalKey)) {
                        val existing = threadsByCanonicalKey[canonicalKey]!!
                        val rowUnread = if (typeVal == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            val dbRead = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true
                            if (!dbRead) 1 else 0
                        } else 0
                        if (rowUnread > 0 && !existing.read) {
                            existing.unreadCount += rowUnread
                        }
                        if (hasOutgoing) {
                            existing.hasOutgoing = true
                        }
                        continue
                    }

                    if (!seenThreadIds.add(threadId)) continue

                    val body = if (bodyIdx != -1) cursor.getString(bodyIdx) ?: "" else ""
                    val threadDate = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    val dbRead = if (readIdx != -1) cursor.getInt(readIdx) == 1 else true

                    val isNonSpam = threadId in nonSpamThreadIds || cleanAddress in nonSpamAddresses || rawAddress in nonSpamAddresses
                    val category = com.ripple.chat.utils.CategoryClassifier.classify(
                        cleanAddress,
                        body,
                        null,
                        customKeywords,
                        isExplicitlyNotSpam = isNonSpam
                    )

                    // Evaluate persistent read override: sent messages are always read, never counted towards unread badge
                    var isRead = if (isSent) true else dbRead
                    if (!isSent && readStatusPrefs.contains("read_state_$threadId")) {
                        val overrideRead = readStatusPrefs.getBoolean("read_state_$threadId", dbRead)
                        val readTime = readStatusPrefs.getLong("read_time_$threadId", 0L)
                        if (overrideRead) {
                            if (readTime > 0L && threadDate > readTime) {
                                isRead = false
                            } else {
                                isRead = true
                            }
                        } else {
                            isRead = false
                        }
                    }

                    val unreadCount = if (isRead || isSent) {
                        0
                    } else {
                        val count = unreadCountsByThread[threadId] ?: 0
                        if (count > 0) count else 1
                    }

                    val newThread = ChatThread(
                        threadId = threadId,
                        address = cleanAddress,
                        body = body,
                        date = threadDate,
                        read = isRead,
                        type = typeVal,
                        category = category,
                        unreadCount = unreadCount,
                        deliveryStatus = deliveryStatus,
                        lastMessageIsFromMe = isSent,
                        hasOutgoing = hasOutgoing
                    )
                    threads.add(newThread)
                    if (canonicalKey.isNotBlank()) {
                        threadsByCanonicalKey[canonicalKey] = newThread
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Attempt to fetch contact names and photos via single fast batch query
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val numberCache = mutableMapOf<String, Pair<String, String?>>()
            
            try {
                val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val phoneProj = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                )
                context.contentResolver.query(phoneUri, phoneProj, null, null, null)?.use { cursor ->
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val normIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

                    while (cursor.moveToNext()) {
                        val rawNumber = if (numIdx != -1) cursor.getString(numIdx) else null
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                        val photo = if (photoIdx != -1) cursor.getString(photoIdx) else null
                        val normalized = if (normIdx != -1) cursor.getString(normIdx) else null

                        if (rawNumber.isNullOrBlank() || name.isNullOrBlank()) continue
                        val pair = Pair(name, photo)

                        val clean = com.ripple.chat.utils.MessageParser.cleanAddress(rawNumber) ?: rawNumber.trim()
                        val digits = clean.filter { it.isDigit() }
                        val last10 = if (digits.length >= 10) digits.takeLast(10) else digits

                        numberCache[rawNumber] = pair
                        numberCache[rawNumber.trim()] = pair
                        numberCache[clean] = pair
                        if (digits.isNotBlank()) {
                            numberCache[digits] = pair
                        }
                        if (last10.length >= 7) {
                            numberCache[last10] = pair
                        }
                        if (!normalized.isNullOrBlank()) {
                            numberCache[normalized] = pair
                            val normDigits = normalized.filter { it.isDigit() }
                            if (normDigits.isNotBlank()) {
                                numberCache[normDigits] = pair
                            }
                        }
                        if (digits.length == 10) {
                            numberCache["+91$digits"] = pair
                            numberCache["91$digits"] = pair
                            numberCache["0$digits"] = pair
                        } else if (digits.length == 12 && digits.startsWith("91")) {
                            numberCache["+$digits"] = pair
                            numberCache[digits.substring(2)] = pair
                        } else if (digits.length == 11 && digits.startsWith("0")) {
                            numberCache[digits.substring(1)] = pair
                            numberCache["+91${digits.substring(1)}"] = pair
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            threads.forEach { thread ->
                val cleanAddr = com.ripple.chat.utils.MessageParser.cleanAddress(thread.address) ?: thread.address
                val parts = cleanAddr.split(Regex("[,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    // Group/multi-recipient thread: resolve each number and join names
                    val resolvedNames = parts.map { number ->
                        val cleanPart = com.ripple.chat.utils.MessageParser.cleanAddress(number) ?: number
                        val pDigits = cleanPart.filter { it.isDigit() }
                        val pLast10 = if (pDigits.length >= 10) pDigits.takeLast(10) else pDigits
                        numberCache[number]?.first 
                            ?: numberCache[cleanPart]?.first 
                            ?: numberCache[pDigits]?.first 
                            ?: (if (pLast10.length >= 7) numberCache[pLast10]?.first else null) 
                            ?: number
                    }
                    thread.contactName = resolvedNames.joinToString(", ")
                    // Use the first contact's photo as the thread avatar
                    thread.contactPhotoUri = parts.firstNotNullOfOrNull {
                        val cleanPart = com.ripple.chat.utils.MessageParser.cleanAddress(it) ?: it
                        val pDigits = cleanPart.filter { it.isDigit() }
                        val pLast10 = if (pDigits.length >= 10) pDigits.takeLast(10) else pDigits
                        numberCache[it]?.second 
                            ?: numberCache[cleanPart]?.second 
                            ?: numberCache[pDigits]?.second 
                            ?: (if (pLast10.length >= 7) numberCache[pLast10]?.second else null)
                    }
                } else {
                    // Single-address thread
                    val pureDigits = cleanAddr.filter { it.isDigit() }
                    val last10 = if (pureDigits.length >= 10) pureDigits.takeLast(10) else pureDigits
                    val digitsWithPlus = if (cleanAddr.startsWith("+")) "+$pureDigits" else pureDigits

                    val match = numberCache[thread.address] 
                        ?: numberCache[cleanAddr] 
                        ?: numberCache[digitsWithPlus] 
                        ?: numberCache[pureDigits] 
                        ?: (if (last10.length >= 7) numberCache[last10] else null)
                        ?: (if (pureDigits.length == 10) numberCache["+91$pureDigits"] ?: numberCache["91$pureDigits"] else null)
                        ?: (if (pureDigits.length == 12 && pureDigits.startsWith("91")) numberCache["+$pureDigits"] ?: numberCache[pureDigits.substring(2)] else null)

                    match?.let { (name, photoUri) ->
                        thread.contactName = name
                        thread.contactPhotoUri = photoUri
                    }
                }
            }
        }
        
        // Re-evaluate category for all threads with resolved contactName and phone number heuristics
        threads.forEach { thread ->
            val cleanAddr = com.ripple.chat.utils.MessageParser.cleanAddress(thread.address) ?: thread.address
            val isNonSpam = thread.threadId in nonSpamThreadIds || cleanAddr in nonSpamAddresses || thread.address in nonSpamAddresses
            val isPhoneNum = !thread.address.any { it.isLetter() } && thread.address.filter { it.isDigit() }.length >= 7
            val hasResolvedContact = !thread.contactName.isNullOrBlank() && thread.contactName != thread.address && thread.contactName != cleanAddr
            val isAlphanumeric = com.ripple.chat.sms.AddressNormalizer.isAlphanumericSender(thread.address)

            // Rule: A conversation whose latest message is outgoing, or that contains any outgoing message
            // to a personal number, must appear in Recent and in Personal.
            thread.category = if ((thread.hasOutgoing || thread.lastMessageIsFromMe) && !isAlphanumeric) {
                SmsCategory.PERSONAL
            } else if (hasResolvedContact) {
                SmsCategory.PERSONAL
            } else {
                val classified = com.ripple.chat.utils.CategoryClassifier.classify(
                    thread.address,
                    thread.body,
                    thread.contactName,
                    customKeywords,
                    isExplicitlyNotSpam = isNonSpam
                )
                if (classified == SmsCategory.OTHERS && isPhoneNum) {
                    SmsCategory.PERSONAL
                } else {
                    classified
                }
            }
        }
        
        return@withContext threads
    }

    fun getNonSpamThreadIds(): Set<Long> {
        return nonSpamPrefs.getStringSet("non_spam_threads", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    fun getNonSpamAddresses(): Set<String> {
        return nonSpamPrefs.getStringSet("non_spam_addresses", emptySet()) ?: emptySet()
    }

    fun markAsNonSpam(threadIds: Set<Long>, addresses: Set<String> = emptySet()) {
        val currentIds = getNonSpamThreadIds().toMutableSet()
        currentIds.addAll(threadIds)
        val currentAddrs = getNonSpamAddresses().toMutableSet()
        currentAddrs.addAll(addresses.filter { it.isNotBlank() })
        nonSpamPrefs.edit()
            .putStringSet("non_spam_threads", currentIds.map { it.toString() }.toSet())
            .putStringSet("non_spam_addresses", currentAddrs)
            .apply()
    }

    fun unmarkAsNonSpam(threadIds: Set<Long>, addresses: Set<String> = emptySet()) {
        val currentIds = getNonSpamThreadIds().toMutableSet()
        currentIds.removeAll(threadIds)
        val currentAddrs = getNonSpamAddresses().toMutableSet()
        currentAddrs.removeAll(addresses)
        nonSpamPrefs.edit()
            .putStringSet("non_spam_threads", currentIds.map { it.toString() }.toSet())
            .putStringSet("non_spam_addresses", currentAddrs)
            .apply()
    }

    suspend fun deleteConversation(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://sms/conversations/$threadId")
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            readStatusPrefs.edit()
                .putBoolean("read_state_$threadId", true)
                .putLong("read_time_$threadId", System.currentTimeMillis())
                .apply()

            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            val uri = Uri.parse("content://sms/conversations/$threadId")
            context.contentResolver.update(uri, values, "${Telephony.Sms.READ} = 0", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAllAsRead(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            val editor = readStatusPrefs.edit()
            val now = System.currentTimeMillis()
            threadIds.forEach { tid ->
                editor.putBoolean("read_state_$tid", true)
                editor.putLong("read_time_$tid", now)
            }
            editor.apply()

            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            threadIds.forEach { threadId ->
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
                val uri = Uri.parse("content://sms/conversations/$threadId")
                context.contentResolver.update(uri, values, "${Telephony.Sms.READ} = 0", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAsUnread(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            readStatusPrefs.edit()
                .putBoolean("read_state_$threadId", false)
                .remove("read_time_$threadId")
                .apply()

            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 1",
                arrayOf(threadId.toString())
            )
            val uri = Uri.parse("content://sms/conversations/$threadId")
            context.contentResolver.update(uri, values, "${Telephony.Sms.READ} = 1", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAllAsUnread(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        try {
            val editor = readStatusPrefs.edit()
            threadIds.forEach { tid ->
                editor.putBoolean("read_state_$tid", false)
                editor.remove("read_time_$tid")
            }
            editor.apply()

            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            }
            threadIds.forEach { threadId ->
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 1",
                    arrayOf(threadId.toString())
                )
                val uri = Uri.parse("content://sms/conversations/$threadId")
                context.contentResolver.update(uri, values, "${Telephony.Sms.READ} = 1", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}






