# Brome — Phase 0 Evidence & Root Cause Analysis

## Executive Summary
This document provides the evidence-first root cause analysis for two primary issues identified in Brome:
1. **Chat Cards Not Created for Personal Messages**: Incoming messages from personal contacts show notifications and are stored in the SMS provider, but fail to appear as chat cards in Recent, Personal, or Unread lists.
2. **"Sent" Messages Never Arrive**: Messages display "Sent" in the conversation UI, but recipients never receive them over the cellular network.

---

## Phase 0 — Evidence & Root Cause Analysis

### Part A: Why No Chat Card Is Created

#### 1. Receive Pipeline Trace (File & Line)
The incoming message execution path:
- **`AndroidManifest.xml` (Line 62–68)**:
  - `receiver .receiver.SmsReceiver` with `permission="android.permission.BROADCAST_SMS"` listening for `Telephony.SMS_DELIVER`.
  - Secondary fallback receiver `.receiver.SmsReceivedReceiver` (Line 71–76) listening for `Telephony.SMS_RECEIVED`.
- **`SmsReceiver.kt`**:
  - `onReceive` (Line 199–205): Intercepts `SMS_DELIVER_ACTION` or `SMS_RECEIVED_ACTION` and calls `processIncomingSms(context, intent)`.
  - `processIncomingSms` (Line 22–122):
    - **Parse**: Line 23–28 calls `Telephony.Sms.Intents.getMessagesFromIntent(intent)`.
    - **Sender & Body**: Line 31–36 extracts `senderNumber` (`displayOriginatingAddress ?: originatingAddress`), `sender` (`getContactName`), and aggregates concatenated PDU bodies into `body`.
    - **Deduplication**: Line 39–46 checks `processedMessages` LRU cache.
    - **Save Message**: Line 49–84 resolves threadId (`Telephony.Threads.getOrCreateThreadId(context, setOf(senderNumber))`) and inserts into `Telephony.Sms.Inbox.CONTENT_URI` with `TYPE = MESSAGE_TYPE_INBOX`, `READ = 0`, `SEEN = 0`.
    - **Broadcast**: Line 87–94 fires local broadcast `Intent("com.ripple.chat.NEW_SMS")`.
    - **Classify**: Line 96 parses OTP (`OtpParser.parseOtp`); Line 103–109 calls `CategoryClassifier.classify(senderNumber, body, sender, ...)`.
    - **Notification**: Line 110–121 constructs `IslandMessage` and triggers `IslandNotifier.onMessageReceived(message)`.
  - **CRITICAL GAP**: There is **no conversation creation or upsert step** in the receive pipeline. Brome writes the message to the provider, but never updates or creates a conversation record.
  - **Soft-Delete Suppression**: If the conversation was previously deleted or auto-cleaned into trash, `TrashManager.softDelete(threadId)` recorded `threadId` in `trash_prefs`. Because `SmsReceiver` does not revive deleted conversations (`trashManager.restore(threadId)`), `ChatsViewModel.loadThreads()` permanently filters the thread out at Line 155 (`if (thread.threadId in deletedIds) return@filter false`).

#### 2. Query Architecture & Filtering Layers
Conversations are reconstructed on-the-fly from raw messages:
- **`ChatsViewModel.kt` (Lines 144–167)**:
  - `loadThreads()` queries `repository.getConversations()`:
    - Filters: `if (thread.threadId in deletedIds) return@filter false` (Lines 154–155).
    - OTP expiration: Soft-deletes OTP messages older than 7 hours.
- **`MainScreen.kt` (Lines 72–74)**:
  - `inboxThreads = threads.filter { it.threadId !in archivedThreadIds }`.
- **`ChatsScreen.kt` (Lines 197–218, 288–308)**:
  - `personal = sortedThreads.filter { isContactMessage(it) || it.category == SmsCategory.PERSONAL }`.
  - `listToShow` when `selectedCategoryKey == null` (Recent) is `sortedThreads`.
  - Unread count: `counts["UNREAD"] = sortedThreads.filter { it.category != SmsCategory.SPAM }.sumOf { ... }`.
  - **Issue**: If `thread.contactName` fails to resolve due to non-normalized phone number formatting in contact lookup, and the message content contains keywords matching Finance (e.g. "500 rs", "account"), OTP, or spam keywords, `thread.category` is assigned `FINANCE`, `OTP`, or `SPAM`. A spam classification drops the thread from Unread, Personal, and buries it under the Spam Strip.

#### 3. Address Key Comparison Across All 6 Operations
Byte-for-byte comparison of the address string across the app lifecycle:
1. **(a) On Receive (`SmsReceiver.kt` Line 31)**:
   - Uses `messages[0].displayOriginatingAddress ?: messages[0].originatingAddress`.
   - May contain directional markers (`\u200E`, `\u200F`), whitespace, or international format (`+91 98765 43210` or `\u200E+919876543210`).
2. **(b) On Conversation Create/Lookup (`SmsReceiver.kt` Line 51, `SmsSender.kt` Line 83)**:
   - Receive calls `Telephony.Threads.getOrCreateThreadId(context, setOf(senderNumber))` with uncleaned raw string.
   - Send calls `Telephony.Threads.getOrCreateThreadId(context, setOf(to))` with E.164 normalized string (`+919876543210`).
   - Android's `MmsSmsProvider` assigns **different thread IDs** for formatted vs unformatted recipients!
3. **(c) Querying Recent (`SmsRepository.kt` Lines 129–137)**:
   - Reads `rawAddress = cursor.getString(addressIdx)`.
   - Cleans with `MessageParser.cleanAddress(rawAddress)`.
   - Computes `canonicalKey = AddressNormalizer.canonicalKey(cleanAddress)`.
   - If `cursor.getLong(threadIdIdx) <= 0L`, calls `Telephony.Threads.getOrCreateThreadId(context, setOf(cleanAddress))`.
4. **(d) Querying Personal (`ChatsScreen.kt` Line 197–209)**:
   - Checks `isContactMessage(thread)`: requires `contactName != null && contactName != addr && contactName != digits`.
   - If contact lookup in `numberCache` failed due to key mismatch (e.g. raw address containing `\u200E` or unstripped country codes), `contactName` remains null, and `category` is evaluated purely on text keywords.
5. **(e) Opening Chat Screen from Compose (`NewMessageScreen.kt` Lines 269–281)**:
   - Cleans numbers with `MessageParser.cleanAddress()`.
   - Resolves threadId with `Telephony.Threads.getOrCreateThreadId(context, numbers.toSet())`.
   - Passes `targetChatId` and `numbersStr`.
6. **(f) Displaying Contact (`ChatDetailViewModel.kt` Lines 248–268)**:
   - Fallback query uses `Telephony.Sms.ADDRESS LIKE %$searchNum` (last 10 digits).
   - This explains why opening the chat from Compose finds all messages, while the conversation list in `SmsRepository` orphaned or filtered them!

---

### Part B: Why "Sent" Messages Never Arrive

#### 1. The State Machine & Radio Truth
- **Tap Send (`ChatDetailViewModel.kt` Lines 474–483)**:
  - Generates a temporary message row with `deliveryStatus = MessageDeliveryStatus.PENDING`.
  - State `PENDING` is rendered with a clock icon.
- **Save to Outbox (`SmsSender.kt` Lines 95–126)**:
  - Writes to `Telephony.Sms.Outbox.CONTENT_URI` with `TYPE = MESSAGE_TYPE_OUTBOX`, `STATUS = STATUS_PENDING`.
- **Radio Transmission (`SmsSender.kt` Lines 162–245)**:
  - Divides message using `SmsManager.divideMessage(body)`.
  - Invokes `SmsManager.sendTextMessage` / `sendMultipartTextMessage` passing `sentIntent` and `deliveryIntent`.
- **Radio Sent Callback (`SmsStatusReceiver.kt` Lines 51–83)**:
  - Broadcast receiver for `ACTION_SMS_SENT`.
  - Checks `resultCode == Activity.RESULT_OK`.
  - For multipart messages: aggregates completed parts in `completedParts[msgId]`.
  - When all parts report `RESULT_OK`: updates provider to `MESSAGE_TYPE_SENT` and broadcasts `ACTION_SMS_STATUS_CHANGED` with `SENT`.
- **Delivery Report Callback (`SmsStatusReceiver.kt` Lines 85–109)**:
  - Broadcast receiver for `ACTION_SMS_DELIVERED`.
  - Parses PDU status; when `STATUS_COMPLETE`: updates provider to `STATUS_COMPLETE` and broadcasts `ACTION_SMS_STATUS_CHANGED` with `DELIVERED`.
- **Timeout Watchdog (`ChatDetailViewModel.kt` Lines 500–507)**:
  - 25s timeout coroutine transitions unacknowledged pending messages to `FAILED` ("Send timed out").

#### 2. Root Causes of Delivery Failure
1. **Destination Address Corruption from Invisible Unicode**:
   - `AddressNormalizer.kt` `normalizeWithRegion()` relied on `trimmed.startsWith("+")`.
   - If an address contained invisible unicode markers (such as Left-To-Right Mark `\u200E`, Right-To-Left Mark `\u200F`, or embedding controls `\u202A`–`\u202C`), `trimmed.startsWith("+")` evaluated to `false`.
   - The leading `+` was lost, stripping international dialing prefixes. Cell towers accepted the malformed PDU locally (`RESULT_OK`), but carrier routing failed downstream, so the recipient never received it.
2. **Hardcoded Region Fallback**:
   - `AddressNormalizer.getDefaultRegion()` fell back to `"IN"` when `simCountryIso` or `networkCountryIso` was unavailable, appending `+91` to 10-digit numbers on non-Indian networks.
3. **Dual-SIM Subscription Targeting on Android 12+ (API 31+)**:
   - Calling deprecated `SmsManager.getSmsManagerForSubscriptionId(subId)` instead of `context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)` prevented proper context attribution and multi-SIM radio routing on API 31+.
4. **Manifest Receiver Export Configuration**:
   - In `AndroidManifest.xml`, `SmsStatusReceiver` was declared with `android:exported="false"` without an explicit intent filter. On targetSdk 34+, system service broadcasts require explicit component targeting and proper registration to guarantee callback dispatch.

---

## Phase 1 Implementation Plan

1. **Unified `MessageIngest` Pipeline**:
   - Single ingestion entry point for incoming (`SMS_DELIVER`), outgoing (`SmsSender`), and provider sync.
   - Comprehensive address sanitization (stripping `\u200E`, `\u200F`, `\u202A`–`\u202C`, `\u00A0`, and formatting characters).
   - Automatic conversation upsert with canonical key (`last10` digits or alphanumeric).
   - Reviving soft-deleted conversations on new message arrival (`isDeleted = false`).
   - Strict Personal category assignment: any conversation with outgoing messages or classified as `User Message` is `PERSONAL`.
2. **`ConversationRebuilder`**:
   - Idempotent backfill on app launch that scans existing messages in the provider, groups by canonical key, and ensures all active conversations are populated in the UI.
3. **Sending State Machine & Radio Truth**:
   - Enforce `PENDING` on send.
   - Sanitize E.164 destination string before `SmsManager` invocation.
   - Use `context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)`.
   - Update 60-second watchdog timeout for network failure handling.
   - Update `AndroidManifest.xml` for `SmsStatusReceiver`.
