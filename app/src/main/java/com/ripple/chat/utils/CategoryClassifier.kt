package com.ripple.chat.utils

import com.ripple.chat.data.SmsCategory

object CategoryClassifier {

    private fun Int.addIfContains(text: String, keyword: String, score: Int): Int {
        return if (text.contains(keyword, ignoreCase = true)) this + score else this
    }
    
    private fun Int.addIfRegexMatches(text: String, pattern: String, score: Int): Int {
        return if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)) this + score else this
    }

    fun isMeaninglessOrGibberish(body: String): Boolean {
        val clean = body.trim()
        if (clean.isBlank()) return false
        
        val words = clean.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 3 }
        if (words.isEmpty()) {
            val lettersOnly = clean.filter { it.isLetter() }
            if (lettersOnly.length >= 6) {
                val vowels = lettersOnly.count { it.lowercaseChar() in "aeiouy" }
                if (vowels.toDouble() / lettersOnly.length < 0.15) {
                    return true
                }
            }
            return false
        }

        val keyboardMashes = listOf(
            "asdf", "sdfg", "dfgh", "fghj", "ghjk", "hjkl",
            "qwer", "wert", "erty", "rtyu", "tyui", "yuio", "uiop",
            "zxcv", "xcvb", "cvbn", "vbnm",
            "fdsa", "gfds", "hgfd", "jhgf", "kjhg", "lkjh",
            "poiuy", "oiutr", "iutre", "ytrew", "trewq",
            "mnbv", "nbvc", "bvcx", "vcxz",
            "asdasd", "sdasda", "qweqwe", "zaxscd", "lkasjd", "sdkfj"
        )
        
        val commonAbbrevs = setOf(
            "http", "https", "html", "mgmt", "dept", "govt", "acct", "pswd",
            "sync", "rsvp", "ctrl", "fbclid", "gclid", "spam", "sms", "mms"
        )

        var gibberishCount = 0
        var totalCheckableWords = 0

        for (word in words) {
            val lower = word.lowercase()
            if (commonAbbrevs.contains(lower)) continue

            val lettersInWord = lower.filter { it.isLetter() }
            if (lettersInWord.length >= 4) {
                totalCheckableWords++
                
                // 1. Keyboard mash
                if (keyboardMashes.any { lower.contains(it) }) {
                    gibberishCount++
                    continue
                }
                
                // 2. No vowels in word of length >= 4
                val vowels = lettersInWord.count { it in "aeiouy" }
                if (vowels == 0) {
                    gibberishCount++
                    continue
                }
                
                // 3. 5+ consecutive consonants
                if (Regex("[bcdfghjklmnpqrstvwxz]{5,}").containsMatchIn(lower)) {
                    gibberishCount++
                    continue
                }
                
                // 4. Very low vowel ratio in word length >= 6
                if (lettersInWord.length >= 6 && (vowels.toDouble() / lettersInWord.length) < 0.16) {
                    gibberishCount++
                    continue
                }
                
                // 5. Repeated characters: 4+ in a row (e.g. "aaaa")
                if (Regex("([a-z])\\1{3,}").containsMatchIn(lower)) {
                    gibberishCount++
                    continue
                }
                
                // 6. Repeated 2-character pattern 3+ times (e.g. asdasd, dfdfdf)
                if (Regex("([a-z]{2})\\1{2,}").containsMatchIn(lower) && !lower.contains("haha") && !lower.contains("hehe")) {
                    gibberishCount++
                    continue
                }
            }
        }

        if (totalCheckableWords > 0 && (gibberishCount.toDouble() / totalCheckableWords) >= 0.35) {
            return true
        }
        if (gibberishCount >= 1 && words.size <= 2) {
            return true
        }
        
        val allLetters = clean.filter { it.isLetter() }
        if (allLetters.length >= 8) {
            val totalVowels = allLetters.count { it.lowercaseChar() in "aeiouy" }
            if (totalVowels.toDouble() / allLetters.length < 0.15) {
                return true
            }
        }
        
        return false
    }

    val TICKET_BRANDS = listOf(
        "BOOKMY", "BMS", "REDBUS", "ABHIBUS", "PVRCIN", "PVR", "INOX", "CINEPOLIS", "CINEPL",
        "IRCTC", "CONFIRMTKT", "CNFTKT", "MAKEMYTRIP", "MMTRIP", "GOIBIBO", "GOIBIB",
        "IXIGO", "YATRA", "EASEMYTRIP", "EASMYT", "CLEARTRIP", "CLRTRP", "TICKETNEW", "TCKTNW",
        "CARNIVAL", "MIRAJ", "KSRTC", "MSRTC", "APSRTC", "TSRTC", "GSRTC", "UPSRTC", "HRTC", "RSRTC",
        "SETC", "VRL", "SRS", "INTRCTY", "CHARTR", "CHTRTR", "ZINGBUS", "INDIGO", "AIRINDIA", "AIRIND",
        "SPICEJET", "SPICEJ", "VISTARA", "VISTAR", "AKASA"
    )

    fun isHighConfidenceTicket(address: String, body: String, contactName: String? = null): Boolean {
        val addressUpper = address.uppercase()
        val contactUpper = contactName?.uppercase().orEmpty()
        val text = body.lowercase()

        // Exclude blatant scams unless sender is an authenticated ticket brand
        val isKnownTicketSender = TICKET_BRANDS.any { brand ->
            addressUpper.contains(brand) || contactUpper.contains(brand)
        }

        val hasScamWords = text.contains("lottery") || text.contains("lucky draw") ||
                text.contains("casino") || text.contains("1xbet") || text.contains("bet365") ||
                text.contains("earn money") || text.contains("work from home") ||
                text.contains("double your money") || text.contains("rummy")

        if (hasScamWords && !isKnownTicketSender) {
            return false
        }

        val hasPnrPattern = Regex("""\bpnr\s*[:#-]?\s*[A-Z0-9]{5,12}\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasBookingIdPattern = Regex("""\b(?:booking\s*(?:id|ref|no|reference)|ticket\s*(?:no|id)|order\s*id|trip\s*id)\s*[:#-]?\s*[A-Z0-9-]{4,20}\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasTicketType = text.contains("m-ticket") || text.contains("e-ticket") || text.contains("boarding pass")
        val hasAudiPattern = Regex("""\baudi\s*[-#]?\s*\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasScreenPattern = Regex("""\bscreen\s*[-#]?\s*\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasSeat = Regex("""\b(?:seat|seats|seat\s*no|berth)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasBookingConfirmation = text.contains("confirmed") || text.contains("booked") || text.contains("reservation")

        // 1. Known Ticket Provider / Brand Sender
        if (isKnownTicketSender) {
            val hasBookingProof = hasPnrPattern || hasBookingIdPattern || hasTicketType ||
                    hasSeat || hasAudiPattern || hasScreenPattern ||
                    hasBookingConfirmation ||
                    text.contains("boarding") || text.contains("pickup") ||
                    text.contains("m-ticket") || text.contains("e-ticket") ||
                    text.contains("track")
            if (hasBookingProof) return true
        }

        // 2. Structured Booking Identifiers: PNR, Booking ID, Ref No with confirmation
        if (hasPnrPattern) return true
        if (hasBookingIdPattern && (hasBookingConfirmation || hasSeat || text.contains("ticket") || text.contains("bus") || text.contains("movie") || text.contains("flight") || text.contains("train"))) {
            return true
        }
        if (hasTicketType) return true

        // 3. Movie Ticket Confirmation:
        val hasMovieKeywords = text.contains("movie") || text.contains("cinema") || text.contains("multiplex") ||
                text.contains("theatre") || text.contains("bookmyshow") || text.contains("pvr") ||
                text.contains("inox") || text.contains("cinepolis") || text.contains("showtime") ||
                text.contains("show time")

        if ((hasAudiPattern || hasScreenPattern) && (hasSeat || hasBookingConfirmation || hasMovieKeywords)) {
            return true
        }
        if (hasMovieKeywords && hasSeat && hasBookingConfirmation) {
            return true
        }

        // 4. Bus Ticket Confirmation:
        val hasBusKeyword = text.contains("bus") || text.contains("sleeper") || text.contains("seater") ||
                text.contains("volvo") || text.contains("redbus") || text.contains("abhibus") ||
                text.contains("ksrtc") || text.contains("msrtc") || text.contains("apsrtc") ||
                text.contains("tsrtc") || text.contains("travels")
        val hasBusTravelDetails = text.contains("boarding") || text.contains("pickup") ||
                text.contains("dropping") || text.contains("boarding pt") || text.contains("boarding point") ||
                text.contains("pickup pt") || text.contains("pickup point") || text.contains("bus no") ||
                Regex("""\bdep(?:arture)?\s*[:#-]?\s*\d{1,2}""", RegexOption.IGNORE_CASE).containsMatchIn(body)

        if (hasBusKeyword && hasBusTravelDetails && (hasSeat || hasBookingConfirmation || text.contains("ticket") || text.contains("pnr"))) {
            return true
        }

        // 5. Train Ticket Confirmation:
        val hasTrainKeyword = text.contains("train") || text.contains("irctc") || text.contains("confirmtkt") || text.contains("railyatri")
        val hasTrainDetails = text.contains("coach") || text.contains("berth") || text.contains("sleeper") ||
                text.contains("tatkal") || text.contains("rac") || text.contains("cnf") || text.contains("quota")
        if (hasTrainKeyword && (hasTrainDetails || hasBookingConfirmation)) {
            return true
        }

        // 6. Flight Ticket Confirmation:
        val hasFlightKeyword = text.contains("flight") || text.contains("airline") || text.contains("indigo") ||
                text.contains("air india") || text.contains("spicejet") || text.contains("vistara") || text.contains("akasa")
        val hasFlightDetails = text.contains("terminal") || text.contains("gate") || text.contains("web check-in") ||
                text.contains("boarding pass") || text.contains("flight no")
        if (hasFlightKeyword && (hasFlightDetails || hasBookingConfirmation)) {
            return true
        }

        // 7. General booking confirmation with seats / ticket
        if ((text.contains("ticket confirmed") || text.contains("tickets confirmed") ||
             text.contains("booking confirmed") || text.contains("reservation confirmed") ||
             text.contains("ticket booked") || text.contains("tickets booked")) &&
            (hasSeat || text.contains("seat") || text.contains("bus") || text.contains("movie") || text.contains("flight") || text.contains("train") || text.contains("hotel"))) {
            return true
        }

        return false
    }

    fun matchesKeyword(text: String?, keyword: String): Boolean {
        if (text.isNullOrBlank()) return false
        val clean = keyword.trim()
        if (clean.isEmpty()) return false

        // Special handling for keyword "sim" (case-insensitive)
        if (clean.equals("sim", ignoreCase = true)) {
            val simWordPattern = Regex("""(?i)\b(?!simpl|similar|simulat|simultan)\w*sim\w*\b""")
            if (simWordPattern.containsMatchIn(text)) return true
            val simBoundaryPattern = Regex("""(?i)(?:^|[\W_])(?:e|4g|5g)?-?sim(?:-?(?:card|swap|slot|\d+))?(?:$|[\W_])""")
            if (simBoundaryPattern.containsMatchIn(text)) return true
            return false
        }

        // For phrases containing spaces or hyphens: case-insensitive substring match
        if (clean.contains(" ") || clean.contains("-")) {
            return text.contains(clean, ignoreCase = true)
        }

        // For single words:
        // Use strict word boundary matching so "window" doesn't match "win", "between" doesn't match "bet", etc.
        val pattern = """(?i)\b${Regex.escape(clean)}\b"""
        return try {
            if (Regex(pattern).containsMatchIn(text)) {
                true
            } else if (clean.length >= 5) {
                text.contains(clean, ignoreCase = true)
            } else {
                false
            }
        } catch (e: Exception) {
            text.contains(clean, ignoreCase = true)
        }
    }

    fun matchesCategory(address: String, body: String, contactName: String?, keyword: String): Boolean {
        val clean = keyword.trim()
        if (clean.isEmpty()) return false
        // 1. Check address
        if ((clean.length >= 4 && address.contains(clean, ignoreCase = true)) || matchesKeyword(address, clean)) return true
        // 2. Check contactName if present
        if (!contactName.isNullOrBlank() && ((clean.length >= 4 && contactName.contains(clean, ignoreCase = true)) || matchesKeyword(contactName, clean))) return true
        // 3. Check body with smart matching (case-insensitive)
        if (matchesKeyword(body, clean)) return true
        return false
    }

    fun classify(
        address: String,
        body: String,
        contactName: String? = null,
        customKeywords: Map<String, List<String>>? = null,
        isExplicitlyNotSpam: Boolean = false
    ): SmsCategory {
        val addressUpper = address.uppercase()
        val text = body.lowercase()

        val activeKeywords = customKeywords ?: com.ripple.chat.data.CategoryKeywordManager.DEFAULT_KEYWORDS
        val defaultMap = com.ripple.chat.data.CategoryKeywordManager.DEFAULT_KEYWORDS

        val categoryOrder = listOf(
            "OTP" to SmsCategory.OTP,
            "TICKETS" to SmsCategory.TICKETS,
            "FINANCE" to SmsCategory.FINANCE,
            "SHOPPING" to SmsCategory.SHOPPING,
            "REMINDERS" to SmsCategory.REMINDERS,
            "SUBSCRIPTIONS" to SmsCategory.SUBSCRIPTIONS,
            "IMPORTANT" to SmsCategory.IMPORTANT,
            "SPAM" to SmsCategory.SPAM,
            "PROMOTIONS" to SmsCategory.PROMOTIONAL
        )

        // ── Priority 0: Explicit user-added trigger keywords (Instant Override) ──
        for ((catName, catEnum) in categoryOrder) {
            if (isExplicitlyNotSpam && catEnum == SmsCategory.SPAM) continue
            val keywords = activeKeywords[catName].orEmpty()
            val defaults = defaultMap[catName].orEmpty()
            val userAdded = keywords.filter { kw -> !defaults.any { it.equals(kw, ignoreCase = true) } }
            if (userAdded.any { kw -> matchesCategory(address, body, contactName, kw) }) {
                return catEnum
            }
        }

        // ── Priority 1: Spam / Gibberish detection ──
        if (!isExplicitlyNotSpam && isMeaninglessOrGibberish(body)) {
            return SmsCategory.SPAM
        }

        // Check if sender is a random number without any saved contact name
        val digitsOnly = address.filter { it.isDigit() }
        val isRandomPhoneNumber = digitsOnly.length in 7..15 && contactName.isNullOrBlank()

        // ── Priority 1.1: Obvious OTP messages ──
        val hasOtpPattern = Regex("""\b\d{4,8}\b""").containsMatchIn(body)
        val hasOtpPhrase = text.contains("is your otp") || text.contains("verification code") ||
                text.contains("login code") || text.contains("security code") ||
                text.contains("do not share") || text.contains("use this code") ||
                text.contains("auth code") || text.contains("one time password") ||
                (hasOtpPattern && text.contains("otp") && (text.contains("valid for") || text.contains("expire") || text.contains("secret code")))
        if ((hasOtpPattern && hasOtpPhrase) || OtpParser.parseOtp(body, System.currentTimeMillis()) != null) {
            return SmsCategory.OTP
        }

        // ── Priority 1.2: Obvious Ticket Confirmations ──
        if (isHighConfidenceTicket(address, body, contactName)) {
            return SmsCategory.TICKETS
        }

        // ── Priority 1.3: Obvious Financial Transactions (without OTP) ──
        val hasRsAmount = Regex("""(?:rs\.?|inr)\s?[\d,]+""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val financeKeywords = activeKeywords["FINANCE"] ?: defaultMap["FINANCE"].orEmpty()
        val hasTransactionVerb = financeKeywords.any { matchesKeyword(text, it) }
        val hasBankKeyword = text.contains("a/c") || text.contains("acct") ||
                             text.contains("bank") || text.contains("upi") ||
                             text.contains("imps") || text.contains("neft") ||
                             text.contains("rtgs") || text.contains("balance") ||
                             text.contains("avl bal")
        if (hasRsAmount && (hasTransactionVerb || hasBankKeyword)) {
            return SmsCategory.FINANCE
        }
        if (hasTransactionVerb && hasBankKeyword) {
            return SmsCategory.FINANCE
        }

        // ── Priority 1.4: Obvious Scams (lottery, casino, 1xbet, earn money, work from home, double your money) ──
        val hasBlatantScamWords = text.contains("lottery") || text.contains("lucky draw") ||
                text.contains("casino") || text.contains("1xbet") || text.contains("bet365") ||
                text.contains("earn money") || text.contains("work from home") ||
                text.contains("double your money") || text.contains("cash prize") ||
                text.contains("rummy")
        val isKnownBrandSender = TICKET_BRANDS.any { addressUpper.contains(it) } ||
                listOf("AMAZON", "FLIPKART", "SWIGGY", "ZOMATO").any { addressUpper.contains(it) }
        if (!isExplicitlyNotSpam && hasBlatantScamWords && !isKnownBrandSender) {
            return SmsCategory.SPAM
        }

        val hasPromoSignals = text.contains("discount") || text.contains("promocode") ||
                text.contains("coupon") || text.contains("special offer") ||
                text.contains("cashback") || Regex("""\d+%\s*(?:off|discount)""", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
                (text.contains("flat ") && (text.contains("off") || text.contains("discount")))

        var otpScore = 0
        var shoppingScore = 0
        var importantScore = 0
        var spamScore = 0
        var promotionScore = 0
        var subscriptionScore = 0
        var ticketsScore = 0
        var financeScore = 0

        // 1. OTP
        val otpKeywords = activeKeywords["OTP"] ?: defaultMap["OTP"].orEmpty()
        for (kw in otpKeywords) {
            if (matchesKeyword(text, kw)) otpScore += 10
        }
        otpScore = otpScore.addIfRegexMatches(text, "\\b\\d{4,8}\\b", 4)

        // 2. Shopping
        val shoppingKeywords = activeKeywords["SHOPPING"] ?: defaultMap["SHOPPING"].orEmpty()
        for (kw in shoppingKeywords) {
            if (matchesKeyword(text, kw)) shoppingScore += 8
        }
        val shoppingBrands = listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "swiggy", "zomato", "blinkit", "zepto", "bigbasket", "nykaa", "ekart", "trends", "bewakoof", "peeko", "yousta")
        for (brand in shoppingBrands) {
            if (addressUpper.contains(brand.uppercase())) {
                shoppingScore += 15
            }
        }

        // 3. Important
        val importantKeywords = activeKeywords["IMPORTANT"] ?: defaultMap["IMPORTANT"].orEmpty()
        for (kw in importantKeywords) {
            if (matchesKeyword(text, kw)) importantScore += 10
        }
        importantScore = importantScore.addIfRegexMatches(text, "\\b[6-9]\\d{9}\\b", 5)

        // 4. Tickets
        val ticketsKeywords = activeKeywords["TICKETS"] ?: defaultMap["TICKETS"].orEmpty()
        for (kw in ticketsKeywords) {
            if (matchesKeyword(text, kw)) ticketsScore += 8
        }
        // Only grant ticket brand boost if not a pure promotional offer
        if (!hasPromoSignals) {
            for (brand in TICKET_BRANDS) {
                if (addressUpper.contains(brand) || contactName?.uppercase()?.contains(brand) == true) {
                    ticketsScore += 15
                }
            }
        }
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\bpnr\b""", 12)
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\bbooking\s*(?:id|ref|no)\b""", 12)
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\b(?:audi|screen)\s*[-#]?\s*\d+\b""", 12)
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\b(?:m-ticket|e-ticket|boarding\s*pass)\b""", 12)
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\b(?:seat|seats|berth)\s*[:#-]?\s*[A-Z0-9]""", 10)
        ticketsScore = ticketsScore.addIfRegexMatches(text, """\b(?:boarding|departure|arrival|showtime|show\s+time)\b""", 8)

        // 5. Spam
        if (!isExplicitlyNotSpam) {
            val spamKeywords = activeKeywords["SPAM"] ?: defaultMap["SPAM"].orEmpty()
            for (kw in spamKeywords) {
                if (matchesKeyword(text, kw)) spamScore += 8
            }
            if (hasBlatantScamWords) {
                spamScore += 25
            }
            // Shortened URLs often used in spam, BUT also in bus tracking & m-tickets!
            // Only penalize URL if ticketsScore is low and not a ticket message
            if (ticketsScore < 10) {
                spamScore = spamScore.addIfRegexMatches(text, "bit\\.ly|tinyurl\\.com|t\\.co", 15)
            } else {
                spamScore = 0 // Legitimate ticket confirmation with tracking/ticket link
            }
        } else {
            spamScore = 0
        }

        // 6. Promotion
        val promotionKeywords = activeKeywords["PROMOTIONS"] ?: defaultMap["PROMOTIONS"].orEmpty()
        for (kw in promotionKeywords) {
            if (matchesKeyword(text, kw)) promotionScore += 8
        }
        promotionScore = promotionScore.addIfRegexMatches(text, "\\d+%", 8)
        if (addressUpper.startsWith("AD-") || addressUpper.startsWith("VK-") || addressUpper.startsWith("DM-")) {
            promotionScore += 5
        }
        if (hasPromoSignals) {
            promotionScore += 15
        }

        // 7. Subscriptions
        val subscriptionKeywords = activeKeywords["SUBSCRIPTIONS"] ?: defaultMap["SUBSCRIPTIONS"].orEmpty()
        for (kw in subscriptionKeywords) {
            if (matchesKeyword(text, kw)) subscriptionScore += 10
        }

        // 8. Finance
        for (kw in financeKeywords) {
            if (matchesKeyword(text, kw)) financeScore += 10
        }
        financeScore = financeScore.addIfRegexMatches(text, "rs\\.?\\s?[\\d,]+", 15)
        financeScore = financeScore.addIfRegexMatches(text, "inr\\s?[\\d,]+", 15)
        val financeBrands = listOf("HDFCBK", "ICICI", "SBINB", "KOTAK", "AXISBK", "PNBBK", "BOIBK", "CANBNK", "UNIONB", "IDFCFB", "YESBNK", "INDUSB", "FEDBK", "RBLBNK", "BARODBK", "PAYTM", "PHONPE", "GPAY", "CRED")
        for (brand in financeBrands) {
            if (addressUpper.contains(brand)) {
                financeScore += 15
            }
        }

        // 9. Reminders
        var remindersScore = 0
        val remindersKeywords = activeKeywords["REMINDERS"] ?: defaultMap["REMINDERS"].orEmpty()
        for (kw in remindersKeywords) {
            if (matchesKeyword(text, kw)) remindersScore += 8
        }

        // Priority boosts: only boost when at least one matching signal exists
        if (spamScore > 0) {
            spamScore += 7
            if (isRandomPhoneNumber) spamScore += 6
        }
        if (otpScore > 0) otpScore += 6
        if (ticketsScore > 0) ticketsScore += 8
        if (financeScore > 0) financeScore += 5
        if (shoppingScore > 0) shoppingScore += 4
        if (subscriptionScore > 0) subscriptionScore += 3
        if (remindersScore > 0) remindersScore += 3
        if (promotionScore > 0) promotionScore += 2
        if (importantScore > 0) importantScore += 1

        val threshold = 9
        val scores = if (isExplicitlyNotSpam) {
            mapOf(
                SmsCategory.OTP to otpScore,
                SmsCategory.TICKETS to ticketsScore,
                SmsCategory.FINANCE to financeScore,
                SmsCategory.SHOPPING to shoppingScore,
                SmsCategory.SUBSCRIPTIONS to subscriptionScore,
                SmsCategory.REMINDERS to remindersScore,
                SmsCategory.PROMOTIONAL to promotionScore,
                SmsCategory.IMPORTANT to importantScore
            )
        } else {
            mapOf(
                SmsCategory.SPAM to spamScore,
                SmsCategory.OTP to otpScore,
                SmsCategory.TICKETS to ticketsScore,
                SmsCategory.FINANCE to financeScore,
                SmsCategory.SHOPPING to shoppingScore,
                SmsCategory.SUBSCRIPTIONS to subscriptionScore,
                SmsCategory.REMINDERS to remindersScore,
                SmsCategory.PROMOTIONAL to promotionScore,
                SmsCategory.IMPORTANT to importantScore
            )
        }

        val maxCategory = scores.maxByOrNull { it.value }
        return if (maxCategory != null && maxCategory.value >= threshold) {
            maxCategory.key
        } else if (!contactName.isNullOrBlank() && contactName != address) {
            SmsCategory.PERSONAL
        } else if (isExplicitlyNotSpam) {
            SmsCategory.PERSONAL
        } else {
            SmsCategory.OTHERS
        }
    }
}
