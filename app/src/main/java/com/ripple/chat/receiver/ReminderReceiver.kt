package com.ripple.chat.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ripple.chat.MainActivity
import com.ripple.chat.R

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SHOW_REMINDER = "com.ripple.chat.ACTION_SHOW_REMINDER"
        const val REQUEST_CODE = 2001
        private const val CHANNEL_ID = "ripplechat_reminders_channel"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHOW_REMINDER) {
            val threadId = intent.getLongExtra("threadId", -1L)
            if (threadId != -1L) {
                showNotification(context, threadId)
                
                // Reschedule for the next interval to keep it repeating
                val reminderAlarmManager = com.ripple.chat.data.ReminderAlarmManager(context)
                val interval = reminderAlarmManager.getSnoozeInterval(threadId)
                if (interval != null) {
                    reminderAlarmManager.setRepeatingSnooze(listOf(threadId), interval)
                }
            }
        }
    }

    private fun showNotification(context: Context, threadId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for snoozed reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_thread_id", threadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentTitle("Message Reminder")
            .setContentText("You have a scheduled reminder for a message.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(threadId.hashCode(), builder.build())
    }
}
