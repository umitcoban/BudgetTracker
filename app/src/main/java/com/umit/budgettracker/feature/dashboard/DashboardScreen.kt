package com.umit.budgettracker.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.BudgetWarning
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.DebtType
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.navigation.navigateToTopLevelDestination
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.MetricTile
import com.umit.budgettracker.core.ui.components.StatusPill
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showSavingGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Genel Bakış", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Finansal durumun tek ekranda",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.CashFlow.route) }) {
                        Icon(Icons.Default.Event, contentDescription = "Nakit Akışı")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            DashboardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DashboardUiState.Success -> {
                val summary = state.summary
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MonthSelector(
                            selectedMonth = selectedMonth,
                            onMonthChange = viewModel::previousMonth,
                            onNextMonth = viewModel::nextMonth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        BalanceHeroCard(
                            summary = summary,
                            previousSummary = state.previousSummary,
                            isFuture = selectedMonth.isAfter(YearMonth.now())
                        )
                    }

                    item {
                        KeyMetrics(summary)
                    }

                    if (summary.warnings.isNotEmpty()) {
                        item {
                            InsightCard(summary.warnings.first())
                        }
                    } else {
                        item {
                            PositiveInsightCard(summary, state.previousSummary)
                        }
                    }

                    item {
                        FinanceSectionHeader(
                            title = "Bu ayın planı",
                            subtitle = "Henüz gerçekleşmemiş düzenli yükümlülükler",
                            action = {
                                TextButton(onClick = { navController.navigate(Screen.CashFlow.route) }) {
                                    Text("Takvim")
                                }
                            }
                        )
                    }
                    item {
                        PlannedPaymentsCard(summary)
                    }

                    item {
                        FinanceSectionHeader(
                            title = "Harcama dağılımı",
                            subtitle = "Bütçeni en çok etkileyen kategoriler",
                            action = {
                                TextButton(
                                    onClick = {
                                        navController.navigateToTopLevelDestination(Screen.Reports)
                                    }
                                ) {
                                    Text("Rapor")
                                }
                            }
                        )
                    }
                    if (summary.categorySummaries.isEmpty()) {
                        item { EmptyExpensesCard() }
                    } else {
                        items(summary.categorySummaries.take(4)) { category ->
                            CategorySummaryRow(category, summary.totalExpenseAmount)
                        }
                    }

                    item {
                        SavingGoalCard(
                            summary = summary,
                            onEdit = { showSavingGoalDialog = true },
                            onApplySuggestion = {
                                viewModel.applySuggestedSaving(summary.suggestedSavingAmount)
                            }
                        )
                    }

                    if (state.openDebts.isNotEmpty()) {
                        item {
                            val owed = state.openDebts
                                .filter { it.type == DebtType.I_OWE }
                                .sumOf { it.amount }
                            val receivable = state.openDebts
                                .filter { it.type == DebtType.OWED_TO_ME }
                                .sumOf { it.amount }
                            FinanceSectionHeader(
                                title = "Borç ve alacak",
                                subtitle = "${state.openDebts.size} açık kayıt"
                            )
                            Spacer(Modifier.height(8.dp))
                            FinanceCard {
                                Row(
                                    Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MetricTile(
                                        label = "Ödenecek",
                                        value = MoneyFormatter.format(owed),
                                        modifier = Modifier.weight(1f),
                                        valueColor = MaterialTheme.colorScheme.error
                                    )
                                    MetricTile(
                                        label = "Alınacak",
                                        value = MoneyFormatter.format(receivable),
                                        modifier = Modifier.weight(1f),
                                        valueColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (showSavingGoalDialog) {
                    SavingGoalDialog(
                        currentAmount = summary.savingGoalAmount,
                        onDismiss = { showSavingGoalDialog = false },
                        onConfirm = { amount ->
                            viewModel.updateSavingGoal(amount)
                            showSavingGoalDialog = false
                        }
                    )
                }
            }

            is DashboardUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun BalanceHeroCard(
    summary: MonthlyBudgetSummary,
    previousSummary: MonthlyBudgetSummary,
    isFuture: Boolean
) {
    val balance = summary.remainingAfterSavingAndFixedPayments
    val difference = balance - previousSummary.remainingAfterSavingAndFixedPayments

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isFuture) "Plan sonrası beklenen" else "Plan sonrası kullanılabilir",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f)
                )
                if (isFuture) {
                    StatusPill(
                        text = "PROJEKSİYON",
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                MoneyFormatter.format(balance),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    previousSummary.totalIncomeAmount == 0L && previousSummary.totalExpenseAmount == 0L ->
                        "Karşılaştırma için önceki ay verisi bulunmuyor"
                    difference >= 0L ->
                        "Geçen aya göre ${MoneyFormatter.format(difference)} daha iyi"
                    else ->
                        "Geçen aya göre ${MoneyFormatter.format(difference.absoluteValue)} daha düşük"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroMetric("Gelir", MoneyFormatter.format(summary.totalIncomeAmount))
                HeroMetric("Harcama", MoneyFormatter.format(summary.totalExpenseAmount))
                HeroMetric("Planlı", MoneyFormatter.format(summary.projectedFixedPaymentsAmount))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f)
        )
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun KeyMetrics(summary: MonthlyBudgetSummary) {
    val income = summary.totalIncomeAmount
    val savingRate = if (income > 0L) summary.savingGoalAmount * 100 / income else 0L
    val expenseRate = if (income > 0L) summary.totalExpenseAmount * 100 / income else 0L
    val fixedRate = if (income > 0L) summary.projectedFixedPaymentsAmount * 100 / income else 0L
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile(
            label = "Birikim",
            value = "%$savingRate",
            modifier = Modifier.weight(1f),
            supportingText = "gelir oranı"
        )
        MetricTile(
            label = "Harcama",
            value = "%$expenseRate",
            modifier = Modifier.weight(1f),
            supportingText = "gelir oranı",
            valueColor = if (expenseRate > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        MetricTile(
            label = "Sabit yük",
            value = "%$fixedRate",
            modifier = Modifier.weight(1f),
            supportingText = "gelir oranı"
        )
    }
}

@Composable
private fun InsightCard(warning: BudgetWarning) {
    FinanceCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Bu ay dikkat et", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(warning.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PositiveInsightCard(
    summary: MonthlyBudgetSummary,
    previousSummary: MonthlyBudgetSummary
) {
    val diff = summary.totalExpenseAmount - previousSummary.totalExpenseAmount
    FinanceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Aylık içgörü", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        previousSummary.totalExpenseAmount == 0L ->
                            "Harcama düzenin oluştukça burada karşılaştırmalı sonuçlar göreceksin."
                        diff <= 0L ->
                            "Harcamaların geçen aya göre ${MoneyFormatter.format(diff.absoluteValue)} azaldı."
                        else ->
                            "Harcamaların geçen aya göre ${MoneyFormatter.format(diff)} arttı."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PlannedPaymentsCard(summary: MonthlyBudgetSummary) {
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PaymentRow("Kredi kartı ekstreleri", summary.creditCardPaymentAmount)
            PaymentRow("Abonelikler", summary.subscriptionPlannedAmount)
            PaymentRow("Kredi ödemeleri", summary.loanPaymentAmount)
            PaymentRow("Sabit giderler", summary.fixedExpenseAmount)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Toplam planlı ödeme", style = MaterialTheme.typography.titleSmall)
                Text(
                    MoneyFormatter.format(summary.projectedFixedPaymentsAmount),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(label: String, amount: Long) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SavingGoalCard(
    summary: MonthlyBudgetSummary,
    onEdit: () -> Unit,
    onApplySuggestion: () -> Unit
) {
    val available = summary.remainingAfterFixedPayments.coerceAtLeast(0L)
    val progress = if (summary.savingGoalAmount > 0L) {
        (available.toFloat() / summary.savingGoalAmount).coerceIn(0f, 1f)
    } else {
        0f
    }
    FinanceCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(9.dp).size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Birikim hedefi", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Bu ay için ayırdığın tutar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onEdit) { Text("Düzenle") }
            }
            Text(
                MoneyFormatter.format(summary.savingGoalAmount),
                style = MaterialTheme.typography.headlineMedium
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (summary.suggestedSavingAmount > 0L &&
                summary.suggestedSavingAmount != summary.savingGoalAmount
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Önerilen: ${MoneyFormatter.format(summary.suggestedSavingAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onApplySuggestion) { Text("Uygula") }
                }
            }
        }
    }
}

@Composable
private fun EmptyExpensesCard() {
    FinanceCard {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("Bu ay henüz harcama yok", style = MaterialTheme.typography.titleSmall)
            Text(
                "İlk işlemini eklediğinde dağılım burada görünür.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthChange: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMonthChange) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Ay")
            }
            Text(
                DateUtils.formatMonthYear(selectedMonth),
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Ay")
            }
        }
    }
}

@Composable
fun CategorySummaryRow(summary: CategorySummary, totalExpenseAmount: Long) {
    val share = if (totalExpenseAmount > 0L) {
        summary.amount.toFloat() / totalExpenseAmount
    } else {
        0f
    }
    val progress = summary.percentage ?: share
    val progressColor = when {
        summary.percentage != null && summary.percentage > 1f -> MaterialTheme.colorScheme.error
        summary.percentage != null && summary.percentage >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> Color(summary.colorValue)
    }

    FinanceCard {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = Color(summary.colorValue).copy(alpha = 0.12f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        IconMapper.getIcon(summary.iconName),
                        contentDescription = null,
                        tint = Color(summary.colorValue),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.categoryName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (summary.budgetLimit != null) {
                            "${MoneyFormatter.format(summary.amount)} / ${MoneyFormatter.format(summary.budgetLimit)}"
                        } else {
                            "Toplam harcamanın %${(share * 100).toInt()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    MoneyFormatter.format(summary.amount),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun SavingGoalDialog(
    currentAmount: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountText by remember { mutableStateOf((currentAmount / 100).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Birikim hedefi") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Hedef tutar (TL)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(MoneyFormatter.parse(amountText) ?: 0L)
            }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
