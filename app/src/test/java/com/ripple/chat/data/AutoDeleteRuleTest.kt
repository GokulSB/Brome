package com.ripple.chat.data

import com.ripple.chat.utils.OtpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDeleteRuleTest {

    private fun isThreadExpiredOtp(
        thread: ChatThread,
        otpSetting: OtpAutoDeleteTime,
        starredIds: Set<Long>,
        now: Long
    ): Boolean {
        if (otpSetting == OtpAutoDeleteTime.NEVER || thread.threadId in starredIds) return false
        val isOtp = thread.category == SmsCategory.OTP || OtpParser.parseOtp(thread.body, thread.date) != null
        val maxOtpAgeMs = otpSetting.hours * 3600 * 1000L
        val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
        return isOtp && (now - msgTime >= maxOtpAgeMs)
    }

    private fun isThreadExpiredSpam(
        thread: ChatThread,
        spamSetting: SpamAutoDeleteTime,
        starredIds: Set<Long>,
        now: Long
    ): Boolean {
        if (spamSetting == SpamAutoDeleteTime.NEVER || thread.threadId in starredIds) return false
        val isSpam = thread.category == SmsCategory.SPAM
        val maxSpamAgeMs = spamSetting.days * 24 * 3600 * 1000L
        val msgTime = if (thread.date < 100_000_000_000L) thread.date * 1000L else thread.date
        return isSpam && (now - msgTime >= maxSpamAgeMs)
    }

    @Test
    fun testOtpAutoDelete1Hour_expiredOtpMessage() {
        val now = 1_700_000_000_000L
        val twoHoursAgo = now - (2 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 1L,
            address = "VK-HDFCBK",
            body = "Your OTP is 482910. Valid for 10 minutes.",
            date = twoHoursAgo,
            read = true,
            type = 1,
            category = SmsCategory.OTP
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.HOUR_1, emptySet(), now)
        assertTrue("OTP from 2 hours ago should be expired when rule is 1 hour", isExpired)
    }

    @Test
    fun testOtpAutoDelete1Hour_recentOtpMessageNotExpired() {
        val now = 1_700_000_000_000L
        val thirtyMinsAgo = now - (30 * 60 * 1000L)

        val thread = ChatThread(
            threadId = 2L,
            address = "VK-HDFCBK",
            body = "Your OTP is 482910. Valid for 10 minutes.",
            date = thirtyMinsAgo,
            read = true,
            type = 1,
            category = SmsCategory.OTP
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.HOUR_1, emptySet(), now)
        assertFalse("OTP from 30 mins ago should NOT be expired when rule is 1 hour", isExpired)
    }

    @Test
    fun testOtpAutoDeleteNever_neverExpires() {
        val now = 1_700_000_000_000L
        val thirtyDaysAgo = now - (30L * 24 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 3L,
            address = "VK-HDFCBK",
            body = "Your OTP is 482910. Valid for 10 minutes.",
            date = thirtyDaysAgo,
            read = true,
            type = 1,
            category = SmsCategory.OTP
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.NEVER, emptySet(), now)
        assertFalse("OTP should NEVER expire when rule is NEVER", isExpired)
    }

    @Test
    fun testOtpAutoDelete_starredThreadsNeverDeleted() {
        val now = 1_700_000_000_000L
        val twoHoursAgo = now - (2 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 4L,
            address = "VK-HDFCBK",
            body = "Your OTP is 482910. Valid for 10 minutes.",
            date = twoHoursAgo,
            read = true,
            type = 1,
            category = SmsCategory.OTP
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.HOUR_1, setOf(4L), now)
        assertFalse("Starred thread must never be auto-deleted", isExpired)
    }

    @Test
    fun testSpamAutoDelete1Day_expiredSpamMessage() {
        val now = 1_700_000_000_000L
        val twoDaysAgo = now - (2 * 24 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 5L,
            address = "+1234567890",
            body = "Claim your free prize now! Visit spam.link",
            date = twoDaysAgo,
            read = true,
            type = 1,
            category = SmsCategory.SPAM
        )

        val isExpired = isThreadExpiredSpam(thread, SpamAutoDeleteTime.DAY_1, emptySet(), now)
        assertTrue("Spam from 2 days ago should be expired when rule is 1 day", isExpired)
    }

    @Test
    fun testSpamAutoDelete1Day_recentSpamNotExpired() {
        val now = 1_700_000_000_000L
        val twelveHoursAgo = now - (12 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 6L,
            address = "+1234567890",
            body = "Claim your free prize now! Visit spam.link",
            date = twelveHoursAgo,
            read = true,
            type = 1,
            category = SmsCategory.SPAM
        )

        val isExpired = isThreadExpiredSpam(thread, SpamAutoDeleteTime.DAY_1, emptySet(), now)
        assertFalse("Spam from 12 hours ago should NOT be expired when rule is 1 day", isExpired)
    }

    @Test
    fun testTimestampNormalization_secondsHandledCorrectly() {
        val now = 1_700_000_000_000L
        val twoHoursAgoSeconds = (now - (2 * 3600 * 1000L)) / 1000L

        val thread = ChatThread(
            threadId = 7L,
            address = "VK-HDFCBK",
            body = "Your OTP is 482910. Valid for 10 minutes.",
            date = twoHoursAgoSeconds, // 10-digit unix seconds
            read = true,
            type = 1,
            category = SmsCategory.OTP
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.HOUR_1, emptySet(), now)
        assertTrue("Timestamp in unix seconds should be normalized to ms and expired", isExpired)
    }

    @Test
    fun testOtpDetectionByBodyParsing() {
        val now = 1_700_000_000_000L
        val twoHoursAgo = now - (2 * 3600 * 1000L)

        val thread = ChatThread(
            threadId = 8L,
            address = "AX-FLPKRT",
            body = "Ä#Ñ 454281 is your LOGIN OTP for Flipkart. DO NOT SHARE this code with anyone.",
            date = twoHoursAgo,
            read = true,
            type = 1,
            category = SmsCategory.PERSONAL // Even if misclassified as PERSONAL initially
        )

        val isExpired = isThreadExpiredOtp(thread, OtpAutoDeleteTime.HOUR_1, emptySet(), now)
        assertTrue("Thread with OTP in body should be recognized and expired", isExpired)
    }
}
