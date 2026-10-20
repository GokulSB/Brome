package com.ripple.chat.island

import android.app.NotificationManager
import android.content.res.Configuration
import com.ripple.chat.data.DynamicIslandSettings
import com.ripple.chat.data.IslandSettings
import com.ripple.chat.data.IslandSound
import com.ripple.chat.data.SmsCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNotifierTest {

    private class TestAppState(override val isForeground: Boolean) : AppForegroundState

    private class RecordingStandardNotifier : StandardNotifier {
        val posted = mutableListOf<Message>()
        override fun post(m: Message) {
            posted.add(m)
        }
    }

    private class RecordingSilentNotifier : SilentNotifier {
        val posted = mutableListOf<Message>()
        override fun post(m: Message) {
            posted.add(m)
        }
    }

    private class RecordingInAppBannerNotifier : InAppBannerNotifier {
        val shown = mutableListOf<Message>()
        override fun show(m: Message) {
            shown.add(m)
        }
    }

    private class RecordingIslandAlertNotifier : IslandAlertNotifier {
        val played = mutableListOf<Message>()
        override fun play(m: Message) {
            played.add(m)
        }
    }

    private class ConfigurableIslandController(
        var shouldSucceed: Boolean = true,
        var delayMs: Long = 0L
    ) : IslandController {
        val shown = mutableListOf<Message>()
        override suspend fun show(m: Message): Boolean {
            if (delayMs > 0) delay(delayMs)
            return if (shouldSucceed) {
                shown.add(m)
                true
            } else {
                false
            }
        }
    }

    private val baseSettings = DynamicIslandSettings(
        enabled = true,
        showFor = setOf("MESSAGES", "OTP", "FINANCE"),
        duration = 5,
        size = "DEFAULT",
        position = "CAMERA",
        hideContent = false,
        skipWhenOpen = true,
        respectDnd = true,
        keepSilentCopy = false,
        sound = IslandSound.USE_MESSAGE_SOUND
    )

    private val otpMessage = IslandMessage(
        id = 100L,
        address = "Google",
        senderName = "Google",
        body = "123456 is your verification code",
        category = SmsCategory.OTP,
        otpCode = "123456"
    )

    private fun createGate(
        settings: IslandSettings = baseSettings,
        isForeground: Boolean = false,
        canDrawOverlays: Boolean = true,
        isInteractive: Boolean = true,
        isKeyguardLocked: Boolean = false,
        interruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL,
        orientation: Int = Configuration.ORIENTATION_PORTRAIT
    ): IslandGate {
        return IslandGate(
            ctx = null,
            settings = settings,
            appState = TestAppState(isForeground),
            overlayPermissionChecker = { canDrawOverlays },
            powerManagerChecker = { isInteractive },
            keyguardChecker = { isKeyguardLocked },
            dndFilterProvider = { interruptionFilter },
            orientationProvider = { orientation }
        )
    }

    private class TestHarness(
        val gate: IslandGate,
        val settings: IslandSettings
    ) {
        val standard = RecordingStandardNotifier()
        val silent = RecordingSilentNotifier()
        val inApp = RecordingInAppBannerNotifier()
        val alert = RecordingIslandAlertNotifier()
        val islandController = ConfigurableIslandController()

        val notifier = MessageNotifier(
            gate = gate,
            islandController = islandController,
            standard = standard,
            silent = silent,
            inAppBanner = inApp,
            alert = alert,
            settings = settings
        )

        suspend fun receive(m: Message) {
            notifier.onMessageReceived(m)
        }
    }

    // -------------------------------------------------------------
    // 1. Island on, permission on, category on, screen on, unlocked,
    //    no DND, Brome in background -> 0 standard (or 1 silent if keepSilentCopy is on)
    // -------------------------------------------------------------
    @Test
    fun test1_islandOn_background_silentOff_postsZeroStandard() = runBlocking {
        val harness = TestHarness(createGate(), baseSettings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 0", 0, harness.standard.posted.size)
        assertEquals("Silent copy must be 0", 0, harness.silent.posted.size)
        assertEquals("In-app banner must be 0", 0, harness.inApp.shown.size)
        assertEquals("Island controller must be called", 1, harness.islandController.shown.size)
        assertEquals("Alert sound/vibration must play", 1, harness.alert.played.size)
    }

    @Test
    fun test1_islandOn_background_silentOn_postsOneSilentZeroStandard() = runBlocking {
        val settings = baseSettings.copy(keepSilentCopy = true)
        val harness = TestHarness(createGate(settings = settings), settings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 0", 0, harness.standard.posted.size)
        assertEquals("Silent copy must be 1", 1, harness.silent.posted.size)
        assertEquals("In-app banner must be 0", 0, harness.inApp.shown.size)
        assertEquals("Island controller must be called", 1, harness.islandController.shown.size)
        assertEquals("Alert sound/vibration must play", 1, harness.alert.played.size)
    }

    // -------------------------------------------------------------
    // 2. Island off -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test2_islandOff_postsOneStandard() = runBlocking {
        val settings = baseSettings.copy(enabled = false)
        val harness = TestHarness(createGate(settings = settings), settings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 3. Permission missing or revoked -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test3_permissionMissing_postsOneStandard() = runBlocking {
        val harness = TestHarness(createGate(canDrawOverlays = false), baseSettings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 4. Category not enabled for the island -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test4_categoryDisabled_postsOneStandard() = runBlocking {
        val spamMessage = otpMessage.copy(
            category = SmsCategory.SPAM,
            body = "Claim your lottery ticket"
        )
        val harness = TestHarness(createGate(), baseSettings)
        harness.receive(spamMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 5. Screen off or device locked -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test5a_screenOff_postsOneStandard() = runBlocking {
        val harness = TestHarness(createGate(isInteractive = false), baseSettings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    @Test
    fun test5b_deviceLocked_postsOneStandard() = runBlocking {
        val harness = TestHarness(createGate(isKeyguardLocked = true), baseSettings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 6. Do Not Disturb on (with Respect DND on) -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test6_dndActive_postsOneStandard() = runBlocking {
        val harness = TestHarness(
            createGate(interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY),
            baseSettings
        )
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 7. Landscape -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test7_landscape_postsOneStandard() = runBlocking {
        val harness = TestHarness(
            createGate(orientation = Configuration.ORIENTATION_LANDSCAPE),
            baseSettings
        )
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 8. Brome open and Skip when Brome is open on -> 0 (in-app banner)
    // -------------------------------------------------------------
    @Test
    fun test8_bromeOpen_showsInAppBannerZeroStandard() = runBlocking {
        val harness = TestHarness(createGate(isForeground = true), baseSettings)
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 0", 0, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0", 0, harness.silent.posted.size)
        assertEquals("In-app banner must be 1", 1, harness.inApp.shown.size)
        assertEquals("Island controller must not be called", 0, harness.islandController.shown.size)
    }

    // -------------------------------------------------------------
    // 9. Island controller returns false or times out -> 1 standard
    // -------------------------------------------------------------
    @Test
    fun test9a_islandControllerReturnsFalse_fallsBackToStandard() = runBlocking {
        val harness = TestHarness(createGate(), baseSettings)
        harness.islandController.shouldSucceed = false
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1 on island failure", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0 on failure", 0, harness.silent.posted.size)
        assertEquals("Alert sound must not play on failure", 0, harness.alert.played.size)
    }

    @Test
    fun test9b_islandControllerTimesOut_fallsBackToStandard() = runBlocking {
        val harness = TestHarness(createGate(), baseSettings)
        harness.islandController.delayMs = 2000L // Exceeds 1500ms timeout
        harness.receive(otpMessage)

        assertEquals("Standard notifications must be 1 on timeout", 1, harness.standard.posted.size)
        assertEquals("Silent notifications must be 0 on timeout", 0, harness.silent.posted.size)
        assertEquals("Alert sound must not play on timeout", 0, harness.alert.played.size)
    }

    // -------------------------------------------------------------
    // 10. Two messages in quick succession -> island queue, 0 standard
    //     (or 2 silent copies when keepSilentCopy is on)
    // -------------------------------------------------------------
    @Test
    fun test10a_twoMessages_quickSuccession_silentOff_zeroStandard() = runBlocking {
        val harness = TestHarness(createGate(), baseSettings)

        val message1 = otpMessage.copy(id = 101L, body = "OTP 111111")
        val message2 = otpMessage.copy(id = 102L, body = "OTP 222222")

        harness.receive(message1)
        harness.receive(message2)

        assertEquals("Standard notifications must be 0 for both messages", 0, harness.standard.posted.size)
        assertEquals("Silent copy must be 0", 0, harness.silent.posted.size)
        assertEquals("Both messages queued in island controller", 2, harness.islandController.shown.size)
        assertEquals("Alert played for each message", 2, harness.alert.played.size)
    }

    @Test
    fun test10b_twoMessages_quickSuccession_silentOn_twoSilentZeroStandard() = runBlocking {
        val settings = baseSettings.copy(keepSilentCopy = true)
        val harness = TestHarness(createGate(settings = settings), settings)

        val message1 = otpMessage.copy(id = 101L, body = "OTP 111111")
        val message2 = otpMessage.copy(id = 102L, body = "OTP 222222")

        harness.receive(message1)
        harness.receive(message2)

        assertEquals("Standard notifications must be 0 for both messages", 0, harness.standard.posted.size)
        assertEquals("Silent copy must be 2", 2, harness.silent.posted.size)
        assertEquals("Both messages queued in island controller", 2, harness.islandController.shown.size)
        assertEquals("Alert played for each message", 2, harness.alert.played.size)
    }
}
