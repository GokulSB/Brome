# Notification Paths in Brome

This document catalogs every notification call site, builder, and foreground service trigger across the Brome codebase as investigated for the Dynamic Island notification coordination.

---

## 1. Inventory of Notification Call Sites

### A. Incoming SMS Notification
- **File**: `app/src/main/java/com/ripple/chat/receiver/SmsReceiver.kt`
- **Lines**: 77–128
- **Call site**:
  - Line 77: `NotificationCompat.Builder(context, CHANNEL_ID)` (Public lockscreen notification)
  - Line 84: `NotificationCompat.Builder(context, CHANNEL_ID)` (Main notification)
  - Line 128: `notificationManager.notify(NOTIFICATION_ID, builder.build())`
- **When it runs**: Executed on broadcast `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` or `SMS_DELIVER_ACTION` immediately after receiving an incoming SMS and inserting it into the Telephony provider.
- **Root Cause of Duplicate**: `SmsReceiver.kt` unconditionally posted the standard notification at line 128 and subsequently called `IslandNotifier.onMessageReceived(message)` at line 146. This caused both the Dynamic Island overlay and the system notification to appear concurrently for the same incoming message.
- **Action Taken**: Replaced direct `notificationManager.notify` with unified decision gate `MessageNotifier.onMessageReceived(m)`. Standard notification is only posted when `IslandGate` decides `Delivery.Standard` or when the Island fails to attach within 1500ms.

---

### B. Dynamic Island Foreground Service
- **File**: `app/src/main/java/com/ripple/chat/island/DynamicIslandService.kt`
- **Lines**: 406–423
- **Call site**:
  - Line 406: `val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)`
  - Line 416: `ServiceCompat.startForeground(this, FGS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)`
  - Line 423: `startForeground(FGS_NOTIFICATION_ID, notification)`
- **When it runs**: Called during `DynamicIslandService.onCreate()` to satisfy Android system requirements for running a foreground service (type `shortService`) while the overlay window is attached to `WindowManager`.
- **Classification**: Internal Android system FGS notification on channel `brome_island_channel` (`IMPORTANCE_MIN`, silent, no badge). It is **not** a user-facing message notification.

---

### C. Scheduled Reminder Alarms
- **File**: `app/src/main/java/com/ripple/chat/receiver/ReminderReceiver.kt`
- **Lines**: 64–73
- **Call site**:
  - Line 64: `val builder = NotificationCompat.Builder(context, CHANNEL_ID)`
  - Line 73: `notificationManager.notify(threadId.hashCode(), builder.build())`
- **When it runs**: Fired only when an explicit user-scheduled snooze/reminder alarm expires (AlarmManager intent `ACTION_REMINDER`).
- **Classification**: Unrelated to incoming SMS/MMS messages. Not affected by incoming message delivery.

---

### D. Incoming MMS Receiver
- **File**: `app/src/main/java/com/ripple/chat/sms/SmsComponents.kt`
- **Line**: 15 (`class MmsReceiver : BroadcastReceiver()`)
- **When it runs**: Broadcast receiver stub for WAP_PUSH MMS. Currently does not construct or post any notifications.

---

### E. Background Workers & Auto-Delete
- **File**: `app/src/main/java/com/ripple/chat/data/AutoDeleteWorker.kt`
- **When it runs**: Periodic background worker (WorkManager) for scheduled message cleanup (OTP, Spam, Trash).
- **Classification**: Does not post notifications.

---

### F. Group Summaries, Conversation Shortcuts, and Bubbles
- Project search for `setGroup`, `ShortcutManager`, `setBubbleMetadata`, `BubbleMetadata`:
  - No group summaries, conversation shortcuts, or bubble metadata are created in the project.
  - When the island is chosen, no secondary shade notifications, shortcuts, or bubbles are created.

---

## 2. Notification Channels

| Channel ID | Name | Importance | Description |
|---|---|---|---|
| `ripplechat_sms_channel` | Messages | `IMPORTANCE_HIGH` | Standard incoming SMS notifications with sound, heads-up pop-up, and vibration. |
| `messages_silent` | Silent Messages | `IMPORTANCE_LOW` | Optional silent copy for Dynamic Island (no sound, no vibration, no heads-up, `setSilent(true)`). |
| `brome_island_channel` | Dynamic Island | `IMPORTANCE_MIN` | Internal foreground service notification for `DynamicIslandService`. |
| `reminder_channel` | Reminders | `IMPORTANCE_HIGH` | Scheduled reminder notifications. |

---

## 3. Resolution Summary

Every incoming message notification path now delegates exclusively to `MessageNotifier.onMessageReceived(m)`.
1. `IslandGate.decide(m)` evaluates all conditions prior to posting.
2. If `Delivery.Standard` -> Only the standard notification on `ripplechat_sms_channel` is posted.
3. If `Delivery.InApp` -> In-app banner is displayed without posting a system notification.
4. If `Delivery.Island` -> Island controller attaches the overlay.
   - If attached successfully within 1500ms -> Standard notification is **suppressed**. If `island_keep_silent_copy` is enabled, a low-priority silent copy is posted to `messages_silent`. The island plays sound/vibration according to `island_sound`.
   - If island attachment fails or times out -> Standard notification is posted as fallback so the message is never lost.
