package com.ripple.chat.island

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.Toast
import com.ripple.chat.MainActivity
import com.ripple.chat.data.SmsRepository
import com.ripple.chat.data.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IslandActions {

    fun copyOtpCode(context: Context, code: String, notificationId: Int) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("OTP Code", code)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                com.ripple.chat.ui.components.BromeToast.show(context, "OTP $code copied")
            }
            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAsRead(context: Context, address: String, notificationId: Int, threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val repo = SmsRepository(context)
            val targetThreadId = if (threadId > 0) threadId else resolveThreadId(context, address)
            if (targetThreadId > 0) {
                repo.markAsRead(targetThreadId)
            }
            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteSpam(context: Context, address: String, notificationId: Int, threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val targetThreadId = if (threadId > 0) threadId else resolveThreadId(context, address)
            if (targetThreadId > 0) {
                val trashManager = TrashManager(context)
                trashManager.softDelete(targetThreadId)
            }
            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun sendReply(context: Context, address: String, body: String, notificationId: Int) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val targetThreadId = resolveThreadId(context, address)

            // Save sent SMS to Telephony Provider
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
                if (targetThreadId > 0) {
                    put(Telephony.Sms.THREAD_ID, targetThreadId)
                }
            }

            try {
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                try {
                    context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
                } catch (_: Exception) {}
            }

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            context.sendBroadcast(Intent("com.ripple.chat.NEW_SMS").apply {
                setPackage(context.packageName)
            })

            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openConversation(context: Context, address: String, threadId: Long, notificationId: Int) {
        try {
            val targetThreadId = if (threadId > 0) threadId else resolveThreadId(context, address)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (targetThreadId > 0) {
                    putExtra("open_thread_id", targetThreadId)
                }
            }
            context.startActivity(intent)
            cancelNotification(context, notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        try {
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveThreadId(context: Context, address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            -1L
        }
    }
}
