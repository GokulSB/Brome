package com.ripple.chat.sms

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object AddressNormalizer {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    /**
     * Strips all invisible Unicode characters, bidirectional markers, zero-width spaces,
     * non-breaking spaces, and leading/trailing whitespace.
     */
    fun cleanAndSanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        // Replace non-breaking spaces with standard space
        val normalizedSpaces = raw.replace(Regex("[\u00A0\u202F]"), " ")
        // Remove directional formatting characters (\u200E LRM, \u200F RLM, \u202A-\u202E, \u2066-\u2069),
        // and zero-width characters (\u200B-\u200D, \uFEFF)
        return normalizedSpaces.replace(Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069\u200B-\u200D\uFEFF]"), "")
            .trim()
    }

    fun getDefaultRegion(context: Context): String {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_PHONE_STATE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val sm = context.getSystemService(SubscriptionManager::class.java)
                    sm?.activeSubscriptionInfoList?.forEach { info ->
                        val country = info.countryIso?.uppercase(Locale.ROOT)
                        if (!country.isNullOrBlank()) return country
                    }
                }
            }
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            if (!simCountry.isNullOrBlank()) return simCountry

            val networkCountry = tm?.networkCountryIso?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            if (!networkCountry.isNullOrBlank()) return networkCountry
        } catch (_: Exception) {}

        val localeCountry = Locale.getDefault().country.uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
        if (!localeCountry.isNullOrBlank()) return localeCountry

        return "IN"
    }

    /**
     * Checks if the raw address is an alphanumeric sender ID (e.g. "JM-KOTAKB-S", "VM-HDFCBK", "Google").
     * Such addresses are one-way service headers and can NEVER be sent to.
     */
    fun isAlphanumericSender(raw: String?): Boolean {
        val sanitized = cleanAndSanitize(raw)
        if (sanitized.isBlank()) return false
        return sanitized.any { it.isLetter() }
    }

    /**
     * Returns true if the address can be sent to (is a valid phone destination, not alphanumeric).
     */
    fun isSendable(raw: String?): Boolean {
        val sanitized = cleanAndSanitize(raw)
        if (sanitized.isBlank()) return false
        if (isAlphanumericSender(sanitized)) return false
        val digits = sanitized.filter { it.isDigit() }
        return digits.length >= 3 // Supports shortcodes (e.g. 1901) and standard mobile numbers
    }

    /**
     * Normalizes a phone number for SMS sending:
     * - Strips invisible unicode characters, spaces, dashes, brackets, parentheses.
     * - Handles international + formats and national domestic formats.
     * - Blocks alphanumeric senders (returns null).
     */
    fun normalize(context: Context, raw: String?): String? {
        val sanitized = cleanAndSanitize(raw)
        if (sanitized.isBlank()) return null
        if (isAlphanumericSender(sanitized)) return null

        val defaultRegion = getDefaultRegion(context)
        return normalizeWithRegion(sanitized, defaultRegion)
    }

    fun normalizeWithRegion(raw: String?, defaultRegion: String): String? {
        val sanitized = cleanAndSanitize(raw)
        if (sanitized.isBlank()) return null
        if (isAlphanumericSender(sanitized)) return null

        val hasPlus = sanitized.startsWith("+")
        var digitsOnly = sanitized.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return null

        // Strip leading trunk 0 if present (e.g. 09876543210 -> 9876543210)
        if (digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
            digitsOnly = digitsOnly.substring(1)
        }

        // Try libphonenumber parsing
        try {
            val candidate = if (hasPlus) "+$digitsOnly" else digitsOnly
            val parsed = phoneUtil.parse(candidate, defaultRegion)
            if (phoneUtil.isValidNumber(parsed) || phoneUtil.isPossibleNumber(parsed)) {
                return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            }
        } catch (_: Exception) {
            // Fallback heuristics below
        }

        return if (hasPlus) {
            "+$digitsOnly"
        } else if (digitsOnly.length == 12 && digitsOnly.startsWith("91")) {
            "+$digitsOnly"
        } else if (defaultRegion == "IN" && digitsOnly.length == 10 && digitsOnly[0] in '6'..'9') {
            "+91$digitsOnly"
        } else if (defaultRegion == "US" && digitsOnly.length == 10) {
            "+1$digitsOnly"
        } else {
            digitsOnly
        }
    }

    /**
     * Produces a canonical comparison key to merge duplicate threads representing the same contact
     * (e.g. "+91 98765 43210", "098765 43210", "9876543210").
     */
    fun canonicalKey(raw: String?): String {
        val sanitized = cleanAndSanitize(raw)
        if (sanitized.isBlank()) return ""
        if (isAlphanumericSender(sanitized)) {
            return sanitized.uppercase(Locale.ROOT)
        }
        var digits = sanitized.filter { it.isDigit() }
        if (digits.length == 11 && digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return if (digits.length >= 10) {
            digits.takeLast(10)
        } else {
            digits
        }
    }
}
