package com.ripple.chat.data.finance

import com.ripple.chat.data.ChatThread
import java.util.regex.Pattern

object FinanceParser {

    private val amountRegexes = listOf(
        Regex("""(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""(?i)(?:debited|credited|paid|received)(?:\s+by|\s+for)?\s*(?:rs\.?|inr|₹)?\s*([\d,]+(?:\.\d{1,2})?)""")
    )

    private val accountRegexes = listOf(
        Regex("""(?i)(?:a/c|acct|account)\s*(?:no\.?)?\s*([x\d\*]+)"""),
        Regex("""(?i)card\s*([x\d\*]{4,})"""),
        Regex("""(?i)(?:ending with|ending in)\s*([x\d\*]{4,})""")
    )

    fun parse(thread: ChatThread): FinanceTransaction {
        val bodyLower = thread.body.lowercase()
        val addressUpper = thread.address.uppercase()

        // Direction
        var direction = when {
            bodyLower.contains("debited") || bodyLower.contains("paid") || bodyLower.contains("sent") || bodyLower.contains("emi") -> TransactionDirection.DEBIT
            bodyLower.contains("credited") || bodyLower.contains("received") || bodyLower.contains("refund") -> TransactionDirection.CREDIT
            else -> TransactionDirection.UNKNOWN
        }
        
        // Ignore payment received on credit card as income
        if (bodyLower.contains("credit card") && (bodyLower.contains("payment received") || bodyLower.contains("received")) && !bodyLower.contains("refund")) {
            direction = TransactionDirection.UNKNOWN
        }

        // Amount
        var amountStr: String? = null
        for (regex in amountRegexes) {
            val match = regex.find(thread.body)
            if (match != null) {
                amountStr = match.groupValues.getOrNull(1)?.replace(",", "")
                break
            }
        }
        
        if (amountStr == null && bodyLower.contains("rs")) {
            // fallback generic
            val fallbackMatch = Regex("""(?i)rs\.?\s*([\d,]+(?:\.\d{1,2})?)""").find(thread.body)
            amountStr = fallbackMatch?.groupValues?.getOrNull(1)?.replace(",", "")
        }

        val amount = amountStr?.toDoubleOrNull() ?: 0.0

        // Account ref
        var accountRef = ""
        for (regex in accountRegexes) {
            val match = regex.find(thread.body)
            if (match != null) {
                accountRef = match.groupValues.getOrNull(1) ?: ""
                break
            }
        }

        // Category
        val category = when {
            bodyLower.contains("upi") || bodyLower.contains("vpa") -> "UPI"
            bodyLower.contains("atm") -> "ATM"
            bodyLower.contains("card") -> "Card"
            bodyLower.contains("salary") -> "Salary"
            bodyLower.contains("refund") -> "Refund"
            bodyLower.contains("bill") || bodyLower.contains("recharge") -> "Bills"
            direction == TransactionDirection.CREDIT -> "Income"
            direction == TransactionDirection.DEBIT -> "Transfer"
            else -> "Bank xfer"
        }

        // Account label (e.g., Kotak Bank)
        val bankName = extractBankName(addressUpper)
        val accountLabel = if (accountRef.isNotEmpty()) {
            if (bankName.isNotEmpty()) "$bankName · $accountRef" else accountRef
        } else {
            bankName.ifEmpty { "Account" }
        }

        val avatarChar = if (bankName.isNotEmpty()) bankName.first() else (addressUpper.firstOrNull { it.isLetter() } ?: '?')

        return FinanceTransaction(
            id = thread.threadId.toString(),
            senderId = thread.address,
            accountLabel = accountLabel,
            accountRef = accountRef,
            amount = amount,
            direction = direction,
            category = category,
            timestamp = thread.date,
            smsBody = thread.body,
            avatarInitial = avatarChar
        )
    }

    private fun extractBankName(address: String): String {
        return when {
            address.contains("HDFCBK") || address.contains("HDFC") -> "HDFC Bank"
            address.contains("ICICI") -> "ICICI Bank"
            address.contains("SBINB") || address.contains("SBI") -> "SBI"
            address.contains("KOTAK") -> "Kotak Bank"
            address.contains("AXIS") -> "Axis Bank"
            address.contains("PNB") -> "PNB"
            else -> ""
        }
    }
}
