package com.ripple.chat.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.ripple.chat.receiver.ReminderReceiver

class ReminderAlarmManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    fun setRepeatingSnooze(threadIds: List<Long>, intervalMinutes: Int) {
        val intervalMillis = intervalMinutes * 60 * 1000L
        
        // Save the currently snoozed threads
        val existingSnoozed = prefs.getString("snoozed_threads", "")?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        existingSnoozed.addAll(threadIds.map { it.toString() })
        
        prefs.edit()
            .putString("snoozed_threads", existingSnoozed.joinToString(","))
            .apply()

        for (threadId in threadIds) {
            prefs.edit().putInt("snooze_interval_$threadId", intervalMinutes).apply()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SHOW_REMINDER
                putExtra("threadId", threadId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                threadId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set exact alarm starting now + interval
            val triggerAtMillis = SystemClock.elapsedRealtime() + intervalMillis
            
            val canScheduleExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun cancelSnooze(threadIds: List<Long>) {
        val existingSnoozed = prefs.getString("snoozed_threads", "")?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        
        for (threadId in threadIds) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SHOW_REMINDER
                putExtra("threadId", threadId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                threadId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            existingSnoozed.remove(threadId.toString())
            prefs.edit().remove("snooze_interval_$threadId").apply()
        }
        
        prefs.edit()
            .putString("snoozed_threads", existingSnoozed.joinToString(","))
            .apply()
    }

    fun getSnoozeInterval(threadId: Long): Int? {
        val interval = prefs.getInt("snooze_interval_$threadId", -1)
        return if (interval != -1) interval else null
    }
}
