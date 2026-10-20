package com.ripple.chat.sms

import android.Manifest
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ripple.chat.receiver.SmsStatusReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class SendResult {
    data class Queued(val messageId: Long, val threadId: Long) : SendResult()
    data class Failed(val messageId: Long, val reason: String, val throwable: Throwable? = null) : SendResult()
    object InvalidNumber : SendResult()
    object EmptyBody : SendResult()
    object NoPermission : SendResult()
    object NotDefaultApp : SendResult()
}

class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "BromeSend"
    }

    suspend fun send(
        rawTo: String,
        body: String,
        subId: Int? = null,
        retryMessageId: Long? = null
    ): SendResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "User initiated send to raw recipient: '$rawTo' (retryId=$retryMessageId)")

        if (body.isBlank()) {
            Log.w(TAG, "Send aborted: message body is blank")
            return@withContext SendResult.EmptyBody
        }

        val to = AddressNormalizer.normalize(context, rawTo)
        if (to == null) {
            Log.w(TAG, "Send aborted: recipient '$rawTo' is invalid or alphanumeric sender")
            return@withContext SendResult.InvalidNumber
        }
        Log.d(TAG, "Normalized recipient address: $to")

        val hasSendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "SEND_SMS permission check: $hasSendSms")
        if (!hasSendSms) {
            return@withContext SendResult.NoPermission
        }

        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }
        Log.d(TAG, "Default SMS app (ROLE_SMS) check: $isDefault")
        if (!isDefault) {
            return@withContext SendResult.NotDefaultApp
        }

        val defaultSubId = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SubscriptionManager.getDefaultSmsSubscriptionId()
            } else -1
        } catch (_: Exception) { -1 }

        val effectiveSubId = subId?.takeIf { it >= 0 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: defaultSubId.takeIf { it >= 0 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        Log.d(TAG, "Resolved subscription ID: $effectiveSubId")

        val threadId = try {
            Telephony.Threads.getOrCreateThreadId(context, setOf(to))
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving threadId for $to", e)
            0L
        }
        Log.d(TAG, "Resolved thread ID: $threadId")

        val now = System.currentTimeMillis()
        var msgId: Long = retryMessageId ?: -1L

        // Step 1: Save FIRST via MessageIngest as OUTBOX/PENDING
        if (msgId <= 0L) {
            val ingestResult = com.ripple.chat.data.MessageIngest.ingestMessage(
                context = context,
                rawAddress = to,
                body = body,
                messageDate = now,
                isFromMe = true,
                subId = effectiveSubId,
                predefinedThreadId = threadId
            )
            msgId = ingestResult?.messageId ?: now
            Log.d(TAG, "Ingested outgoing message via MessageIngest: msgId=$msgId, threadId=$threadId")
        } else {
            // Reusing existing message row for retry
            try {
                val updateValues = ContentValues().apply {
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
                    put(Telephony.Sms.DATE, now)
                }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    updateValues,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(msgId.toString())
                )
                Log.d(TAG, "Reused existing message row $msgId for retry, reset to OUTBOX/PENDING")
            } catch (e: Exception) {
                Log.w(TAG, "Could not update existing message row $msgId", e)
            }
        }

        // Revive soft-deleted thread in TrashManager if needed
        if (threadId > 0) {
            val tm = com.ripple.chat.data.TrashManager(context)
            if (tm.isDeleted(threadId)) {
                tm.restore(threadId)
            }
        }

        // Broadcast NEW_SMS immediately so Recent cards and Personal lists display the sending conversation
        try {
            context.sendBroadcast(Intent("com.ripple.chat.NEW_SMS").apply {
                setPackage(context.packageName)
                putExtra("address", to)
                putExtra("thread_id", threadId)
            })
        } catch (_: Exception) {}

        // Reset tracking state for this message ID in SmsStatusReceiver
        SmsStatusReceiver.messageTrackers.remove(msgId)
        SmsStatusReceiver.completedParts.remove(msgId)
        SmsStatusReceiver.failureReasons.remove(msgId)

        // Step 2: Acquire SmsManager
        val sm = getSmsManager(context, effectiveSubId)

        // Clean destination: only digits with optional leading '+'
        val cleanDestination = if (to.startsWith("+")) {
            "+" + to.drop(1).filter { it.isDigit() }
        } else {
            to.filter { it.isDigit() }
        }
        val destHex = cleanDestination.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "Destination: '$cleanDestination' [hex: $destHex], subId=$effectiveSubId")

        // Step 3: Divide message & send with explicit callbacks
        val parts = sm.divideMessage(body)
        val totalParts = parts.size
        Log.d(TAG, "SmsManager divideMessage yielded $totalParts part(s) for length=${body.length}")

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        try {
            if (totalParts <= 1) {
                val requestCode = ((msgId.hashCode() and 0x7FFFFFFF) % 500000) * 2 + 1000
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(context, SmsStatusReceiver::class.java).apply {
                        action = SmsStatusReceiver.ACTION_SMS_SENT
                        putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, msgId)
                        putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, 0)
                        putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, 1)
                        putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
                        putExtra(SmsStatusReceiver.EXTRA_ADDRESS, cleanDestination)
                    },
                    pendingIntentFlags
                )
                val deliveryIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + 1,
                    Intent(context, SmsStatusReceiver::class.java).apply {
                        action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                        putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, msgId)
                        putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, 0)
                        putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, 1)
                        putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
                        putExtra(SmsStatusReceiver.EXTRA_ADDRESS, cleanDestination)
                    },
                    pendingIntentFlags
                )

                Log.d(TAG, "About to call sm.sendTextMessage to $cleanDestination [hex: $destHex], msgId=$msgId")
                sm.sendTextMessage(cleanDestination, null, body, sentIntent, deliveryIntent)
                Log.d(TAG, "sm.sendTextMessage returned successfully for msgId=$msgId")
            } else {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    val partReq = (((msgId xor (i.toLong() shl 16)).hashCode() and 0x7FFFFFFF) % 500000) * 2 + 1000
                    val sIntent = PendingIntent.getBroadcast(
                        context,
                        partReq,
                        Intent(context, SmsStatusReceiver::class.java).apply {
                            action = SmsStatusReceiver.ACTION_SMS_SENT
                            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, msgId)
                            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                            putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
                            putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
                            putExtra(SmsStatusReceiver.EXTRA_ADDRESS, cleanDestination)
                        },
                        pendingIntentFlags
                    )
                    val dIntent = PendingIntent.getBroadcast(
                        context,
                        partReq + 1,
                        Intent(context, SmsStatusReceiver::class.java).apply {
                            action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                            putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, msgId)
                            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, i)
                            putExtra(SmsStatusReceiver.EXTRA_TOTAL_PARTS, totalParts)
                            putExtra(SmsStatusReceiver.EXTRA_THREAD_ID, threadId)
                            putExtra(SmsStatusReceiver.EXTRA_ADDRESS, cleanDestination)
                        },
                        pendingIntentFlags
                    )
                    sentIntents.add(sIntent)
                    deliveryIntents.add(dIntent)
                }

                Log.d(TAG, "About to call sm.sendMultipartTextMessage to $cleanDestination [hex: $destHex], msgId=$msgId, parts=$totalParts")
                sm.sendMultipartTextMessage(cleanDestination, null, parts, sentIntents, deliveryIntents)
                Log.d(TAG, "sm.sendMultipartTextMessage returned successfully for msgId=$msgId")
            }
            SendResult.Queued(msgId, threadId)
        } catch (e: Exception) {
            Log.e(TAG, "SmsManager dispatch failed for msgId=$msgId", e)
            try {
                val failValues = ContentValues().apply {
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED)
                    put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_FAILED)
                }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    failValues,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(msgId.toString())
                )
            } catch (_: Exception) {}
            SendResult.Failed(msgId, e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun getSmsManager(context: Context, subId: Int?): SmsManager {
        return if (subId != null && subId >= 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sm = context.getSystemService(SmsManager::class.java)
                sm?.createForSubscriptionId(subId) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }
}
