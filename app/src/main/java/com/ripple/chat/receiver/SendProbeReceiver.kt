package com.ripple.chat.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import com.ripple.chat.ui.debug.SendProbeManager

class SendProbeReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BromeSend"
        const val ACTION_PROBE_SENT = "com.ripple.chat.ACTION_PROBE_SENT"
        const val ACTION_PROBE_DELIVERED = "com.ripple.chat.ACTION_PROBE_DELIVERED"
        const val ACTION_TRIGGER_PROBE_A = "com.ripple.chat.TRIGGER_PROBE_A"
        const val ACTION_TRIGGER_PROBE_B = "com.ripple.chat.TRIGGER_PROBE_B"
        const val ACTION_TRIGGER_PROBE_C = "com.ripple.chat.TRIGGER_PROBE_C"
        const val ACTION_TRIGGER_PROBE_D = "com.ripple.chat.TRIGGER_PROBE_D"
        const val EXTRA_PROBE_BUTTON = "extra_probe_button"
        const val EXTRA_SUB_ID = "extra_sub_id"
        const val EXTRA_DESTINATION = "extra_destination"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val destinationExtra = intent.getStringExtra("destination") ?: intent.getStringExtra(EXTRA_DESTINATION) ?: ""
        val textExtra = intent.getStringExtra("text") ?: "Brome send probe test"
        val subIdExtra = if (intent.hasExtra("sub_id")) intent.getIntExtra("sub_id", -1) else intent.getIntExtra(EXTRA_SUB_ID, -1)

        when (action) {
            ACTION_TRIGGER_PROBE_A -> {
                SendProbeManager.refreshSubscriptions(context)
                val sub = if (subIdExtra > 0) subIdExtra else SendProbeManager.selectedSubId.value
                SendProbeManager.runProbeA(context, destinationExtra, textExtra, sub)
                return
            }
            ACTION_TRIGGER_PROBE_B -> {
                SendProbeManager.refreshSubscriptions(context)
                val sub = if (subIdExtra > 0) subIdExtra else SendProbeManager.selectedSubId.value
                SendProbeManager.runProbeB(context, destinationExtra, textExtra, sub, buttonLabel = "B")
                return
            }
            ACTION_TRIGGER_PROBE_C -> {
                SendProbeManager.refreshSubscriptions(context)
                val sub = if (subIdExtra > 0) subIdExtra else SendProbeManager.selectedSubId.value
                SendProbeManager.runProbeC(context, destinationExtra, textExtra, sub)
                return
            }
            ACTION_TRIGGER_PROBE_D -> {
                SendProbeManager.runProbeD(context, destinationExtra, textExtra)
                return
            }
        }

        val button = intent.getStringExtra(EXTRA_PROBE_BUTTON) ?: "Unknown"
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
        val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""

        when (action) {
            ACTION_PROBE_SENT -> {
                val resultCode = resultCode
                val errorReason = when (resultCode) {
                    Activity.RESULT_OK -> "RESULT_OK"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "RESULT_ERROR_GENERIC_FAILURE (1)"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "RESULT_ERROR_RADIO_OFF (2)"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "RESULT_ERROR_NULL_PDU (3)"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "RESULT_ERROR_NO_SERVICE (4)"
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "RESULT_ERROR_LIMIT_EXCEEDED (5)"
                    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED (7)"
                    SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED (8)"
                    else -> "RESULT_ERROR ($resultCode)"
                }

                val logLine = "Probe $button SENT callback: subId=$subId, dest='$destination' [hex: ${SendProbeManager.toHex(destination)}], code=$resultCode ($errorReason)"
                Log.d(TAG, logLine)
                SendProbeManager.onProbeSent(button, resultCode, errorReason, logLine)
            }

            ACTION_PROBE_DELIVERED -> {
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
                        Log.w(TAG, "Probe delivery PDU parse error", e)
                        null
                    }
                } else null

                val status = sms?.status ?: 0
                val statusText = when (status) {
                    0 -> "STATUS_COMPLETE (Delivered)"
                    32 -> "STATUS_PENDING"
                    64 -> "STATUS_FAILED"
                    else -> "STATUS_CODE ($status)"
                }

                val logLine = "Probe $button DELIVERED callback: subId=$subId, dest='$destination', status=$status ($statusText)"
                Log.d(TAG, logLine)
                SendProbeManager.onProbeDelivered(button, status, statusText, logLine)
            }
        }
    }
}
