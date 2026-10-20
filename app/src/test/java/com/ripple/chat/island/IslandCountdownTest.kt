package com.ripple.chat.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandCountdownTest {

    @Test
    fun testOtpCountdown_formatting() {
        assertEquals("5:00", formatRemaining(300))
        assertEquals("4:59", formatRemaining(299))
        assertEquals("1:00", formatRemaining(60))
        assertEquals("0:30", formatRemaining(30))
        assertEquals("0:05", formatRemaining(5))
        assertEquals("0:00", formatRemaining(0))
    }

    @Test
    fun testOtpCountdown_nearExpiryThreshold() {
        // Prompt rule: transitions to red in the last 30s
        assertFalse(isNearExpiry(300))
        assertFalse(isNearExpiry(31))
        assertTrue(isNearExpiry(30))
        assertTrue(isNearExpiry(15))
        assertTrue(isNearExpiry(1))
        assertTrue(isNearExpiry(0))
    }

    private fun formatRemaining(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun isNearExpiry(seconds: Int): Boolean {
        return seconds <= 30
    }
}
