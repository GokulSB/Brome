package com.ripple.chat.utils

import org.junit.Assert.*
import org.junit.Test

class OtpParserTest {

    @Test
    fun testNumericOtpWithMinutes() {
        val now = System.currentTimeMillis()
        val text = "Your verification code is 482913. Valid for 10 minutes."
        val result = OtpParser.parseOtp(text, now)

        assertNotNull(result)
        assertEquals("482913", result!!.code)
        assertEquals(now + 10 * 60 * 1000L, result.expiresAt)
        assertFalse(result.usedDefaultExpiry)
    }

    @Test
    fun testAlphanumericOtpWithShortMinutes() {
        val now = System.currentTimeMillis()
        val text = "Your passcode is A1B2C3. Valid for 5 mins."
        val result = OtpParser.parseOtp(text, now)

        assertNotNull(result)
        assertEquals("A1B2C3", result!!.code)
        assertEquals(now + 5 * 60 * 1000L, result.expiresAt)
        assertFalse(result.usedDefaultExpiry)
    }

    @Test
    fun testCurrencyExclusion() {
        val now = System.currentTimeMillis()
        val text = "Your OTP is 987654 for purchase of Rs. 1500. Valid for 15 minutes."
        val result = OtpParser.parseOtp(text, now)

        assertNotNull(result)
        assertEquals("987654", result!!.code)
        assertEquals(now + 15 * 60 * 1000L, result.expiresAt)
    }

    @Test
    fun testAccountNumberExclusion() {
        val now = System.currentTimeMillis()
        val text = "OTP for transaction on card ending 4821 is 123456."
        val result = OtpParser.parseOtp(text, now)

        assertNotNull(result)
        assertEquals("123456", result!!.code)
    }

    @Test
    fun testNoExpiryDefaultTenMinutes() {
        val now = System.currentTimeMillis()
        val text = "Your login code is 889900."
        val result = OtpParser.parseOtp(text, now)

        assertNotNull(result)
        assertEquals("889900", result!!.code)
        assertEquals(now + 10 * 60 * 1000L, result.expiresAt)
        assertTrue(result.usedDefaultExpiry)
    }

    @Test
    fun testNonOtpMessage() {
        val now = System.currentTimeMillis()
        val text = "Hey, what's up? Call me at 9876543210."
        val result = OtpParser.parseOtp(text, now)

        assertNull(result)
    }
}
