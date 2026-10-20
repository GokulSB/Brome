package com.ripple.chat.data.finance

import androidx.compose.runtime.Immutable


enum class TransactionDirection {
    CREDIT, DEBIT, UNKNOWN
}

@Immutable
data class FinanceTransaction(
    val id: String,
    val senderId: String,
    val accountLabel: String,
    val accountRef: String,
    val amount: Double,
    val direction: TransactionDirection,
    val category: String,
    val timestamp: Long,
    val smsBody: String,
    val avatarInitial: Char
)

@Immutable
data class CategoryTotal(
    val category: String,
    val totalAmount: Double,
    val count: Int
)

@Immutable
data class FinanceMonthSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val creditCount: Int,
    val debitCount: Int,
    val accountCount: Int,
    val categoryTotals: List<CategoryTotal>
)
