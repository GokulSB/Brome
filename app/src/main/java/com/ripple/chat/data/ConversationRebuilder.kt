package com.ripple.chat.data

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.ripple.chat.sms.AddressNormalizer
import com.ripple.chat.utils.MessageParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ConversationRebuilder {
    private const val TAG = "ConversationRebuilder"
    private var hasRun = false

    /**
     * Idempotent conversation rebuilder. Scans existing messages in the SMS provider,
     * groups by canonicalKey, revives any erroneously soft-deleted threads that have recent messages,
     * and logs the rebuilt state.
     */
    suspend fun rebuild(context: Context, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (hasRun && !force) return@withContext 0
        hasRun = true

        val trashManager = TrashManager(context)
        val deletedMap = trashManager.deletedThreads.value

        var totalMessages = 0
        val threadsByCanonicalKey = mutableMapOf<String, Long>() // canonicalKey -> latestDate
        val threadIdsByCanonicalKey = mutableMapOf<String, Long>() // canonicalKey -> threadId

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                Telephony.Sms.DEFAULT_SORT_ORDER
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    totalMessages++
                    val rawAddress = if (addressIdx != -1) cursor.getString(addressIdx) else null
                    if (rawAddress.isNullOrBlank()) continue

                    val cleanAddress = MessageParser.cleanAddress(rawAddress) ?: rawAddress
                    val canonicalKey = AddressNormalizer.canonicalKey(cleanAddress)
                    if (canonicalKey.isBlank()) continue

                    val date = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                    var threadId = if (threadIdIdx != -1) cursor.getLong(threadIdIdx) else 0L
                    if (threadId <= 0L && idIdx != -1) {
                        threadId = cursor.getLong(idIdx)
                    }

                    if (!threadsByCanonicalKey.containsKey(canonicalKey)) {
                        threadsByCanonicalKey[canonicalKey] = date
                        threadIdsByCanonicalKey[canonicalKey] = threadId
                    }

                    // If this threadId was soft-deleted, check if message is newer than deletion timestamp
                    if (threadId > 0 && deletedMap.containsKey(threadId)) {
                        val deletionTime = deletedMap[threadId] ?: 0L
                        if (date > deletionTime) {
                            Log.d(TAG, "Reviving thread $threadId (new message date $date > deletion time $deletionTime)")
                            trashManager.restore(threadId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running ConversationRebuilder", e)
        }

        val totalConversations = threadsByCanonicalKey.size
        Log.i(TAG, "ConversationRebuilder: rebuilt $totalConversations conversations from $totalMessages messages")
        return@withContext totalConversations
    }
}
