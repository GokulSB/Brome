package com.ripple.chat.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_COPY_OTP = "com.ripple.chat.ACTION_COPY_OTP"
        const val ACTION_DISMISS = "com.ripple.chat.ACTION_DISMISS"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_COPY_OTP -> {
                val otpCode = intent.getStringExtra(EXTRA_OTP_CODE)
                if (!otpCode.isNullOrEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Code", otpCode)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        com.ripple.chat.ui.components.BromeToast.show(context, "OTP $otpCode copied")
                    }
                }
                cancelNotification(context, notificationId)
            }
            ACTION_DISMISS -> {
                cancelNotification(context, notificationId)
            }
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}
