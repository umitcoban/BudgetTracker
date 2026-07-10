package com.umit.budgettracker.feature.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.feature.dashboard.MonthSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raporlar") },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is ReportsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ReportsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(text = "Özet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        ReportSummaryCard(state.currentMonth)
                    }

                    item {
                        Text(text = "Son 6 Ay Trendi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        SixMonthTrendCard(state.trend)
                    }

                    item {
                        Text(text = "Harcama Kanalları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        SpendingChannelsCard(state.currentMonth)
                    }

                    if (state.currentMonth.categorySummaries.isNotEmpty()) {
                        item {
                            Text(
                                text = "Kategori Dağılımı",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(state.currentMonth.categorySummaries) { category ->
                            CategoryReportRow(category, state.currentMonth.totalExpenseAmount)
                        }
                    }

                    item {
                        Text(text = "Karşılaştırma (Önceki Ay)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonCard(state.currentMonth, state.previousMonth)
                    }

                    item {
                        Text(text = "Karar Özeti", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        DecisionSummaryCard(state.currentMonth, state.previousMonth)
                    }

                    if (state.netWorth != null) {
                        item {
                            Text(text = "Net Varlık", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            NetWorthCard(state.netWorth.netWorth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SixMonthTrendCard(trend: List<MonthlyTrendPoint>) {
    val maximumAmount = trend.maxOfOrNull { maxOf(it.incomeAmount, it.expenseAmount) } ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            trend.forEach { point ->
                val incomeProgress = if (maximumAmount > 0L) point.incomeAmount.toFloat() / maximumAmount else 0f
                val expenseProgress = if (maximumAmount > 0L) point.expenseAmount.toFloat() / maximumAmount else 0f
                Text(DateUtils.formatMonthYear(point.month), fontWeight = FontWeight.Bold)
                TrendBar("Gelir", point.incomeAmount, incomeProgress, MaterialTheme.colorScheme.primary)
                TrendBar("Harcama", point.expenseAmount, expenseProgress, MaterialTheme.colorScheme.error)
                ReportRow(
                    "Plan sonrası kalan",
                    MoneyFormatter.format(point.remainingAmount),
                    color = if (point.remainingAmount < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TrendBar(label: String, amount: Long, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color
        )
    }
}

@Composable
fun ReportSummaryCard(summary: MonthlyBudgetSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            ReportRow("Maaş", MoneyFormatter.format(summary.salaryAmount))
            ReportRow("Ek Gelirler", MoneyFormatter.format(summary.additionalIncomeAmount))
            ReportRow("Toplam Gelir", MoneyFormatter.format(summary.totalIncomeAmount), fontWeight = FontWeight.Bold)
            ReportRow("Tasarruf Hedefi", MoneyFormatter.format(summary.savingGoalAmount))
            ReportRow("Toplam Harcama", MoneyFormatter.format(summary.totalExpenseAmount), Color.Red)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ReportRow("Abonelikler Toplamı", MoneyFormatter.format(summary.subscriptionPlannedAmount))
            ReportRow("Harcamalara İşlenen Abonelikler", MoneyFormatter.format(summary.subscriptionPaidAmount))
            ReportRow("Planlanan / Henüz İşlenmeyen Abonelikler", MoneyFormatter.format(summary.subscriptionUnpaidPlannedAmount))
            ReportRow("Kredi Ödemeleri", MoneyFormatter.format(summary.loanPaymentAmount))
            ReportRow("Sabit Giderler", MoneyFormatter.format(summary.fixedExpenseAmount))
            ReportRow("Toplam Planlı Sabit Ödeme", MoneyFormatter.format(summary.projectedFixedPaymentsAmount), fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ReportRow("Birikim Önerisi", MoneyFormatter.format(summary.suggestedSavingAmount), fontWeight = FontWeight.Bold)
            ReportRow("Kalan", MoneyFormatter.format(summary.remainingAfterSaving), fontWeight = FontWeight.Bold)
            ReportRow("Sabit Ödemeler Sonrası Kalan", MoneyFormatter.format(summary.remainingAfterFixedPayments), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ComparisonCard(current: MonthlyBudgetSummary, previous: MonthlyBudgetSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val diff = current.totalExpenseAmount - previous.totalExpenseAmount
            val diffText = if (diff > 0) "+${MoneyFormatter.format(diff)}" else MoneyFormatter.format(diff)
            val color = if (diff > 0) Color.Red else Color(0xFF4CAF50)
            
            ReportRow("Harcama Değişimi", diffText, color, FontWeight.Bold)
            Text(
                text = if (diff > 0) "Geçen aya göre daha fazla harcadınız." else "Geçen aya göre daha az harcadınız.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DecisionSummaryCard(
    current: MonthlyBudgetSummary,
    previous: MonthlyBudgetSummary
) {
    val currentCategories = current.categorySummaries.associateBy { it.categoryId }
    val topIncrease = currentCategories.values
        .map { category ->
            val previousAmount = previous.categorySummaries.firstOrNull { it.categoryId == category.categoryId }?.amount ?: 0L
            category to (category.amount - previousAmount)
        }
        .filter { it.second > 0L }
        .maxByOrNull { it.second }
    val fixedPaymentDiff = current.projectedFixedPaymentsAmount - previous.projectedFixedPaymentsAmount
    val savingGap = current.savingGoalAmount - current.suggestedSavingAmount
    val cashWarning = current.remainingAfterSavingAndFixedPayments < 0L

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (topIncrease != null) {
                ReportRow("En Çok Artan Kategori", "${topIncrease.first.categoryName} +${MoneyFormatter.format(topIncrease.second)}", Color.Red, FontWeight.Bold)
            } else {
                Text("Kategori artışı dikkat çekmiyor.", style = MaterialTheme.typography.bodySmall)
            }
            val diffText = if (fixedPaymentDiff > 0) "+${MoneyFormatter.format(fixedPaymentDiff)}" else MoneyFormatter.format(fixedPaymentDiff)
            ReportRow("Planlı Ödeme Farkı", diffText, if (fixedPaymentDiff > 0) Color.Red else Color(0xFF4CAF50), FontWeight.Bold)
            ReportRow(
                "Birikim Hedefi Sapması",
                if (savingGap > 0L) "${MoneyFormatter.format(savingGap)} hedef öneriden yüksek" else "Hedef öneriyle uyumlu",
                if (savingGap > 0L) Color.Red else Color(0xFF4CAF50)
            )
            Text(
                text = if (cashWarning) {
                    "Kalan nakit negatif. Bu ay hedef, sabit ödemeler veya harcama planı gözden geçirilmeli."
                } else {
                    "Kalan nakit pozitif. Bu ay plan sürdürülebilir görünüyor."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (cashWarning) Color.Red else Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun NetWorthCard(amount: Long) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = MoneyFormatter.format(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpendingChannelsCard(summary: MonthlyBudgetSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportRow("Nakit / banka harcamaları", MoneyFormatter.format(summary.directExpenseAmount))
            ReportRow("Kredi kartı ekstre harcamaları", MoneyFormatter.format(summary.creditCardPaymentAmount))
            HorizontalDivider()
            ReportRow(
                "Toplam harcama",
                MoneyFormatter.format(summary.totalExpenseAmount),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategoryReportRow(summary: CategorySummary, totalExpenseAmount: Long) {
    val share = if (totalExpenseAmount > 0L) {
        summary.amount.toFloat() / totalExpenseAmount
    } else {
        0f
    }
    val budgetProgress = summary.percentage
    val progress = budgetProgress ?: share
    val progressColor = when {
        budgetProgress != null && budgetProgress > 1f -> MaterialTheme.colorScheme.error
        budgetProgress != null && budgetProgress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> Color(summary.colorValue)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IconMapper.getIcon(summary.iconName),
                    contentDescription = null,
                    tint = Color(summary.colorValue)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.categoryName, fontWeight = FontWeight.Bold)
                    val detail = if (summary.budgetLimit != null) {
                        "Bütçe: ${MoneyFormatter.format(summary.amount)} / ${MoneyFormatter.format(summary.budgetLimit)}"
                    } else {
                        "Toplam harcamanın %${(share * 100).toInt()}\'i"
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(MoneyFormatter.format(summary.amount), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor
            )
        }
    }
}

@Composable
fun ReportRow(label: String, value: String, color: Color = Color.Unspecified, fontWeight: FontWeight = FontWeight.Normal) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = fontWeight)
    }
}
