package com.ripple.chat.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Handles android.provider.Telephony.SMS_RECEIVED without requiring BROADCAST_SMS permission,
 * allowing Brome to receive incoming SMS even when running alongside another default SMS app or
 * when Android broadcasts SMS_RECEIVED.
 */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            SmsReceiver.processIncomingSms(context, intent)
        }
    }
}
