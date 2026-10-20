package com.ripple.chat.island

import com.ripple.chat.data.IslandPeekText
import com.ripple.chat.data.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class IslandPeekTextTest {

    private val otpMessage = IslandMessage(
        address = "Google",
        senderName = "Google",
        body = "849201 is your Google verification code.",
        category = SmsCategory.OTP,
        otpCode = "849201"
    )

    private val bankMessage = IslandMessage(
        address = "JM-KOTAKB-S",
        senderName = "JM-KOTAKB-S",
        body = "Sent Rs.2,450.00 from Kotak Bank AC X9102 to Dinner.",
        category = SmsCategory.FINANCE,
        transactionAmount = "−₹2,450",
        merchant = "Dinner"
    )

    private val personalMessage = IslandMessage(
        address = "+1234567890",
        senderName = "Sarah Jenkins",
        body = "Hey! Are you free for lunch today at 1?",
        category = SmsCategory.PERSONAL
    )

    @Test
    fun testSmartMode() {
        assertEquals("849201", formatPeekText(otpMessage, IslandPeekText.SMART, 20, false))
        assertEquals("−₹2,450", formatPeekText(bankMessage, IslandPeekText.SMART, 20, false))
        assertEquals("Sarah Jenkins", formatPeekText(personalMessage, IslandPeekText.SMART, 20, false))
    }

    @Test
    fun testSenderMode() {
        assertEquals("Google", formatPeekText(otpMessage, IslandPeekText.SENDER, 20, false))
        assertEquals("JM-KOTAKB-S", formatPeekText(bankMessage, IslandPeekText.SENDER, 20, false))
        assertEquals("Sarah Jenkins", formatPeekText(personalMessage, IslandPeekText.SENDER, 20, false))
    }

    @Test
    fun testPreviewMode() {
        assertEquals("849201 is your OTP", formatPeekText(otpMessage, IslandPeekText.PREVIEW, 30, false))
        assertEquals("−₹2,450 Dinner", formatPeekText(bankMessage, IslandPeekText.PREVIEW, 30, false))
        assertEquals("Hey! Are you free fo…", formatPeekText(personalMessage, IslandPeekText.PREVIEW, 20, false))
    }

    @Test
    fun testCategoryMode() {
        assertEquals("OTP", formatPeekText(otpMessage, IslandPeekText.CATEGORY, 20, false))
        assertEquals("Transactions", formatPeekText(bankMessage, IslandPeekText.CATEGORY, 20, false))
        assertEquals("Messages", formatPeekText(personalMessage, IslandPeekText.CATEGORY, 20, false))
    }

    @Test
    fun testNothingMode() {
        assertEquals("", formatPeekText(otpMessage, IslandPeekText.NOTHING, 20, false))
        assertEquals("", formatPeekText(bankMessage, IslandPeekText.NOTHING, 20, false))
        assertEquals("", formatPeekText(personalMessage, IslandPeekText.NOTHING, 20, false))
    }

    @Test
    fun testMaxChars_truncationWithEllipsis() {
        // "JM-KOTAKB-S" has 11 chars
        assertEquals("JM-KOTAKB-S", formatPeekText(bankMessage, IslandPeekText.SENDER, 12, false))
        // "Sarah Jenkins" has 13 chars
        assertEquals("Sarah Jenkin…", formatPeekText(personalMessage, IslandPeekText.SENDER, 12, false))
    }

    @Test
    fun testPrivacy_hideContentOverrides() {
        assertEquals("OTP", formatPeekText(otpMessage, IslandPeekText.SMART, 20, true))
        assertEquals("Transaction", formatPeekText(bankMessage, IslandPeekText.SMART, 20, true))
        assertEquals("Sarah Jenkins", formatPeekText(personalMessage, IslandPeekText.SMART, 20, true))
    }
}
