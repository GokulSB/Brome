package com.ripple.chat.island

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.ripple.chat.data.BromeSettings
import com.ripple.chat.data.DynamicIslandSettings
import com.ripple.chat.data.SmsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class TriggerEvaluationResult {
    SHOW_OVERLAY,
    SHOW_IN_APP_BANNER,
    SKIP_DISABLED,
    SKIP_NO_OVERLAY_PERMISSION,
    SKIP_CATEGORY_FILTER,
    SKIP_SCREEN_NOT_INTERACTIVE,
    SKIP_DEVICE_LOCKED,
    SKIP_DND_ACTIVE,
    SKIP_NOT_PORTRAIT
}

object IslandNotifier {
    @Volatile
    private var appContext: Context? = null
    private val notifierScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onMessageReceived(message: IslandMessage) {
        val context = message.context ?: appContext ?: return
        if (appContext == null) {
            appContext = context.applicationContext
        }

        notifierScope.launch {
            try {
                val bromeSettings = BromeSettings(context)
                val settings = bromeSettings.islandSettingsFlow.first()
                val notifier = MessageNotifier.create(context, settings)
                notifier.onMessageReceived(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onMessageReceived(
        context: Context,
        address: String,
        senderName: String,
        body: String,
        date: Long = System.currentTimeMillis(),
        category: SmsCategory = SmsCategory.PERSONAL,
        otpCode: String? = null,
        notificationId: Int = 1001,
        threadId: Long = -1L
    ) {
        val message = IslandMessage(
            context = context,
            address = address,
            senderName = senderName,
            body = body,
            date = date,
            category = category,
            otpCode = otpCode,
            notificationId = notificationId,
            threadId = threadId
        )
        onMessageReceived(message)
    }

    fun evaluateTrigger(
        context: Context,
        message: IslandMessage,
        settings: DynamicIslandSettings
    ): TriggerEvaluationResult {
        val canDrawOverlays = Settings.canDrawOverlays(context)

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isKeyguardLocked = km?.isKeyguardLocked ?: false

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val interruptionFilter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL

        val isForeground = try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (e: Exception) {
            false
        }

        val orientation = context.resources.configuration.orientation

        return evaluateTriggerLogic(
            message = message,
            settings = settings,
            canDrawOverlays = canDrawOverlays,
            isInteractive = isInteractive,
            isKeyguardLocked = isKeyguardLocked,
            interruptionFilter = interruptionFilter,
            isAppInForeground = isForeground,
            orientation = orientation
        )
    }

    /**
     * Pure function for testing all trigger & skip conditions.
     */
    fun evaluateTriggerLogic(
        message: IslandMessage,
        settings: DynamicIslandSettings,
        canDrawOverlays: Boolean,
        isInteractive: Boolean,
        isKeyguardLocked: Boolean,
        interruptionFilter: Int,
        isAppInForeground: Boolean,
        orientation: Int
    ): TriggerEvaluationResult {
        // 1. island_enabled
        if (!settings.enabled) {
            return TriggerEvaluationResult.SKIP_DISABLED
        }

        // 2. Settings.canDrawOverlays()
        if (!canDrawOverlays) {
            return TriggerEvaluationResult.SKIP_NO_OVERLAY_PERMISSION
        }

        // 3. Category in island_show_for
        val categoryKey = message.islandCategory.key
        if (categoryKey !in settings.showFor) {
            return TriggerEvaluationResult.SKIP_CATEGORY_FILTER
        }

        // 4. Screen interactive and device not locked
        if (!isInteractive) {
            return TriggerEvaluationResult.SKIP_SCREEN_NOT_INTERACTIVE
        }
        if (isKeyguardLocked) {
            return TriggerEvaluationResult.SKIP_DEVICE_LOCKED
        }

        // 5. island_respect_dnd: NotificationManager.currentInterruptionFilter == INTERRUPTION_FILTER_ALL
        if (settings.respectDnd && interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            return TriggerEvaluationResult.SKIP_DND_ACTIVE
        }

        // 6. island_skip_when_open: Brome is not in the foreground
        if (settings.skipWhenOpen && isAppInForeground) {
            return TriggerEvaluationResult.SHOW_IN_APP_BANNER
        }

        // 7. Display in portrait
        if (orientation != Configuration.ORIENTATION_PORTRAIT) {
            return TriggerEvaluationResult.SKIP_NOT_PORTRAIT
        }

        return TriggerEvaluationResult.SHOW_OVERLAY
    }

    private fun startIslandService(context: Context, message: IslandMessage) {
        IslandQueueManager.enqueue(message)
        try {
            val intent = Intent(context, DynamicIslandService::class.java).apply {
                action = DynamicIslandService.ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
