package com.ripple.chat.utils

import java.net.URI
import java.util.Locale

enum class ScamRiskLevel {
    LOW_RISK,
    SUSPICIOUS,
    HIGH_RISK
}

data class ExtractedLink(
    val url: String,
    val startIndex: Int,
    val endIndex: Int
)

data class LinkSafetyAnalysis(
    val originalUrl: String,
    val normalizedUrl: String,
    val host: String,
    val riskLevel: ScamRiskLevel,
    val warnings: List<String>,
    val isShortener: Boolean,
    val isIpAddress: Boolean,
    val isInsecureHttp: Boolean,
    val isImpersonation: Boolean,
    val isExecutable: Boolean
)

object LinkSafetyChecker {

    // Regex matching HTTP/HTTPS URLs, www. addresses, domain names with common TLDs, and IPv4 addresses
    private val URL_REGEX = Regex(
        """(?i)\b(?:https?://[^\s<>"]+|www\.[^\s<>"]+|(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+(?:com|org|net|edu|gov|mil|io|ai|co|info|biz|me|tv|cc|in|uk|us|ca|au|de|fr|jp|ru|cn|br|xyz|top|site|online|tech|store|app|dev|pro|live|club|space|fun|buzz|click|link|work|loan|vip|today|icu|shop|mobi|asia|lat|best|bid|trade|date|review|download|stream|racing|accountant|kim|party|gq|tk|ml|cf|ga|ly|is|gd|to|gl|so|be|ch|nl|se|no|es|it|eu)(?::\d{1,5})?(?:/[^\s<>"]*)?|(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,12}/[^\s<>"]*|(?:https?://)?(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?(?:/[^\s<>"]*)?)"""
    )

    private val IP_REGEX = Regex("""^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""")

    // Common URL shorteners used in SMS phishing
    private val URL_SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly", "rb.gy",
        "shorturl.at", "tiny.cc", "ow.ly", "bit.do", "lnkd.in", "rebrand.ly",
        "t.ly", "v.gd", "goo.gl", "qr.ae", "adf.ly", "bl.ink", "buff.ly",
        "s.id", "rotf.lol", "clck.ru", "shorte.st", "bc.vc", "snip.ly"
    )

    // TLDs frequently abused in smishing campaigns
    private val HIGH_RISK_TLDS = setOf(
        "xyz", "top", "buzz", "club", "work", "click", "loan", "gq", "tk",
        "ml", "cf", "ga", "rest", "country", "stream", "download", "racing",
        "accountant", "kim", "party", "trade", "bid", "date", "review",
        "icu", "vip", "monster", "cfd", "sbs", "skin", "quest", "beauty",
        "hair", "fit", "surf", "cam", "live", "lat", "asia", "today"
    )

    // Dangerous download extensions
    private val DANGEROUS_EXTENSIONS = setOf(
        ".apk", ".exe", ".scr", ".bat", ".vbs", ".msi", ".jar", ".cmd", ".ps1", ".iso", ".dmg"
    )

    // Phishing / smishing keywords often present in malicious hosts
    private val PHISHING_KEYWORDS = listOf(
        "login", "signin", "verify", "verification", "secure", "security",
        "authenticate", "wallet", "kyc", "account-update", "update-account",
        "billing", "suspended", "confirm", "claim", "refund", "support-help",
        "password", "passcode", "otp-verify"
    )

    private data class BrandProfile(
        val brandName: String,
        val brandKeywords: List<String>,
        val legitimateDomains: Set<String>
    )

    private val BRAND_PROFILES = listOf(
        BrandProfile("PayPal", listOf("paypal"), setOf("paypal.com", "paypal.me")),
        BrandProfile("Google", listOf("google", "gmail"), setOf("google.com", "google.co.in", "google.co.uk", "goo.gl", "youtube.com", "gmail.com")),
        BrandProfile("Apple", listOf("apple", "icloud"), setOf("apple.com", "icloud.com")),
        BrandProfile("Amazon", listOf("amazon"), setOf("amazon.com", "amazon.in", "amazon.co.uk", "amzn.to")),
        BrandProfile("Microsoft", listOf("microsoft", "outlook"), setOf("microsoft.com", "live.com", "office.com", "outlook.com")),
        BrandProfile("Netflix", listOf("netflix"), setOf("netflix.com")),
        BrandProfile("Meta", listOf("facebook", "instagram", "whatsapp"), setOf("facebook.com", "fb.com", "instagram.com", "whatsapp.com", "meta.com")),
        BrandProfile("Chase Bank", listOf("chase"), setOf("chase.com")),
        BrandProfile("Bank of America", listOf("bankofamerica", "bofa"), setOf("bankofamerica.com", "bofa.com")),
        BrandProfile("Wells Fargo", listOf("wellsfargo"), setOf("wellsfargo.com")),
        BrandProfile("Citibank", listOf("citibank"), setOf("citi.com", "citibank.com")),
        BrandProfile("Capital One", listOf("capitalone"), setOf("capitalone.com")),
        BrandProfile("Venmo", listOf("venmo"), setOf("venmo.com")),
        BrandProfile("Cash App", listOf("cashapp"), setOf("cash.app")),
        BrandProfile("Zelle", listOf("zelle"), setOf("zellepay.com")),
        BrandProfile("Coinbase", listOf("coinbase"), setOf("coinbase.com")),
        BrandProfile("Binance", listOf("binance"), setOf("binance.com")),
        BrandProfile("Stripe", listOf("stripe"), setOf("stripe.com")),
        BrandProfile("USPS", listOf("usps"), setOf("usps.com")),
        BrandProfile("FedEx", listOf("fedex"), setOf("fedex.com")),
        BrandProfile("UPS", listOf("ups"), setOf("ups.com")),
        BrandProfile("DHL", listOf("dhl"), setOf("dhl.com")),
        BrandProfile("India Post", listOf("indiapost"), setOf("indiapost.gov.in")),
        BrandProfile("SBI Bank", listOf("sbi", "onlinesbi"), setOf("sbi.co.in", "onlinesbi.sbi")),
        BrandProfile("HDFC Bank", listOf("hdfc"), setOf("hdfcbank.com")),
        BrandProfile("ICICI Bank", listOf("icici"), setOf("icicibank.com")),
        BrandProfile("Paytm", listOf("paytm"), setOf("paytm.com")),
        BrandProfile("PhonePe", listOf("phonepe"), setOf("phonepe.com")),
        BrandProfile("Telegram", listOf("telegram"), setOf("telegram.org", "t.me")),
        BrandProfile("Twitter / X", listOf("twitter"), setOf("twitter.com", "x.com", "t.co"))
    )

    fun extractLinks(text: String): List<ExtractedLink> {
        if (text.isBlank()) return emptyList()
        val results = mutableListOf<ExtractedLink>()
        val matches = URL_REGEX.findAll(text)
        for (m in matches) {
            var raw = m.value
            var start = m.range.first
            var end = m.range.last + 1

            // Strip trailing punctuation often found in natural sentences
            while (raw.isNotEmpty() && (raw.endsWith('.') || raw.endsWith(',') || raw.endsWith(';') ||
                                         raw.endsWith('!') || raw.endsWith('?') || raw.endsWith(')') ||
                                         raw.endsWith(']') || raw.endsWith('>') || raw.endsWith('"') ||
                                         raw.endsWith('\''))) {
                raw = raw.dropLast(1)
                end -= 1
            }

            if (raw.isNotBlank()) {
                results.add(ExtractedLink(url = raw, startIndex = start, endIndex = end))
            }
        }
        return results
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun extractHost(normalizedUrl: String): String {
        return try {
            val uri = URI(normalizedUrl)
            uri.host?.lowercase(Locale.ROOT) ?: parseHostManually(normalizedUrl)
        } catch (_: Exception) {
            parseHostManually(normalizedUrl)
        }
    }

    private fun parseHostManually(normalizedUrl: String): String {
        val withoutScheme = normalizedUrl.substringAfter("://")
        val beforeSlash = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val beforePort = beforeSlash.substringBefore(':')
        val afterAt = beforePort.substringAfterLast('@')
        return afterAt.lowercase(Locale.ROOT).trim()
    }

    fun analyze(rawUrl: String): LinkSafetyAnalysis {
        val normalized = normalizeUrl(rawUrl)
        val host = extractHost(normalized)
        val warnings = mutableListOf<String>()

        var isHighRisk = false
        var suspiciousCount = 0

        // 1. Raw IP address check
        val isIp = IP_REGEX.matches(host)
        if (isIp) {
            isHighRisk = true
            warnings.add("Host is a numeric IP address ($host). Legitimate companies use domain names.")
        }

        // 2. Insecure HTTP check
        val isInsecure = normalized.startsWith("http://", ignoreCase = true)
        if (isInsecure) {
            suspiciousCount++
            warnings.add("Connection is unencrypted (HTTP). Data entered can be viewed or modified by third parties.")
        }

        // 3. URL shortener check
        val isShortener = URL_SHORTENERS.any { host == it || host.endsWith(".$it") }
        if (isShortener) {
            suspiciousCount++
            warnings.add("Link shortener ($host) conceals the true destination website.")
        }

        // 4. Punycode / IDN spoofing check
        val isPunycode = host.startsWith("xn--") || host.contains(".xn--")
        if (isPunycode) {
            isHighRisk = true
            warnings.add("Contains encoded international characters (punycode) often used for lookalike domain spoofing.")
        }

        // 5. Embedded credentials / '@' check
        val hasAtSymbol = normalized.substringBefore('?').substringBefore('#').contains('@')
        if (hasAtSymbol) {
            isHighRisk = true
            warnings.add("Contains an '@' character before the host, a common technique to deceive users about the destination.")
        }

        // 6. Dangerous executable / application download
        val pathAndQuery = normalized.substringAfter(host, "").substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        val isExecutable = DANGEROUS_EXTENSIONS.any { pathAndQuery.endsWith(it) }
        if (isExecutable) {
            isHighRisk = true
            val ext = DANGEROUS_EXTENSIONS.first { pathAndQuery.endsWith(it) }
            warnings.add("Direct download of an installable application or script file ($ext), which may contain malware.")
        }

        // 7. Brand Impersonation check
        var isImpersonation = false
        for (profile in BRAND_PROFILES) {
            val matchesBrand = profile.brandKeywords.any { host.contains(it) }
            if (matchesBrand) {
                val isLegitimate = profile.legitimateDomains.any { legitimate ->
                    host == legitimate || host.endsWith(".$legitimate")
                }
                if (!isLegitimate) {
                    isImpersonation = true
                    isHighRisk = true
                    warnings.add("Possible impersonation of ${profile.brandName}: URL mentions '${profile.brandName}', but the domain is '$host', which is not official.")
                    break
                }
            }
        }

        // 8. Suspicious / Abused TLDs
        val tld = host.substringAfterLast('.', "")
        if (tld in HIGH_RISK_TLDS) {
            suspiciousCount++
            warnings.add("Domain uses '.$tld', an extension frequently associated with spam and phishing.")
        }

        // 9. Phishing keywords in hostname
        val matchedKeyword = PHISHING_KEYWORDS.firstOrNull { host.contains(it) }
        if (matchedKeyword != null && !isShortener) {
            suspiciousCount++
            warnings.add("Hostname contains '$matchedKeyword', commonly used in credential phishing.")
        }

        // 10. Excessive Subdomains (4+ dots in host)
        val dotCount = host.count { it == '.' }
        if (dotCount >= 4 && !isIp) {
            suspiciousCount++
            warnings.add("Contains multiple nested subdomains, which may be attempting to conceal the root domain.")
        }

        val riskLevel = when {
            isHighRisk || suspiciousCount >= 2 -> ScamRiskLevel.HIGH_RISK
            suspiciousCount == 1 -> ScamRiskLevel.SUSPICIOUS
            else -> ScamRiskLevel.LOW_RISK
        }

        return LinkSafetyAnalysis(
            originalUrl = rawUrl,
            normalizedUrl = normalized,
            host = host.ifBlank { rawUrl },
            riskLevel = riskLevel,
            warnings = warnings,
            isShortener = isShortener,
            isIpAddress = isIp,
            isInsecureHttp = isInsecure,
            isImpersonation = isImpersonation,
            isExecutable = isExecutable
        )
    }
}
