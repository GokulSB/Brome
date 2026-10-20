package com.ripple.chat.sms

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle incoming SMS
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle incoming MMS
    }
}

class HeadlessSmsSendService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE || action == "android.intent.action.RESPOND_VIA_MESSAGE") {
            val uri = intent.data
            val recipient = uri?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)

            if (!recipient.isNullOrBlank() && !body.isNullOrBlank()) {
                val sender = SmsSender(applicationContext)
                serviceScope.launch {
                    try {
                        sender.send(recipient, body)
                    } catch (e: Exception) {
                        android.util.Log.e("BromeSend", "Headless send error", e)
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_REDELIVER_INTENT
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
