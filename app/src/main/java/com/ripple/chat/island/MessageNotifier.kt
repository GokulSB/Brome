package com.ripple.chat.island

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.ripple.chat.MainActivity
import com.ripple.chat.R
import com.ripple.chat.data.IslandSettings
import com.ripple.chat.data.IslandSound
import com.ripple.chat.receiver.NotificationActionReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Delivery {
    data object Island : Delivery
    data object InApp : Delivery
    data object Standard : Delivery
}

interface AppForegroundState {
    val isForeground: Boolean
}

class ProcessLifecycleAppState : AppForegroundState {
    override val isForeground: Boolean
        get() = try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            false
        }
}

open class IslandGate(
    private val ctx: Context? = null,
    private val settings: IslandSettings,
    private val appState: AppForegroundState,
    private val overlayPermissionChecker: (Context?) -> Boolean = { c -> c != null && Settings.canDrawOverlays(c) },
    private val powerManagerChecker: (Context?) -> Boolean = { c -> c?.getSystemService(PowerManager::class.java)?.isInteractive ?: true },
    private val keyguardChecker: (Context?) -> Boolean = { c -> c?.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false },
    private val dndFilterProvider: (Context?) -> Int = { c -> c?.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL },
    private val orientationProvider: (Context?) -> Int = { c -> c?.resources?.configuration?.orientation ?: Configuration.ORIENTATION_PORTRAIT }
) {
    fun decide(m: Message): Delivery {
        if (!settings.enabled || !overlayPermissionChecker(ctx)) return Delivery.Standard
        val categoryKey = m.islandCategory.key
        if (categoryKey !in settings.showFor && m.category.name !in settings.showFor) return Delivery.Standard
        if (!powerManagerChecker(ctx) || keyguardChecker(ctx)) return Delivery.Standard
        if (settings.respectDnd) {
            val filter = dndFilterProvider(ctx)
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                return Delivery.Standard
            }
        }
        if (orientationProvider(ctx) == Configuration.ORIENTATION_LANDSCAPE) return Delivery.Standard
        if (appState.isForeground && settings.skipWhenOpen) return Delivery.InApp
        return Delivery.Island
    }
}

fun interface IslandController {
    suspend fun show(m: Message): Boolean
}

fun interface StandardNotifier {
    fun post(m: Message)
}

fun interface SilentNotifier {
    fun post(m: Message)
}

fun interface InAppBannerNotifier {
    fun show(m: Message)
}

fun interface IslandAlertNotifier {
    fun play(m: Message)
}

open class IslandAlert(
    private val ctx: Context,
    private val settings: IslandSettings
) : IslandAlertNotifier {
    companion object {
        const val MESSAGES_CHANNEL_ID = "ripplechat_sms_channel"
    }

    override fun play(m: Message) {
        if (settings.sound == IslandSound.Silent || settings.sound == IslandSound.SILENT) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        var ch = nm.getNotificationChannel(MESSAGES_CHANNEL_ID)
        if (ch == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ch = NotificationChannel(
                MESSAGES_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS notifications"
            }
            nm.createNotificationChannel(ch)
        }
        if (ch == null) return
        if (ch.importance < NotificationManager.IMPORTANCE_DEFAULT) return // silent channel: stay silent

        val audioManager = ctx.getSystemService(AudioManager::class.java) ?: return
        val ringer = audioManager.ringerMode
        if (ringer == AudioManager.RINGER_MODE_NORMAL && ch.sound != null) {
            try {
                RingtoneManager.getRingtone(ctx, ch.sound)?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                    play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (ringer != AudioManager.RINGER_MODE_SILENT && ch.shouldVibrate()) {
            try {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                        ?: (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                } else {
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (v != null && v.hasVibrator()) {
                    val p = ch.vibrationPattern
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(if (p != null) VibrationEffect.createWaveform(p, -1) else VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        if (p != null) v.vibrate(p, -1) else v.vibrate(40)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class DefaultIslandController(private val ctx: Context) : IslandController {
    override suspend fun show(m: Message): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        IslandQueueManager.enqueue(m)
        if (DynamicIslandService.isAttached) {
            return true
        }
        val deferred = CompletableDeferred<Boolean>()
        DynamicIslandService.registerAttachmentCallback(deferred)
        return try {
            val intent = Intent(ctx, DynamicIslandService::class.java).apply {
                action = DynamicIslandService.ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            deferred.await()
        } catch (e: Throwable) {
            DynamicIslandService.notifyAttached(false)
            false
        }
    }
}

class DefaultStandardNotifier(private val context: Context) : StandardNotifier {
    companion object {
        const val CHANNEL_ID = "ripplechat_sms_channel"
    }

    override fun post(m: Message) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming SMS notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (m.address.isNotEmpty()) {
                putExtra("contact_address", m.address)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Public lockscreen notification version
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(m.senderName)
            .setContentText("New message received")
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(m.senderName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)

        if (m.otpCode != null) {
            val otpCode = m.otpCode
            val formattedOtp = otpCode.toList().joinToString(" ")
            val htmlText = "${m.body}<br><br><font color='#F5A623'><b>$formattedOtp</b></font>"
            val styledText = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)

            builder.setContentText(m.body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(styledText))

            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COPY_OTP
                putExtra(NotificationActionReceiver.EXTRA_OTP_CODE, otpCode)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, m.notificationId)
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context, 1, copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Copy code", copyPendingIntent)

            val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DISMISS
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, m.notificationId)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context, 2, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Dismiss", dismissPendingIntent)
        } else {
            builder.setContentText(m.body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
        }

        notificationManager.notify(m.notificationId, builder.build())
    }
}

class DefaultSilentNotifier(
    private val context: Context,
    private val settings: IslandSettings
) : SilentNotifier {
    companion object {
        const val SILENT_CHANNEL_ID = "messages_silent"
    }

    override fun post(m: Message) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SILENT_CHANNEL_ID,
                "Silent Messages",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent incoming message notifications for dynamic island"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (m.address.isNotEmpty()) {
                putExtra("contact_address", m.address)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (settings.hideContent) {
            "New message received"
        } else {
            m.body
        }

        val builder = NotificationCompat.Builder(context, SILENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(m.senderName)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (!settings.hideContent && m.otpCode != null) {
            val formattedOtp = m.otpCode.toList().joinToString(" ")
            val htmlText = "${m.body}<br><br><font color='#F5A623'><b>$formattedOtp</b></font>"
            val styledText = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(styledText))

            val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_COPY_OTP
                putExtra(NotificationActionReceiver.EXTRA_OTP_CODE, m.otpCode)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, m.notificationId)
            }
            val copyPendingIntent = PendingIntent.getBroadcast(
                context, 1, copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Copy code", copyPendingIntent)
        } else if (!settings.hideContent) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }

        notificationManager.notify(m.notificationId, builder.build())
    }
}

class DefaultInAppBannerNotifier : InAppBannerNotifier {
    override fun show(m: Message) {
        IslandBannerManager.postBanner(m)
    }
}

class MessageNotifier(
    private val gate: IslandGate,
    private val islandController: IslandController,
    private val standard: StandardNotifier,
    private val silent: SilentNotifier,
    private val inAppBanner: InAppBannerNotifier,
    private val alert: IslandAlertNotifier,
    private val settings: IslandSettings
) {
    companion object {
        fun create(context: Context, settings: IslandSettings): MessageNotifier {
            val appState = ProcessLifecycleAppState()
            val gate = IslandGate(context, settings, appState)
            val islandController = DefaultIslandController(context)
            val standardNotifier = DefaultStandardNotifier(context)
            val silentNotifier = DefaultSilentNotifier(context, settings)
            val inAppNotifier = DefaultInAppBannerNotifier()
            val alert = IslandAlert(context, settings)

            return MessageNotifier(
                gate = gate,
                islandController = islandController,
                standard = standardNotifier,
                silent = silentNotifier,
                inAppBanner = inAppNotifier,
                alert = alert,
                settings = settings
            )
        }
    }

    suspend fun onMessageReceived(m: Message) {
        when (gate.decide(m)) {
            Delivery.Standard -> standard.post(m)
            Delivery.InApp -> inAppBanner.show(m)
            Delivery.Island -> {
                val shown = withTimeoutOrNull(1500) { islandController.show(m) } ?: false
                if (shown) {
                    if (settings.keepSilentCopy) silent.post(m)
                    alert.play(m)
                } else {
                    standard.post(m)
                }
            }
        }
    }
}
