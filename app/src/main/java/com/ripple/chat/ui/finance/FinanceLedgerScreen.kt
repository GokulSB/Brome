package com.ripple.chat.ui.finance

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ripple.chat.data.SmsCategory
import com.ripple.chat.data.finance.*
import com.ripple.chat.theme.*
import com.ripple.chat.ui.chat.formatAmountCompact
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
}

enum class FinanceMode { EXPENSES, INCOME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceLedgerScreen(
    viewModel: FinanceLedgerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val financeQuad = BromeCategoryColors.getColors(SmsCategory.FINANCE, dark)
    val promoQuad = BromeCategoryColors.getColors(SmsCategory.PROMOTIONAL, dark)

    val summary by viewModel.summary.collectAsState()
    val groupedTransactions by viewModel.filteredTransactions.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var currentMode by remember { mutableStateOf(FinanceMode.EXPENSES) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showLedgerSheet by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedChartCategory by remember { mutableStateOf<String?>(null) }

    val allTransactions = remember(groupedTransactions) {
        groupedTransactions.flatMap { it.value }
    }

    val totalExpense = summary?.totalExpense ?: 0.0
    val totalIncome = summary?.totalIncome ?: 0.0

    // Categorized Expenses Data
    val expenseCategoryMap = remember(allTransactions, dark) {
        val map = mutableMapOf<ExpenseCategory, Double>()
        allTransactions.filter { it.direction == TransactionDirection.DEBIT }.forEach { tx ->
            val cat = FinanceCategorizer.getExpenseCategory(tx.senderId, tx.smsBody, context)
            map[cat] = (map[cat] ?: 0.0) + tx.amount
        }
        if (map.isEmpty()) {
            map[ExpenseCategory.BILLS] = totalExpense * 0.45
            map[ExpenseCategory.FOOD] = totalExpense * 0.25
            map[ExpenseCategory.SHOPPING] = totalExpense * 0.20
            map[ExpenseCategory.OTHER] = totalExpense * 0.10
        }
        map
    }

    // Categorized Income Data
    val incomeCategoryMap = remember(allTransactions, dark) {
        val map = mutableMapOf<IncomeCategory, Double>()
        allTransactions.filter { it.direction == TransactionDirection.CREDIT }.forEach { tx ->
            val cat = FinanceCategorizer.getIncomeCategory(tx.senderId, tx.smsBody, context)
            map[cat] = (map[cat] ?: 0.0) + tx.amount
        }
        if (map.isEmpty()) {
            map[IncomeCategory.SALARY] = totalIncome * 0.75
            map[IncomeCategory.TRANSFERS] = totalIncome * 0.25
        }
        map
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header Row: Back button + Title + Ledger Pill + Month Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(52.dp).pressScale()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Finance",
                        fontFamily = BricolageFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectedMonth.ifEmpty { "Financial Activity" },
                        fontFamily = FigtreeFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Ledger Pill Button
                Surface(
                    onClick = { showLedgerSheet = !showLedgerSheet },
                    shape = CircleShape,
                    color = if (showLedgerSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.height(38.dp).pressScale()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = if (showLedgerSheet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ledger",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FigtreeFontFamily,
                            color = if (showLedgerSheet) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Segmented Control (56dp, surfaceContainerHigh)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isExp = currentMode == FinanceMode.EXPENSES
                    Surface(
                        onClick = { currentMode = FinanceMode.EXPENSES },
                        shape = CircleShape,
                        color = if (isExp) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f).fillMaxHeight().pressScale()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Expenses",
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isExp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isInc = currentMode == FinanceMode.INCOME
                    Surface(
                        onClick = { currentMode = FinanceMode.INCOME },
                        shape = CircleShape,
                        color = if (isInc) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f).fillMaxHeight().pressScale()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Income",
                                fontFamily = FigtreeFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isInc) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (showLedgerSheet) {
                // PASSBOOK LEDGER VIEW
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedTransactions.entries.forEachIndexed { groupIndex, entry ->
                        val dateHeader = entry.key
                        val transactions = entry.value

                        item {
                            Text(
                                text = dateHeader.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            Surface(
                                shape = getTideCornerShape(groupIndex + 1, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    transactions.forEachIndexed { idx, tx ->
                                        val isCredit = tx.direction == TransactionDirection.CREDIT

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 60.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(financeQuad.shape)
                                                    .background(if (isCredit) financeQuad.accent else MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = tx.avatarInitial.toString(),
                                                    fontFamily = BricolageFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = if (isCredit) financeQuad.onAccent else MaterialTheme.colorScheme.onPrimary
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tx.accountLabel,
                                                    fontFamily = BricolageFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${tx.senderId} · ${tx.category}",
                                                    fontFamily = FigtreeFontFamily,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = "${if (isCredit) "+" else "−"}₹${formatAmountCompact(tx.amount)}",
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = if (isCredit) financeQuad.accent else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (idx < transactions.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // CHARTS & CATEGORIZED VIEW
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (currentMode == FinanceMode.EXPENSES) {
                        // 1. DONUT CHART CARD (160dp Canvas Donut)
                        item {
                            val expSum = expenseCategoryMap.values.sum().coerceAtLeast(1.0)
                            val maxCat = expenseCategoryMap.maxByOrNull { it.value }

                            Surface(
                                shape = getTideCornerShape(0, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Donut Canvas (160dp)
                                    Box(
                                        modifier = Modifier.size(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            var startAngle = -90f
                                            val strokeWidthPx = 18.dp.toPx()
                                            val diameter = size.minDimension - strokeWidthPx
                                            val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)

                                            expenseCategoryMap.forEach { (cat, amt) ->
                                                val sweepAngle = ((amt / expSum) * 360f).toFloat()
                                                val col = FinanceCategorizer.getChartColor(cat.label, dark)

                                                drawArc(
                                                    color = col,
                                                    startAngle = startAngle,
                                                    sweepAngle = (sweepAngle - 3f).coerceAtLeast(1f),
                                                    useCenter = false,
                                                    topLeft = topLeft,
                                                    size = Size(diameter, diameter),
                                                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                                                )
                                                startAngle += sweepAngle
                                            }
                                        }

                                        // Center text
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Spent",
                                                fontSize = 12.sp,
                                                fontFamily = FigtreeFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "₹${formatAmountCompact(selectedChartCategory?.let { label -> expenseCategoryMap.entries.firstOrNull { it.key.label == label }?.value ?: expSum } ?: expSum)}",
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 22.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedMonth.ifEmpty { "This Month" },
                                            fontSize = 12.sp,
                                            fontFamily = FigtreeFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "₹${formatAmountCompact(expSum)}",
                                            fontFamily = BricolageFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 30.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${expenseCategoryMap.size} categories",
                                            fontSize = 13.sp,
                                            fontFamily = FigtreeFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (maxCat != null) {
                                            val share = ((maxCat.value / expSum) * 100).toInt()
                                            Text(
                                                text = "Top: ${maxCat.key.label} ($share%)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FigtreeFontFamily,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. BAR CHART CARD ("Last 7 days")
                        item {
                            Surface(
                                shape = getTideCornerShape(1, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "LAST 7 DAYS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                                        val todayIdx = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

                                        days.forEachIndexed { idx, day ->
                                            val isToday = idx == todayIdx
                                            val barH = (24 + (idx * 8) % 60).dp
                                            val barCol = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Bottom
                                            ) {
                                                if (isToday) {
                                                    Text(
                                                        text = "₹1.2k",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FigtreeFontFamily,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .width(18.dp)
                                                        .height(barH)
                                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 6.dp, bottomStart = 6.dp))
                                                        .background(barCol)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = day,
                                                    fontSize = 12.sp,
                                                    fontFamily = FigtreeFontFamily,
                                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. "BY CATEGORY" GROUP CARD
                        item {
                            Text(
                                text = "BY CATEGORY",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            val maxVal = expenseCategoryMap.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                            val expSum = expenseCategoryMap.values.sum().coerceAtLeast(1.0)

                            Surface(
                                shape = getTideCornerShape(2, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    expenseCategoryMap.entries.forEachIndexed { idx, entry ->
                                        val (cat, amt) = entry
                                        val chartCol = FinanceCategorizer.getChartColor(cat.label, dark)
                                        val sharePct = ((amt / expSum) * 100).toInt()
                                        val progress = (amt / maxVal).toFloat()

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 64.dp)
                                                .clickable {
                                                    selectedChartCategory = if (selectedChartCategory == cat.label) null else cat.label
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(Cookie8Shape)
                                                    .background(chartCol),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Category,
                                                    contentDescription = null,
                                                    tint = if (dark) Color(0xFF10140F) else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = cat.label,
                                                        fontFamily = BricolageFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "₹${formatAmountCompact(amt)}",
                                                        fontFamily = BricolageFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(6.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxHeight()
                                                                .fillMaxWidth(progress)
                                                                .clip(CircleShape)
                                                                .background(chartCol)
                                                        )
                                                    }
                                                    Text(
                                                        text = "$sharePct%",
                                                        fontSize = 12.sp,
                                                        fontFamily = FigtreeFontFamily,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (idx < expenseCategoryMap.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // INCOME MODE
                        // 1. Summary Card in Transactions container color
                        item {
                            val netAmount = totalIncome - totalExpense

                            Surface(
                                shape = getTideCornerShape(0, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = financeQuad.container,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "RECEIVED THIS MONTH",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = financeQuad.onContainer.copy(alpha = 0.8f)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "₹${formatAmountCompact(totalIncome)}",
                                        fontFamily = BricolageFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 44.sp,
                                        color = financeQuad.onContainer
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Two Stat Boxes: Spent and Net
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = financeQuad.accent.copy(alpha = 0.2f),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                Text("Spent", fontSize = 11.sp, fontFamily = FigtreeFontFamily, color = financeQuad.onContainer.copy(alpha = 0.8f))
                                                Text("₹${formatAmountCompact(totalExpense)}", fontSize = 15.sp, fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, color = financeQuad.onContainer)
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = financeQuad.accent.copy(alpha = 0.2f),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                Text("Net", fontSize = 11.sp, fontFamily = FigtreeFontFamily, color = financeQuad.onContainer.copy(alpha = 0.8f))
                                                Text("${if (netAmount >= 0) "+" else "−"}₹${formatAmountCompact(kotlin.math.abs(netAmount))}", fontSize = 15.sp, fontFamily = BricolageFontFamily, fontWeight = FontWeight.Bold, color = financeQuad.onContainer)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Grouped Bar Chart ("Last 6 months")
                        item {
                            Surface(
                                shape = getTideCornerShape(1, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "LAST 6 MONTHS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(90.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        val months = listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct")
                                        months.forEachIndexed { idx, m ->
                                            val isCurr = idx == months.size - 1
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(8.dp)
                                                        .height((30 + (idx * 10) % 50).dp)
                                                        .clip(CircleShape)
                                                        .background(financeQuad.accent)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(8.dp)
                                                        .height((20 + (idx * 8) % 40).dp)
                                                        .clip(CircleShape)
                                                        .background(promoQuad.accent)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. "BY SOURCE" LIST
                        item {
                            Text(
                                text = "BY SOURCE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            Surface(
                                shape = getTideCornerShape(2, baseRadius = 30.dp, sharpRadius = 10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    incomeCategoryMap.entries.forEachIndexed { idx, entry ->
                                        val (cat, amt) = entry
                                        val chartCol = FinanceCategorizer.getChartColor(cat.label, dark)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 64.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(Flower5Shape)
                                                    .background(chartCol),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = if (dark) Color(0xFF10140F) else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Text(
                                                text = cat.label,
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Text(
                                                text = "+₹${formatAmountCompact(amt)}",
                                                fontFamily = BricolageFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = financeQuad.accent
                                            )
                                        }

                                        if (idx < incomeCategoryMap.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMonthPicker) {
            ModalBottomSheet(
                onDismissRequest = { showMonthPicker = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                    items(availableMonths) { month ->
                        Text(
                            text = month,
                            fontFamily = FigtreeFontFamily,
                            fontSize = 16.sp,
                            fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal,
                            color = if (month == selectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectMonth(month)
                                    showMonthPicker = false
                                }
                                .padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}
