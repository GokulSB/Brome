package com.ripple.chat.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BromeSend"
        const val ACTION_SMS_SENT = "com.ripple.chat.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.ripple.chat.ACTION_SMS_DELIVERED"
        const val ACTION_SMS_STATUS_CHANGED = "com.ripple.chat.SMS_STATUS_CHANGED"

        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_DELIVERY_STATUS = "extra_delivery_status"
        const val EXTRA_ERROR_REASON = "extra_error_reason"

        // Tracks state machine per message
        val messageTrackers = ConcurrentHashMap<Long, com.ripple.chat.sms.SmsSendStateMachine.Tracker>()
        // Tracks multipart completion: messageId -> Set of completed part indices
        val completedParts = ConcurrentHashMap<Long, MutableSet<Int>>()
        // Tracks message failures for UI display: messageId -> error reason
        val failureReasons = ConcurrentHashMap<Long, String>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (msgId == -1L) return

        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1).coerceAtLeast(1)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""

        when (action) {
            ACTION_SMS_SENT -> {
                val resultCode = resultCode
                val tracker = messageTrackers.computeIfAbsent(msgId) {
                    com.ripple.chat.sms.SmsSendStateMachine.Tracker(totalParts)
                }
                val newState = tracker.onPartSent(partIndex, resultCode)
                val errorReason = tracker.failureReason
                if (resultCode == Activity.RESULT_OK) {
                    val partsSet = completedParts.computeIfAbsent(msgId) { ConcurrentHashMap.newKeySet() }
                    partsSet.add(partIndex)
                }

                Log.d(TAG, "Sent callback received for msgId=$msgId, part $partIndex/$totalParts, resultCode=$resultCode ($errorReason), newState=$newState")

                when (newState) {
                    com.ripple.chat.sms.SmsSendStateMachine.MessageState.FAILED -> {
                        failureReasons[msgId] = errorReason ?: "Send failed"
                        updateProviderStatus(context, msgId, Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED)
                        notifyStatusChanged(context, msgId, "FAILED", errorReason ?: "Send failed")
                    }
                    com.ripple.chat.sms.SmsSendStateMachine.MessageState.SENT -> {
                        failureReasons.remove(msgId)
                        Log.d(TAG, "All $totalParts parts successfully sent for msgId=$msgId")
                        updateProviderStatus(context, msgId, Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE)
                        notifyStatusChanged(context, msgId, "SENT", null)
                    }
                    else -> {
                        // Still SENDING, waiting for other parts
                    }
                }
            }

            ACTION_SMS_DELIVERED -> {
                val pdu = intent.getByteArrayExtra("pdu")
                val format = intent.getStringExtra("format") ?: "3gpp"
                val sms = if (pdu != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(pdu, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing delivery PDU", e)
                        null
                    }
                } else null

                val status = sms?.status ?: Telephony.Sms.STATUS_COMPLETE
                Log.d(TAG, "Delivery report received for msgId=$msgId, status=$status")

                val tracker = messageTrackers[msgId]
                val newState = tracker?.onDeliveryReport(status) ?: if (status == Telephony.Sms.STATUS_COMPLETE) com.ripple.chat.sms.SmsSendStateMachine.MessageState.DELIVERED else null

                if (newState == com.ripple.chat.sms.SmsSendStateMachine.MessageState.DELIVERED || status == Telephony.Sms.STATUS_COMPLETE) {
                    updateProviderStatus(context, msgId, Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_COMPLETE)
                    notifyStatusChanged(context, msgId, "DELIVERED", null)
                }
            }
        }
    }

    private fun updateProviderStatus(context: Context, msgId: Long, type: Int, status: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.TYPE, type)
                    put(Telephony.Sms.STATUS, status)
                }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(msgId.toString())
                )
                Log.d(TAG, "Updated provider row msgId=$msgId to type=$type, status=$status")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update provider status for msgId=$msgId", e)
            }
        }
    }

    private fun notifyStatusChanged(context: Context, msgId: Long, status: String, errorReason: String?) {
        try {
            context.sendBroadcast(Intent(ACTION_SMS_STATUS_CHANGED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_MESSAGE_ID, msgId)
                putExtra(EXTRA_DELIVERY_STATUS, status)
                if (errorReason != null) {
                    putExtra(EXTRA_ERROR_REASON, errorReason)
                }
            })
            // Also notify NEW_SMS to update ChatsViewModel thread list
            context.sendBroadcast(Intent("com.ripple.chat.NEW_SMS").apply {
                setPackage(context.packageName)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast status change for msgId=$msgId", e)
        }
    }
}
