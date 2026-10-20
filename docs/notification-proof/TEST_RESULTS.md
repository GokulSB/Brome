# Brome Notification Coordination Proof & Verification

This document provides complete verification and test results proving that Brome shows **either** the Dynamic Island notification **OR** the standard notification, **never both**.

---

## 1. Test Matrix & Results Summary

Every scenario defined in the specification was implemented as automated unit tests in [`MessageNotifierTest.kt`](file:///d:/Messages/app/src/test/java/com/ripple/chat/island/MessageNotifierTest.kt) and verified via `./gradlew.bat testDebugUnitTest`.

| # | Situation | Standard Notification | Silent Notification | Island Overlay / Banner | Total System Notifications | Test Method | Result |
|---|---|:---:|:---:|:---:|:---:|---|:---:|
| 1a | Island ON, permission granted, category ON, screen interactive, unlocked, no DND, background, silent copy OFF | **0** | **0** | Island Shown | **0** | `test1_islandOn_background_silentOff_postsZeroStandard` | **PASSED** |
| 1b | Island ON, permission granted, category ON, screen interactive, unlocked, no DND, background, silent copy ON | **0** | **1** | Island Shown | **1** | `test1_islandOn_background_silentOn_postsOneSilentZeroStandard` | **PASSED** |
| 2 | Island OFF (`island_enabled = false`) | **1** | **0** | None | **1** | `test2_islandOff_postsOneStandard` | **PASSED** |
| 3 | Overlay Permission missing or revoked (`canDrawOverlays = false`) | **1** | **0** | None | **1** | `test3_permissionMissing_postsOneStandard` | **PASSED** |
| 4 | Category not enabled for island (e.g., SPAM message when SPAM not in `showFor`) | **1** | **0** | None | **1** | `test4_categoryDisabled_postsOneStandard` | **PASSED** |
| 5a | Screen off (`isInteractive = false`) | **1** | **0** | None | **1** | `test5a_screenOff_postsOneStandard` | **PASSED** |
| 5b | Device locked (`isKeyguardLocked = true`) | **1** | **0** | None | **1** | `test5b_deviceLocked_postsOneStandard` | **PASSED** |
| 6 | Do Not Disturb active (`respectDnd = true`, filter != `INTERRUPTION_FILTER_ALL`) | **1** | **0** | None | **1** | `test6_dndActive_postsOneStandard` | **PASSED** |
| 7 | Device in landscape (`orientation = ORIENTATION_LANDSCAPE`) | **1** | **0** | None | **1** | `test7_landscape_postsOneStandard` | **PASSED** |
| 8 | Brome in foreground and "Skip when app open" enabled (`skipWhenOpen = true`) | **0** | **0** | In-App Banner | **0** | `test8_bromeOpen_showsInAppBannerZeroStandard` | **PASSED** |
| 9a | Island controller returns `false` (e.g. `BadTokenException` or WindowManager failure) | **1** | **0** | Fallback to Standard | **1** | `test9a_islandControllerReturnsFalse_fallsBackToStandard` | **PASSED** |
| 9b | Island controller times out (>1500ms attachment delay) | **1** | **0** | Fallback to Standard | **1** | `test9b_islandControllerTimesOut_fallsBackToStandard` | **PASSED** |
| 10a | Two messages in quick succession (silent copy OFF) | **0** | **0** | Island Queue (both queued) | **0** | `test10a_twoMessages_quickSuccession_silentOff_zeroStandard` | **PASSED** |
| 10b | Two messages in quick succession (silent copy ON) | **0** | **2** | Island Queue (both queued) | **2** (silent copies) | `test10b_twoMessages_quickSuccession_silentOn_twoSilentZeroStandard` | **PASSED** |

**Conclusion**: In no circumstance is the count of standard message notifications ever greater than 1, and the user **never** sees both the island overlay and a standard heads-up/alert notification for the same message.

---

## 2. Unit Test Execution Report

Running `./gradlew.bat testDebugUnitTest` confirms 100% test success across all 72 tests in the application:

```
Test Summary:
- Total tests: 72
- Failures: 0
- Ignored: 0
- Duration: 4.255s
- Success rate: 100%

Package com.ripple.chat.island:
- MessageNotifierTest: 14 tests, 0 failures, 100% passed
  * test10a_twoMessages_quickSuccession_silentOff_zeroStandard: PASSED
  * test10b_twoMessages_quickSuccession_silentOn_twoSilentZeroStandard: PASSED
  * test1_islandOn_background_silentOff_postsZeroStandard: PASSED
  * test1_islandOn_background_silentOn_postsOneSilentZeroStandard: PASSED
  * test2_islandOff_postsOneStandard: PASSED
  * test3_permissionMissing_postsOneStandard: PASSED
  * test4_categoryDisabled_postsOneStandard: PASSED
  * test5a_screenOff_postsOneStandard: PASSED
  * test5b_deviceLocked_postsOneStandard: PASSED
  * test6_dndActive_postsOneStandard: PASSED
  * test7_landscape_postsOneStandard: PASSED
  * test8_bromeOpen_showsInAppBannerZeroStandard: PASSED
  * test9a_islandControllerReturnsFalse_fallsBackToStandard: PASSED
  * test9b_islandControllerTimesOut_fallsBackToStandard: PASSED
- IslandTriggerLogicTest: 11 tests, 0 failures, 100% passed
- IslandCountdownTest: 2 tests, 0 failures, 100% passed
- IslandPeekTextTest: 7 tests, 0 failures, 100% passed
- IslandPositionClamperTest: 6 tests, 0 failures, 100% passed
- IslandQueueManagerTest: 4 tests, 0 failures, 100% passed
```

---

## 3. Dumpsys Verification Procedure

To verify notification counts on a running Android device or emulator:

### A. Notification Dump Command
```bash
adb shell dumpsys notification --noredact | grep -E "NotificationRecord.*pkg=com.ripple.chat"
```

### B. Expected Notification Records:
1. **Case 1 (Island Shown, Keep Silent Copy = OFF)**:
   - Command: `adb shell dumpsys notification --noredact | grep -c "channel=ripplechat_sms_channel"`
   - Output: `0`
   - Only the internal FGS notification (`brome_island_channel`, `IMPORTANCE_MIN`) is active while the overlay is attached.

2. **Case 2 (Island Shown, Keep Silent Copy = ON)**:
   - Command: `adb shell dumpsys notification --noredact | grep -c "channel=messages_silent"`
   - Output: `1`
   - Command: `adb shell dumpsys notification --noredact | grep -c "channel=ripplechat_sms_channel"`
   - Output: `0`

3. **Case 3 (Island OFF or Permission Missing or Fallback)**:
   - Command: `adb shell dumpsys notification --noredact | grep -c "channel=ripplechat_sms_channel"`
   - Output: `1`
   - Command: `adb shell dumpsys notification --noredact | grep -c "channel=messages_silent"`
   - Output: `0`

In all cases, the count of user message notifications in the shade is at most 1, and standard heads-up notifications are never posted when the island displays.

---

## 4. Sound & Vibration Coordination

Sound and vibration are handled by `IslandAlert`:
- When `island_sound == USE_MESSAGE_SOUND` (default):
  - Retrieves the audio attributes, sound URI, and vibration pattern configured on `ripplechat_sms_channel`.
  - Queries `AudioManager.ringerMode`:
    - If `RINGER_MODE_NORMAL` and `sound != null`: plays the ringtone once.
    - If `ringerMode != RINGER_MODE_SILENT` and `channel.shouldVibrate()`: vibrates using `Vibrator` with the channel pattern or a 40ms pulse.
- When `island_sound == SILENT`:
  - Neither sound nor vibration is played.
- In Do Not Disturb mode:
  - `IslandGate` suppresses island delivery when DND is active; the notification is routed to standard notification handling, which respects OS DND policies natively.

---

## 5. Conversation Opening Verification

1. **From Standard Notification**:
   - `setContentIntent(pendingIntent)` with `MainActivity::class.java` and `contact_address` extra.
   - Launches `MainActivity` which navigates to the chat thread for the sender address.
2. **From Dynamic Island**:
   - `IslandActions.openConversation(context, address, threadId, notificationId)`
   - Dispatches `MainActivity` launch intent with `contact_address` and cancels the notification ID.
