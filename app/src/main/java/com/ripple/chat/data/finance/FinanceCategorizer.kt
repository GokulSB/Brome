package com.ripple.chat.data.finance

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ExpenseCategory(val label: String) {
    BILLS("Bills"),
    FOOD("Food"),
    SHOPPING("Shopping"),
    TRANSPORT("Transport"),
    SUBSCRIPTIONS("Subscriptions"),
    OTHER("Other")
}

enum class IncomeCategory(val label: String) {
    SALARY("Salary"),
    TRANSFERS("Transfers"),
    REFUNDS("Refunds"),
    INTEREST("Interest")
}

object FinanceCategorizer {

    private const val PREFS_NAME = "finance_category_overrides"

    fun getExpenseCategory(sender: String, body: String, context: Context): ExpenseCategory {
        val override = getOverride(sender, context)
        if (override != null) {
            try {
                return ExpenseCategory.valueOf(override)
            } catch (e: Exception) { /* continue */ }
        }

        val text = "$sender $body".lowercase()
        return when {
            text.containsAny("swiggy", "zomato", "restaurant", "food", "cafe", "dining", "mcdonalds", "starbucks", "dominos", "kfc", "eats") -> ExpenseCategory.FOOD
            text.containsAny("uber", "ola", "rapido", "irctc", "metro", "fuel", "petrol", "diesel", "parking", "toll", "cab", "ride") -> ExpenseCategory.TRANSPORT
            text.containsAny("amazon", "flipkart", "myntra", "ajio", "retail", "store", "fashion", "mall", "purchase", "mart", "supermarket") -> ExpenseCategory.SHOPPING
            text.containsAny("netflix", "spotify", "prime", "youtube", "apple", "hotstar", "google", "subscription", "membership") -> ExpenseCategory.SUBSCRIPTIONS
            text.containsAny("bill", "utility", "electricity", "water", "gas", "recharge", "mobile", "broadband", "dth", "bescom", "airtel", "jio") -> ExpenseCategory.BILLS
            else -> ExpenseCategory.OTHER
        }
    }

    fun getIncomeCategory(sender: String, body: String, context: Context): IncomeCategory {
        val override = getOverride(sender, context)
        if (override != null) {
            try {
                return IncomeCategory.valueOf(override)
            } catch (e: Exception) { /* continue */ }
        }

        val text = "$sender $body".lowercase()
        return when {
            text.containsAny("salary", "payroll", "stipend", "wages") -> IncomeCategory.SALARY
            text.containsAny("refund", "cashback", "reversed", "reversal") -> IncomeCategory.REFUNDS
            text.containsAny("interest", "dividend", "yield") -> IncomeCategory.INTEREST
            else -> IncomeCategory.TRANSFERS
        }
    }

    fun setOverride(merchant: String, categoryName: String, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(merchant.lowercase().trim(), categoryName).apply()
    }

    private fun getOverride(merchant: String, context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(merchant.lowercase().trim(), null)
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }

    // Chart Colors (Dark and Light sets)
    fun getChartColor(categoryLabel: String, isDark: Boolean): Color {
        return when (categoryLabel) {
            "Bills" -> if (isDark) Color(0xFF6FE0D0) else Color(0xFF1F7A3E)
            "Food" -> if (isDark) Color(0xFFFF8F6B) else Color(0xFFB4510F)
            "Shopping" -> if (isDark) Color(0xFFB8A9FF) else Color(0xFF6B3FD4)
            "Transport" -> if (isDark) Color(0xFFFCD36A) else Color(0xFF8A6D00)
            "Subscriptions" -> if (isDark) Color(0xFF8FD0FF) else Color(0xFF1565C0)
            "Salary" -> if (isDark) Color(0xFF6FE0D0) else Color(0xFF1F7A3E)
            "Transfers" -> if (isDark) Color(0xFFB8A9FF) else Color(0xFF6B3FD4)
            "Refunds" -> if (isDark) Color(0xFFFF8F6B) else Color(0xFFB4510F)
            "Interest" -> if (isDark) Color(0xFFFCD36A) else Color(0xFF8A6D00)
            else -> if (isDark) Color(0xFF9BAA9F) else Color(0xFF5F6B63)
        }
    }
}
