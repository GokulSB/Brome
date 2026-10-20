package com.ripple.chat.island

import android.app.NotificationManager
import android.content.res.Configuration
import com.ripple.chat.data.DynamicIslandSettings
import com.ripple.chat.data.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandTriggerLogicTest {

    private val defaultSettings = DynamicIslandSettings(
        enabled = true,
        showFor = setOf("MESSAGES", "OTP", "FINANCE"),
        duration = 5,
        size = "DEFAULT",
        position = "CAMERA",
        actReply = true,
        actCopy = true,
        actRead = true,
        actExpand = true,
        hideContent = false,
        skipWhenOpen = true,
        respectDnd = true
    )

    private val defaultOtpMessage = IslandMessage(
        id = 1L,
        address = "Google",
        senderName = "Google",
        body = "Your code is 123456",
        category = SmsCategory.OTP,
        otpCode = "123456"
    )

    @Test
    fun testTrigger_whenDisabled_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings.copy(enabled = false),
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_DISABLED, result)
    }

    @Test
    fun testTrigger_whenNoOverlayPermission_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings,
            canDrawOverlays = false,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_NO_OVERLAY_PERMISSION, result)
    }

    @Test
    fun testTrigger_whenCategoryNotInShowFor_skips() {
        val spamMessage = defaultOtpMessage.copy(
            category = SmsCategory.SPAM,
            body = "You won lottery"
        )
        val result = IslandNotifier.evaluateTriggerLogic(
            message = spamMessage,
            settings = defaultSettings, // showFor does not include SPAM
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_CATEGORY_FILTER, result)
    }

    @Test
    fun testTrigger_whenScreenNotInteractive_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings,
            canDrawOverlays = true,
            isInteractive = false,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_SCREEN_NOT_INTERACTIVE, result)
    }

    @Test
    fun testTrigger_whenDeviceLocked_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings,
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = true,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_DEVICE_LOCKED, result)
    }

    @Test
    fun testTrigger_whenDndActiveAndRespectDnd_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings.copy(respectDnd = true),
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SKIP_DND_ACTIVE, result)
    }

    @Test
    fun testTrigger_whenDndActiveAndDoNotRespectDnd_shows() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings.copy(respectDnd = false),
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SHOW_OVERLAY, result)
    }

    @Test
    fun testTrigger_whenAppInForegroundAndSkipWhenOpen_showsInAppBanner() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings.copy(skipWhenOpen = true),
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = true,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SHOW_IN_APP_BANNER, result)
    }

    @Test
    fun testTrigger_whenAppInForegroundAndDoNotSkipWhenOpen_showsOverlay() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings.copy(skipWhenOpen = false),
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = true,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SHOW_OVERLAY, result)
    }

    @Test
    fun testTrigger_whenOrientationLandscape_skips() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings,
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )
        assertEquals(TriggerEvaluationResult.SKIP_NOT_PORTRAIT, result)
    }

    @Test
    fun testTrigger_whenAllConditionsPass_showsOverlay() {
        val result = IslandNotifier.evaluateTriggerLogic(
            message = defaultOtpMessage,
            settings = defaultSettings,
            canDrawOverlays = true,
            isInteractive = true,
            isKeyguardLocked = false,
            interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
            isAppInForeground = false,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )
        assertEquals(TriggerEvaluationResult.SHOW_OVERLAY, result)
    }
}
