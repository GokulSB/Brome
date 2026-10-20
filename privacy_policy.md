# Privacy Policy for Brome

**Effective Date:** October 17, 2026  
**App Name:** Brome  
**Package:** com.ripple.chat  

Thank you for choosing Brome ("the App", "we", "our", or "us"). We are committed to protecting your privacy and ensuring a secure, transparent experience when using our default SMS and messaging application.

This Privacy Policy explains how Brome accesses, handles, and protects your information. Brome is built with a **privacy-first, local-first** architecture: your messages, contacts, and personal data remain stored locally on your device and are never collected, sold, or synced to developer-owned cloud servers.

---

## 1. Permissions We Request and How We Use Them

To function as your default SMS application on Android, Brome requires specific permissions strictly to deliver core messaging functionality:

### SMS & MMS Permissions (`READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`, `WRITE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`)
- **Displaying & Sending Messages:** Used to display your conversations, receive incoming SMS/MMS messages, and dispatch outgoing messages via your mobile cellular network.
- **On-Device Categorization:** Brome organizes messages into categories (Personal, OTP, Finance/Transactions, Promotions, Spam, Reminders, Tickets). **All categorization is performed 100% locally on your device** using heuristic analysis. Message text is never uploaded to remote servers or external AI services.
- **Telephony Provider Storage:** Messages are saved directly to the standard Android Telephony database so your SMS history remains intact on your device.

### Contacts Permission (`READ_CONTACTS`)
- **Sender Identification:** Displays contact names and avatars in conversation lists and chat headers instead of raw phone numbers.
- **Compose Auto-complete:** Allows you to search and select contacts when starting a new message.
- **Local-Only Processing:** Contact data is accessed in real time in device memory and is never uploaded, synced, or shared with third parties.

### Phone State & Multi-SIM Information (`READ_PHONE_STATE`, `READ_PHONE_NUMBERS`)
- **Dual-SIM Support:** Detects active SIM cards, slot assignments, and carrier subscription IDs so you can select and persist which SIM card to send messages from on dual-SIM devices.
- **No Device Tracking:** This information is never used to fingerprint or track your device across sessions.

### Notification Access (`POST_NOTIFICATIONS`)
- Displays real-time alerts for incoming text messages with inline action buttons (such as "Copy Code" for OTPs and "Mark as Read") and allows direct inline replies.

### Alarms & Background Execution (`SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE`)
- **Scheduled Messages:** Delivers messages you schedule to send at a specific future date and time.
- **Auto-Delete Rules:** Executes automated cleanup rules (e.g., auto-deleting OTPs after 24 hours or emptying the Recycle Bin after 7 days) as configured in Settings.
- **Headless Telephony Operations:** Powers standard background SMS/MMS processing when incoming messages arrive while the app is closed.

### Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)
- An optional permission used only if you explicitly enable floating heads-up message bubbles or Island notifications in Settings.

---

## 2. Information We Collect and Store

**Brome does not collect, harvest, or store your personal information on external servers.**

All conversations, SMS logs, contacts, and user preferences reside entirely on your physical device, protected by the Android application sandbox.

---

## 3. Data Sharing and Third Parties

- **No Third-Party Sales or Sharing:** We do not sell, rent, monetize, or disclose your messages, contacts, phone numbers, or analytics data to any third party, advertiser, or data broker.
- **Carrier Transmission:** Outgoing SMS/MMS messages are transmitted through your chosen cellular network operator to reach the recipient's phone, which is standard behavior for cellular telephony.

---

## 4. Advertising and Tracking

- **Zero Ads:** Brome contains no third-party advertisements or ad networks.
- **Zero Analytics Trackers:** Brome does not embed Google Analytics, Firebase Analytics, Meta SDKs, or any behavioral telemetry trackers.

---

## 5. Data Retention and Deletion

You have complete control over your data retention:
- **Manual Deletion:** You can delete individual messages or conversations at any time.
- **Recycle Bin:** Deleted messages are kept in the Recycle Bin for 7 days before permanent removal, or you can purge them immediately.
- **Auto-Delete Rules:** Configure automated deletion for OTP messages (e.g., after 1 hour or 24 hours) and spam messages in Settings.
- **Total Eradication:** Uninstalling Brome or clearing its data via Android Settings immediately deletes all app databases, settings, and cached assets.

---

## 6. Security

Your data is secured by Android's built-in application sandboxing, which prevents unauthorized apps from accessing Brome's internal database. We recommend keeping your device protected with a screen lock (PIN, password, fingerprint, or face unlock).

---

## 7. User Control & Revoking Permissions

You can modify or revoke permissions at any time:
- Navigate to **Settings > Apps > Brome > Permissions**.
- You can change your default SMS app at any time via **Settings > Apps > Default apps > SMS app**.

---

## 8. Children's Privacy

Brome is not directed at children under the age of 13 (or 16 in applicable jurisdictions) and does not knowingly collect personal information from children.

---

## 9. Changes to this Privacy Policy

We may update this Privacy Policy from time to time. Updates will be published with a revised effective date.

---

## 10. Contact Us

If you have questions or feedback about this Privacy Policy, please contact:

- **Email:** support@brome.app
- **Website:** https://ripplefiles.in
