package com.ripple.chat.utils

import java.util.Calendar

data class OtpParseResult(
    val code: String,
    val expiresAt: Long,
    val durationMs: Long,
    val usedDefaultExpiry: Boolean
)

object OtpParser {

    private val KEYWORD_PATTERNS = listOf(
        "one time password",
        "one-time password",
        "verification code",
        "security code",
        "login code",
        "passcode",
        "code is",
        "otp"
    )

    fun parseOtp(messageText: String, receivedAt: Long): OtpParseResult? {
        if (messageText.isBlank()) return null
        val lowerText = messageText.lowercase()

        // 1. Check for keyword
        val keywordMatchIndex = findKeywordIndex(lowerText) ?: return null

        // 2. Extract valid code
        val code = extractCode(messageText, keywordMatchIndex) ?: return null

        // 3. Extract expiry duration / time
        val (expiresAt, durationMs, usedDefault) = parseExpiry(messageText, receivedAt)

        return OtpParseResult(
            code = code,
            expiresAt = expiresAt,
            durationMs = durationMs,
            usedDefaultExpiry = usedDefault
        )
    }

    private fun findKeywordIndex(lowerText: String): Int? {
        var firstIndex: Int? = null
        for (kw in KEYWORD_PATTERNS) {
            val idx = lowerText.indexOf(kw)
            if (idx != -1) {
                if (firstIndex == null || idx < firstIndex) {
                    firstIndex = idx
                }
            }
        }
        return firstIndex
    }

    private fun extractCode(text: String, keywordIndex: Int): String? {
        val sanitized = sanitizeTextForCodeExtraction(text)

        // Find numeric codes (4-8 digits)
        val numericRegex = Regex("\\b\\d{4,8}\\b")
        val numericMatches = numericRegex.findAll(sanitized).map { it.value to it.range.first }.toList()

        // Find alphanumeric codes (6-8 chars) near a keyword indicator
        val alphaNumRegex = Regex("(?i)(?:code|passcode|otp|is|:)\\s*[:\\-]?\\s*([a-zA-Z0-9]{6,8})\\b")
        val alphaNumMatches = alphaNumRegex.findAll(sanitized).mapNotNull { match ->
            val candidate = match.groupValues[1]
            if (candidate.any { it.isLetter() } && candidate.any { it.isDigit() }) {
                candidate to match.groups[1]!!.range.first
            } else null
        }.toList()

        val allCandidates = (numericMatches + alphaNumMatches)
        if (allCandidates.isEmpty()) return null

        // Pick candidate closest to keyword index
        val sortedByProximity = allCandidates.minByOrNull { kotlin.math.abs(it.second - keywordIndex) }
        return sortedByProximity?.first
    }

    private fun sanitizeTextForCodeExtraction(text: String): String {
        var s = text
        // Neutralize URLs
        s = s.replace(Regex("(?i)https?://\\S+"), " [URL_REMOVED] ")
        s = s.replace(Regex("(?i)www\\.\\S+"), " [URL_REMOVED] ")

        // Neutralize Currency amounts ($100, Rs. 500, ₹1000, INR 500, Rs 50.00)
        s = s.replace(Regex("(?i)(?:₹|rs\\.?|inr|\\$)\\s*[\\d,]+(?:\\.\\d+)?"), " [CURRENCY_REMOVED] ")

        // Neutralize Dates (2024-05-10, 10/05/2024, 10-05-2024)
        s = s.replace(Regex("\\b\\d{2,4}[\\-/.]\\d{1,2}[\\-/.]\\d{2,4}\\b"), " [DATE_REMOVED] ")

        // Neutralize Times (10:45, 14:30:00, 3:15 pm)
        s = s.replace(Regex("(?i)\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:am|pm)?\\b"), " [TIME_REMOVED] ")

        // Neutralize Phone numbers (10+ digits)
        s = s.replace(Regex("\\+?\\b\\d{10,12}\\b"), " [PHONE_REMOVED] ")

        // Neutralize Account/Card numbers ("XX4821", "ending 1234", "a/c 123456", "card 9876")
        s = s.replace(Regex("(?i)(?:ending|account|a/c|card|acct)\\s*[#:]?\\s*(?:x+|\\*+)?\\s*\\d{3,8}\\b"), " [ACCT_REMOVED] ")
        s = s.replace(Regex("(?i)\\b[x*]{2,}\\d{3,6}\\b"), " [ACCT_REMOVED] ")

        return s
    }

    private fun parseExpiry(text: String, receivedAt: Long): Triple<Long, Long, Boolean> {
        val lower = text.lowercase()

        // 1. Duration patterns
        val durationMatches = Regex("(?i)\\b(\\d+)\\s*(minutes|minute|mins|min|m|seconds|second|secs|sec|s|hours|hour|hrs|hr|h)\\b").findAll(lower)

        for (match in durationMatches) {
            val amount = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()

            val durationMs = when {
                unit.startsWith("s") -> amount * 1000L
                unit.startsWith("m") -> amount * 60 * 1000L
                unit.startsWith("h") -> amount * 60 * 60 * 1000L
                else -> null
            }

            if (durationMs != null && durationMs in 5_000L..86_400_000L) {
                return Triple(receivedAt + durationMs, durationMs, false)
            }
        }

        // 2. Absolute time patterns
        val timeRegex = Regex("(?i)(?:valid\\s*(?:till|until|up\\s*to)|expires\\s*at)\\s*(\\d{1,2}):(\\d{2})\\s*(am|pm)?")
        val timeMatch = timeRegex.find(lower)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 0
            val min = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            val targetCal = Calendar.getInstance().apply {
                timeInMillis = receivedAt
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (targetCal.timeInMillis <= receivedAt) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val durationMs = targetCal.timeInMillis - receivedAt
            if (durationMs > 0 && durationMs <= 86_400_000L) {
                return Triple(targetCal.timeInMillis, durationMs, false)
            }
        }

        // 3. Default 10 minutes
        val defaultDurationMs = 10 * 60 * 1000L
        return Triple(receivedAt + defaultDurationMs, defaultDurationMs, true)
    }
}
