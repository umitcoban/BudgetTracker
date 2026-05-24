package com.umit.budgettracker.feature.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.util.MoneyFormatter
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
                        Text(text = "Karşılaştırma (Önceki Ay)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonCard(state.currentMonth, state.previousMonth)
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
fun ReportSummaryCard(summary: com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary) {
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
fun ComparisonCard(current: com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary, previous: com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary) {
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
fun NetWorthCard(amount: Long) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(text = MoneyFormatter.format(amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
