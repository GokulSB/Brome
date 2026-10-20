package com.ripple.chat.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.ripple.chat.utils.OtpParser

open class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
        private val processedMessages = LruCache<String, Long>(100)

        fun processIncomingSms(context: Context, intent: Intent) {
            val messages = try {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing messages from intent", e)
                emptyArray()
            }
            if (messages.isNullOrEmpty()) return

            val senderNumber = messages[0].displayOriginatingAddress
                ?: messages[0].originatingAddress
                ?: "Unknown"
            val body = messages.mapNotNull { it.displayMessageBody ?: it.messageBody }.joinToString(separator = "")
            val messageDate = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

            // Run through unified MessageIngest pipeline
            val ingestResult = com.ripple.chat.data.MessageIngest.ingestMessage(
                context = context,
                rawAddress = senderNumber,
                body = body,
                messageDate = messageDate,
                isFromMe = false
            )

            val cleanSender = ingestResult?.address ?: com.ripple.chat.utils.MessageParser.cleanAddress(senderNumber) ?: senderNumber
            val sender = getContactName(context, cleanSender)
            val category = ingestResult?.category ?: com.ripple.chat.data.SmsCategory.PERSONAL
            val otpResult = OtpParser.parseOtp(body, messageDate)

            val notificationId = (cleanSender.hashCode() and 0x7FFFFFFF)
            val message = com.ripple.chat.island.IslandMessage(
                context = context,
                address = cleanSender,
                senderName = sender,
                body = body,
                date = messageDate,
                category = category,
                otpCode = otpResult?.code,
                notificationId = notificationId
            )
            com.ripple.chat.island.IslandNotifier.onMessageReceived(message)
        }

        fun getContactName(context: Context, phoneNumber: String): String {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return phoneNumber
            }

            val cleanNum = com.ripple.chat.utils.MessageParser.cleanAddress(phoneNumber) ?: phoneNumber
            val digitsOnly = cleanNum.filter { it.isDigit() }
            val last10 = if (digitsOnly.length >= 10) digitsOnly.takeLast(10) else digitsOnly
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            var contactName: String? = null

            // 1. Exact PhoneLookup with cleanNum
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(cleanNum))
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contactName = cursor.getString(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. PhoneLookup with last 10 digits
            if (contactName == null && last10.length >= 7 && last10 != cleanNum) {
                try {
                    val digitsUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(last10))
                    context.contentResolver.query(digitsUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            contactName = cursor.getString(0)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Fallback: CommonDataKinds.Phone with NORMALIZED_NUMBER or NUMBER
            if (contactName == null && last10.length >= 7) {
                try {
                    val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val phoneProj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ?"
                    val args = arrayOf("%$last10%", "%$last10%")
                    context.contentResolver.query(phoneUri, phoneProj, selection, args, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            contactName = cursor.getString(0)
                        }
                    }
                } catch (e: Exception) {
                    try {
                        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        val phoneProj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        context.contentResolver.query(
                            phoneUri,
                            phoneProj,
                            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                            arrayOf("%$last10%"),
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                contactName = cursor.getString(0)
                            }
                        }
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }

            return contactName ?: phoneNumber
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Telephony.Sms.Intents.SMS_DELIVER_ACTION || action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            processIncomingSms(context, intent)
        }
    }
}
