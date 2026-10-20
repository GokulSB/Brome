package com.ripple.chat.ui.finance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ripple.chat.data.SmsRepository
import com.ripple.chat.data.DataRepository
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.data.finance.CategoryTotal
import com.ripple.chat.data.finance.FinanceMonthSummary
import com.ripple.chat.data.finance.FinanceParser
import com.ripple.chat.data.finance.FinanceTransaction
import com.ripple.chat.data.finance.TransactionDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FinanceLedgerViewModel(private val context: Context) : ViewModel() {

    private val repository = SmsRepository(context)

    private val _transactions = MutableStateFlow<List<FinanceTransaction>>(emptyList())
    
    private val _filteredTransactions = MutableStateFlow<Map<String, List<FinanceTransaction>>>(emptyMap())
    val filteredTransactions: StateFlow<Map<String, List<FinanceTransaction>>> = _filteredTransactions.asStateFlow()

    private val _summary = MutableStateFlow<FinanceMonthSummary?>(null)
    val summary: StateFlow<FinanceMonthSummary?> = _summary.asStateFlow()

    private val _selectedMonth = MutableStateFlow<String>("")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()
    
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()
    
    private val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

    val availableMonths = MutableStateFlow<List<String>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val threads = repository.getConversations()
                    val financeThreads = threads.filter { it.category == SmsCategory.FINANCE }
                    financeThreads.map { FinanceParser.parse(it) }
                        .filter { it.amount > 0 }
                        .sortedByDescending { it.timestamp }
                }
                
                _transactions.value = parsed
                
                // Extract available months
                val months = parsed.map { 
                    monthFormatter.format(Instant.ofEpochMilli(it.timestamp)) 
                }.distinct()
                
                availableMonths.value = months
                if (_selectedMonth.value.isEmpty() && months.isNotEmpty()) {
                    _selectedMonth.value = months.first()
                }
                
                updateFilteredData()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
        _selectedCategory.value = null // reset category filter
        updateFilteredData()
    }
    
    fun toggleCategory(category: String) {
        if (_selectedCategory.value == category) {
            _selectedCategory.value = null
        } else {
            _selectedCategory.value = category
        }
        updateFilteredData()
    }

    private fun updateFilteredData() {
        val allTx = _transactions.value
        val currentMonth = _selectedMonth.value
        
        val monthTx = allTx.filter { 
            monthFormatter.format(Instant.ofEpochMilli(it.timestamp)) == currentMonth 
        }
        
        // Calculate summary for the month
        var income = 0.0
        var expense = 0.0
        var creditCount = 0
        var debitCount = 0
        val categoryMap = mutableMapOf<String, Pair<Double, Int>>()
        val accounts = mutableSetOf<String>()

        for (tx in monthTx) {
            if (tx.direction == TransactionDirection.CREDIT) {
                income += tx.amount
                creditCount++
            } else if (tx.direction == TransactionDirection.DEBIT) {
                expense += tx.amount
                debitCount++
            }
            
            if (tx.accountRef.isNotEmpty()) {
                accounts.add(tx.accountRef)
            }
            
            val current = categoryMap[tx.category] ?: Pair(0.0, 0)
            categoryMap[tx.category] = Pair(current.first + tx.amount, current.second + 1)
        }

        val categoryTotals = categoryMap.map { 
            CategoryTotal(it.key, it.value.first, it.value.second) 
        }.sortedByDescending { it.totalAmount }

        _summary.value = FinanceMonthSummary(
            totalIncome = income,
            totalExpense = expense,
            creditCount = creditCount,
            debitCount = debitCount,
            accountCount = accounts.size,
            categoryTotals = categoryTotals
        )
        
        // Filter by category if selected
        val categoryFilter = _selectedCategory.value
        val finalTx = if (categoryFilter != null) {
            monthTx.filter { it.category == categoryFilter }
        } else {
            monthTx
        }
        
        // Group by day
        val grouped = finalTx.groupBy { tx ->
            val date = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            val today = java.time.LocalDate.now()
            val yesterday = today.minusDays(1)
            
            when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> dayFormatter.format(Instant.ofEpochMilli(tx.timestamp)).uppercase()
            }
        }
        
        _filteredTransactions.value = grouped
    }
}
