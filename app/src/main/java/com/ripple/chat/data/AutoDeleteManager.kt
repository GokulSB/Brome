package com.ripple.chat.data

import android.content.Context
import android.provider.Telephony
import com.ripple.chat.utils.OtpParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object AutoDeleteManager {

    /**
     * Executes auto-delete rules for OTP, Spam, and Recycle Bin cleanup.
     *
     * @param context Application context
     * @param bromeSettings Optional BromeSettings instance
     * @param forcedOtpTime If set, uses this threshold instead of saved preference
     * @param forcedSpamTime If set, uses this threshold instead of saved preference
     * @param forcedTrashTime If set, uses this threshold instead of saved preference
     * @return Number of threads moved to trash
     */
    suspend fun executeAutoDelete(
        context: Context,
        bromeSettings: BromeSettings? = null,
        forcedOtpTime: OtpAutoDeleteTime? = null,
        forcedSpamTime: SpamAutoDeleteTime? = null,
        forcedTrashTime: TrashAutoDeleteTime? = null
    ): Int = withContext(Dispatchers.IO) {
        var processedCount = 0
        try {
            val settings = bromeSettings ?: BromeSettings(context)
            val appearance = settings.appearanceFlow.first()
            val trashManager = TrashManager(context)
            val repository = SmsRepository(context)

            val otpSetting = forcedOtpTime ?: appearance.otpAutoDelete
            val spamSetting = forcedSpamTime ?: appearance.spamAutoDelete
            val trashSetting = forcedTrashTime ?: appearance.trashAutoDelete

            val now = System.currentTimeMillis()
            val conversations = repository.getConversations()
            val starredPrefs = context.getSharedPreferences("starred_prefs", Context.MODE_PRIVATE)
            val starredIds = starredPrefs.getStringSet("starred_ids", emptySet())
                ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

            // 1. Auto-delete OTP messages/threads
            if (otpSetting != OtpAutoDeleteTime.NEVER) {
                val maxOtpAgeMs = otpSetting.hours * 3600 * 1000L
                val expiredOtpThreads = conversations.filter { thread ->
                    if (thread.threadId in starredIds || trashManager.isDeleted(thread.threadId)) return@filter false
                    val isOtp = thread.category == SmsCategory.OTP || OtpParser.parseOtp(thread.body, thread.date) != null
                    val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                    isOtp && (now - msgTime >= maxOtpAgeMs)
                }

                if (expiredOtpThreads.isNotEmpty()) {
                    val ids = expiredOtpThreads.map { it.threadId }
                    trashManager.softDelete(ids)
                    processedCount += ids.size
                }
            }

            // 2. Auto-delete Spam messages/threads
            if (spamSetting != SpamAutoDeleteTime.NEVER) {
                val maxSpamAgeMs = spamSetting.days * 24 * 3600 * 1000L
                val expiredSpamThreads = conversations.filter { thread ->
                    if (thread.threadId in starredIds || trashManager.isDeleted(thread.threadId)) return@filter false
                    val isSpam = thread.category == SmsCategory.SPAM
                    val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
                    isSpam && (now - msgTime >= maxSpamAgeMs)
                }

                if (expiredSpamThreads.isNotEmpty()) {
                    val ids = expiredSpamThreads.map { it.threadId }
                    trashManager.softDelete(ids)
                    processedCount += ids.size
                }
            }

            // 3. Clean old trash from Recycle Bin
            val trashAgeMs = trashSetting.days * 24 * 3600 * 1000L
            trashManager.cleanupOldTrashWithCustomAge(repository, trashAgeMs)

            // 4. If default SMS app, also purge individual expired OTP / Spam messages from content://sms
            try {
                val isDefault = context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
                if (isDefault) {
                    if (otpSetting != OtpAutoDeleteTime.NEVER) {
                        val maxOtpAgeMs = otpSetting.hours * 3600 * 1000L
                        context.contentResolver.query(
                            Telephony.Sms.CONTENT_URI,
                            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                            "${Telephony.Sms.DATE} <= ? AND ${Telephony.Sms.TYPE} = ?",
                            arrayOf((now - maxOtpAgeMs).toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                            null
                        )?.use { c ->
                            val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                            val tidIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                            val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                            val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                            val idsToDelete = mutableListOf<Long>()
                            while (c.moveToNext()) {
                                val tid = c.getLong(tidIdx)
                                if (tid in starredIds) continue
                                val addr = if (addrIdx != -1) c.getString(addrIdx) ?: "" else ""
                                val body = c.getString(bodyIdx) ?: ""
                                val date = c.getLong(dateIdx)
                                val isOtp = OtpParser.parseOtp(body, date) != null ||
                                        com.ripple.chat.utils.CategoryClassifier.classify(addr, body) == SmsCategory.OTP
                                if (isOtp) {
                                    idsToDelete.add(c.getLong(idIdx))
                                }
                            }
                            if (idsToDelete.isNotEmpty()) {
                                idsToDelete.chunked(50).forEach { chunk ->
                                    val placeholders = chunk.joinToString(",") { "?" }
                                    context.contentResolver.delete(
                                        Telephony.Sms.CONTENT_URI,
                                        "${Telephony.Sms._ID} IN ($placeholders)",
                                        chunk.map { it.toString() }.toTypedArray()
                                    )
                                }
                            }
                        }
                    }

                    if (spamSetting != SpamAutoDeleteTime.NEVER) {
                        val maxSpamAgeMs = spamSetting.days * 24 * 3600 * 1000L
                        val customKeywords = CategoryKeywordManager.getAllKeywords(context)
                        val nonSpamThreadIds = repository.getNonSpamThreadIds()
                        val nonSpamAddresses = repository.getNonSpamAddresses()
                        context.contentResolver.query(
                            Telephony.Sms.CONTENT_URI,
                            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                            "${Telephony.Sms.DATE} <= ? AND ${Telephony.Sms.TYPE} = ?",
                            arrayOf((now - maxSpamAgeMs).toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                            null
                        )?.use { c ->
                            val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                            val tidIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                            val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                            val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                            val idsToDelete = mutableListOf<Long>()
                            while (c.moveToNext()) {
                                val tid = c.getLong(tidIdx)
                                if (tid in starredIds || tid in nonSpamThreadIds) continue
                                val addr = c.getString(addrIdx) ?: ""
                                if (addr in nonSpamAddresses) continue
                                val body = c.getString(bodyIdx) ?: ""
                                val cat = com.ripple.chat.utils.CategoryClassifier.classify(
                                    address = addr,
                                    body = body,
                                    contactName = null,
                                    customKeywords = customKeywords,
                                    isExplicitlyNotSpam = false
                                )
                                if (cat == SmsCategory.SPAM) {
                                    idsToDelete.add(c.getLong(idIdx))
                                }
                            }
                            if (idsToDelete.isNotEmpty()) {
                                idsToDelete.chunked(50).forEach { chunk ->
                                    val placeholders = chunk.joinToString(",") { "?" }
                                    context.contentResolver.delete(
                                        Telephony.Sms.CONTENT_URI,
                                        "${Telephony.Sms._ID} IN ($placeholders)",
                                        chunk.map { it.toString() }.toTypedArray()
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 5. Notify UI of any changes
            try {
                val broadcastIntent = android.content.Intent("com.ripple.chat.NEW_SMS").setPackage(context.packageName)
                context.sendBroadcast(broadcastIntent)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext processedCount
    }
}
