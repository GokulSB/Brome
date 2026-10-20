package com.ripple.chat.utils

import com.ripple.chat.sms.AddressNormalizer

object MessageParser {
    
    fun extractOtp(body: String): String? {
        val regex = Regex("\\b\\d{4,8}\\b")
        return regex.find(body)?.value
    }
    
    fun extractValidityDurationMillis(body: String): Long? {
        val lowerBody = body.lowercase()
        val regex = Regex("\\b(\\d+)\\s*(min|minute|minutes|sec|second|seconds|hrs|hour|hours)\\b")
        val match = regex.find(lowerBody)
        if (match != null) {
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            val unit = match.groupValues[2]
            return when {
                unit.startsWith("sec") -> amount * 1000L
                unit.startsWith("min") -> amount * 60 * 1000L
                unit.startsWith("h") -> amount * 60 * 60 * 1000L
                else -> null
            }
        }
        return null
    }

    fun extractUrl(body: String): String? {
        val regex = Regex("(https?://[^\\s]+)")
        return regex.find(body)?.value
    }

    fun extractPhoneNumber(body: String): String? {
        val regex = Regex("\\b[6-9]\\d{9}\\b")
        return regex.find(body)?.value
    }

    fun cleanAddress(raw: String?): String? {
        val sanitized = AddressNormalizer.cleanAndSanitize(raw)
        if (sanitized.isBlank()) return null
        if (sanitized.contains(",") || sanitized.contains(";")) {
            val parts = sanitized.split(Regex("[,;]+")).mapNotNull { cleanSingleAddress(it) }
            return if (parts.isNotEmpty()) parts.joinToString(",") else sanitized
        }
        return cleanSingleAddress(sanitized)
    }

    private fun cleanSingleAddress(raw: String?): String? {
        val sanitized = AddressNormalizer.cleanAndSanitize(raw)
        if (sanitized.isBlank()) return null
        // If it contains letters (like "VM-HDFCBK", "AD-AMZNIN", "Google"), it is an alphanumeric sender ID
        if (sanitized.any { it.isLetter() }) {
            return sanitized
        }
        val hasPlus = sanitized.startsWith("+") || sanitized.contains("+")
        var digitsOnly = sanitized.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return sanitized

        // Strip leading trunk 0 if 11 digits (e.g. 09876543210 -> 9876543210)
        if (digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
            digitsOnly = digitsOnly.substring(1)
        }

        return if (hasPlus) {
            "+$digitsOnly"
        } else if (digitsOnly.length == 12 && digitsOnly.startsWith("91")) {
            "+$digitsOnly"
        } else {
            digitsOnly
        }
    }
}
