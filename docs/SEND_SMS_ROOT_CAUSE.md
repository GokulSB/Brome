# Brome — Root Cause Analysis: SMS Sending & Chat Visibility

## Summary of Root Causes
The primary failure in SMS sending stems from **missing default-SMS role checks** prior to sending, **dynamic-only receiver lifecycle teardown** causing sent and delivery callbacks to be lost when leaving the screen or process, **lack of multipart callback aggregation** (marking messages prematurely or mishandling multi-segment PDUs), **absence of delivery intent requests**, and **unprotected transmission to alphanumeric sender IDs** (e.g. `JM-KOTAKB-S`) which carriers reject. The invisibility of sent chats in **Recent** and **Personal** is caused by: (1) conversations relying entirely on asynchronous ContentObserver re-queries of SQLite rather than immediately generating a conversation card upon local save with `Status.PENDING`, (2) the classification logic running incoming heuristics on outgoing messages which categorized sent chats as `OTHERS` instead of enforcing the rule that every outgoing message is `User Message` (Personal) and any thread containing sent messages is `Personal`, and (3) missing normalization and deduplication grouping across disparate formats (`+91 98765 43210`, `098765 43210`, `9876543210`).

---

## Detailed Investigation (Questions 1 – 8)

### 1. Does the send button call `SmsManager.sendTextMessage` / `sendMultipartTextMessage` or only save locally? Is it inside a swallowing `try/catch`?
- **Evidence:** [`ChatDetailScreen.kt` lines 486–499](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailScreen.kt#L486-L499) and [`ChatDetailViewModel.kt` lines 443–660](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L443-L660).
- **Finding:** The UI calls `viewModel.sendMessage(inputText, chatId, address)`. In `ChatDetailViewModel`, the call is enclosed in a broad `try/catch (e: Exception)` block.
- While `smsManager.sendTextMessage` or `sendMultipartTextMessage` is invoked at lines 608 and 625, any preliminary exception thrown during URI insertion (`contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)`) or thread ID resolution is caught, logged, and marks the message as `FAILED` without completing delivery.
- Crucially, if the recipient address is an alphanumeric sender (such as `JM-KOTAKB-S` or `VM-HDFCBK`), line 508 leaves `addressToSend` as letters and passes it directly to `SmsManager`. The radio/carrier rejects alphanumeric destinations, resulting in immediate failure.

### 2. Is `SEND_SMS` permission requested and granted, and does Brome hold `RoleManager.ROLE_SMS`?
- **Evidence:** [`ChatDetailScreen.kt` lines 488–498](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailScreen.kt#L488-L498).
- **Finding:** The UI checks only `Manifest.permission.SEND_SMS` before calling `viewModel.sendMessage`.
- **Brome does NOT verify whether it currently holds the default SMS role (`RoleManager.isRoleHeld(RoleManager.ROLE_SMS)`) before sending.** If the user changed the default SMS app in Android Settings, reinstalled Brome, or dismissed the home banner, writes to `Telephony.Sms.Sent` fail with `SecurityException` or return `null`, preventing provider persistence and message synchronization.

### 3. What does the sent callback return? Status mapping & PendingIntent registration
- **Evidence:** [`ChatDetailViewModel.kt` lines 59–93](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L59-L93) and [`AndroidManifest.xml`](file:///d:/Messages/app/src/main/AndroidManifest.xml).
- **Finding:** `smsStatusReceiver` was registered **only dynamically** inside `ChatDetailViewModel.init` and unregistered on `onCleared()`. There was **no manifest-declared `SmsStatusReceiver`**. When the user navigated away from the chat screen or the app process paused, the receiver was destroyed and callbacks were lost.
- **Multipart callbacks:** In multipart messages, every part broadcasted the same `ACTION_SMS_SENT` with `EXTRA_MESSAGE_ID`. The receiver immediately marked the entire message `SENT` upon the first part's completion, without verifying all parts succeeded.
- **Delivery reports:** `deliveryIntent` was passed as `null` in lines 608 and 625. Delivery tracking via status PDU (`SmsMessage.createFromPdu(pdu, format).status`) was completely absent.

### 4. Destination address normalization & long message handling
- **Evidence:** [`ChatDetailViewModel.kt` lines 505–525](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L505-L525) and [`MessageParser.kt` lines 47–65](file:///d:/Messages/app/src/main/java/com/ripple/chat/utils/MessageParser.kt#L47-L65).
- **Finding:** Address cleaning relied on basic character filtering rather than full libphonenumber or SIM-country ISO parsing.
- Alphanumeric sender IDs (such as `AD-AMZNIN`, `JM-KOTAKB`) were not blocked in the composer, allowing users to send doomed replies.
- Messages containing emojis or non-Latin characters (which reduce single-SMS PDU capacity to 70 characters) were divided by `divideMessage`, but per-part status aggregation and failure indicators were missing.

### 5. Dual-SIM subscription ID targeting
- **Evidence:** [`ChatDetailViewModel.kt` lines 144, 476–477, 734–767](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L144).
- **Finding:** `_selectedSim` was initialized to `1`. On dual-SIM devices where SIM 1 has no SMS recharge pack or is disabled while SIM 2 is active, sending failed with `RESULT_ERROR_GENERIC_FAILURE` or `RESULT_ERROR_NO_SERVICE` unless the user explicitly tapped the SIM toggle.
- Direct subscription resolution using `SubscriptionManager.getDefaultSmsSubscriptionId()` was not bound at startup.

### 6. Premature local "SENT" status marking
- **Evidence:** [`ChatDetailViewModel.kt` lines 547, 630–640](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L547).
- **Finding:** Messages were directly inserted into `Telephony.Sms.Sent.CONTENT_URI` with `TYPE = MESSAGE_TYPE_SENT` before transmission was attempted over the radio.
- In earlier versions, a 3.5s watchdog auto-marked pending messages as `SENT`. Even after removing the fake watchdog, inserting directly into `Sent` rather than `Outbox` with `PENDING` meant that radio transmission failures left dead "sent" records in the provider.

### 7. System SMS provider persistence (`Telephony.Sms.Sent` / `Outbox`)
- **Evidence:** [`ChatDetailViewModel.kt` lines 541–573](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatDetailViewModel.kt#L541-L573) and [`SmsComponents.kt` lines 21–30](file:///d:/Messages/app/src/main/java/com/ripple/chat/sms/SmsComponents.kt#L21-L30).
- **Finding:** Brome attempted to write directly to `Telephony.Sms.Sent.CONTENT_URI` with explicit `THREAD_ID`. If the OEM telephony provider rejected the `THREAD_ID` parameter, insertion failed.
- In addition, `HeadlessSmsSendService` was an empty stub, causing background or external send intents (quick reply, notifications, WearOS) to drop without writing to the provider.

### 8. Recent and Personal query construction
- **Evidence:** [`SmsRepository.kt` lines 40–121](file:///d:/Messages/app/src/main/java/com/ripple/chat/data/SmsRepository.kt#L40-L121) and [`ChatsScreen.kt` lines 193–218](file:///d:/Messages/app/src/main/java/com/ripple/chat/ui/chat/ChatsScreen.kt#L193-L218).
- **Finding:**
  1. `SmsRepository.getConversations()` classified every thread based strictly on the latest message body evaluated through `CategoryClassifier.classify()`.
  2. Because outgoing messages (e.g. "Hi", "Where are you?") lack service keywords and don't match banking/OTP templates, they were classified as `OTHERS`.
  3. `ChatsScreen.kt` filtered `personal = sortedThreads.filter { isContactMessage(it) || it.category == SmsCategory.PERSONAL }`. A sent-only chat to a number without a saved contact was classified as `OTHERS`, excluding it from the **Personal** category.
  4. The conversation card was not created immediately upon tapping Send; it waited for an asynchronous provider write and ContentObserver cycle.
  5. The rule that **every outgoing message is User Message (Personal)** and **any conversation containing an outgoing message is Personal** was never implemented.
  6. Recent cards did not display the "You: " prefix or delivery status icons for sent messages.
