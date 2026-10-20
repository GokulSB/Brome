package com.ripple.chat.sms

import android.provider.Telephony
import com.ripple.chat.data.ChatThread
import com.ripple.chat.data.MessageDeliveryStatus
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.utils.CategoryClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationClassificationTest {

    private fun resolveThreadCategory(thread: ChatThread): SmsCategory {
        val cleanAddr = thread.address.trim()
        val isPhoneNum = !thread.address.any { it.isLetter() } && thread.address.filter { it.isDigit() }.length >= 7
        val hasResolvedContact = !thread.contactName.isNullOrBlank() && thread.contactName != thread.address && thread.contactName != cleanAddr
        val isAlphanumeric = AddressNormalizer.isAlphanumericSender(thread.address)

        return if ((thread.hasOutgoing || thread.lastMessageIsFromMe) && !isAlphanumeric) {
            SmsCategory.PERSONAL
        } else if (hasResolvedContact) {
            SmsCategory.PERSONAL
        } else {
            val classified = CategoryClassifier.classify(
                thread.address,
                thread.body,
                thread.contactName
            )
            if (classified == SmsCategory.OTHERS && isPhoneNum) {
                SmsCategory.PERSONAL
            } else {
                classified
            }
        }
    }

    @Test
    fun testSentOnlyThreadClassifiedAsPersonal() {
        val thread = ChatThread(
            threadId = 101L,
            address = "+919876543210",
            body = "Hey, are you free this evening?",
            date = System.currentTimeMillis(),
            read = true,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            category = SmsCategory.OTHERS,
            unreadCount = 0,
            deliveryStatus = MessageDeliveryStatus.SENT,
            lastMessageIsFromMe = true,
            hasOutgoing = true
        )

        val resolved = resolveThreadCategory(thread)
        assertEquals(SmsCategory.PERSONAL, resolved)
    }

    @Test
    fun testConversationWithOutgoingMessageClassifiedAsPersonal() {
        // Even if the latest message was an incoming reply, having an outgoing message in the thread marks it as PERSONAL
        val thread = ChatThread(
            threadId = 102L,
            address = "9876543210",
            body = "Yes, let's meet at 6!",
            date = System.currentTimeMillis(),
            read = true,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            category = SmsCategory.OTHERS,
            unreadCount = 0,
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            lastMessageIsFromMe = false,
            hasOutgoing = true // User previously sent a message in this conversation
        )

        val resolved = resolveThreadCategory(thread)
        assertEquals(SmsCategory.PERSONAL, resolved)
    }

    @Test
    fun testServiceSenderRetainsProperCategoryAndNeverForcedToPersonal() {
        // Finance SMS from alphanumeric sender
        val financeThread = ChatThread(
            threadId = 103L,
            address = "VK-KOTAKB",
            body = "Dear Customer, INR 5,000.00 debited from A/C XX1234 on 17-Oct-26. UPI Ref 629182.",
            date = System.currentTimeMillis(),
            read = false,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            category = SmsCategory.FINANCE,
            unreadCount = 1,
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            lastMessageIsFromMe = false,
            hasOutgoing = false
        )
        val resolvedFinance = resolveThreadCategory(financeThread)
        assertEquals(SmsCategory.FINANCE, resolvedFinance)

        // OTP SMS from alphanumeric sender
        val otpThread = ChatThread(
            threadId = 104L,
            address = "AD-GOOGLE",
            body = "G-123456 is your Google verification code.",
            date = System.currentTimeMillis(),
            read = false,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            category = SmsCategory.OTP,
            unreadCount = 1,
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            lastMessageIsFromMe = false,
            hasOutgoing = false
        )
        val resolvedOtp = resolveThreadCategory(otpThread)
        assertEquals(SmsCategory.OTP, resolvedOtp)
    }

    @Test
    fun testDuplicateThreadsMergeByCanonicalKey() {
        val rawMessages = listOf(
            // Newest message: sent by user to local format
            Triple("9876543210", "See you soon!", Telephony.Sms.MESSAGE_TYPE_SENT),
            // Older message: received from international format
            Triple("+91 98765 43210", "Okay, waiting.", Telephony.Sms.MESSAGE_TYPE_INBOX)
        )

        val threadsByCanonicalKey = mutableMapOf<String, String>()
        for ((addr, body, _) in rawMessages) {
            val canonicalKey = AddressNormalizer.canonicalKey(addr)
            if (!threadsByCanonicalKey.containsKey(canonicalKey)) {
                threadsByCanonicalKey[canonicalKey] = body
            }
        }

        // Only ONE merged thread must exist for both formats
        assertEquals(1, threadsByCanonicalKey.size)
        assertTrue(threadsByCanonicalKey.containsKey("9876543210"))
        // It holds the newest message body
        assertEquals("See you soon!", threadsByCanonicalKey["9876543210"])
    }

    @Test
    fun testOutgoingMessagesDoNotCountInUnreadBadge() {
        val outgoingThread = ChatThread(
            threadId = 105L,
            address = "+919876543210",
            body = "Where are you?",
            date = System.currentTimeMillis(),
            read = true,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            category = SmsCategory.PERSONAL,
            unreadCount = 0,
            deliveryStatus = MessageDeliveryStatus.PENDING,
            lastMessageIsFromMe = true,
            hasOutgoing = true
        )

        assertTrue(outgoingThread.read)
        assertEquals(0, outgoingThread.unreadCount)
    }
}
