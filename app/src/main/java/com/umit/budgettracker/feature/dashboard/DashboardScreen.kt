package com.umit.budgettracker.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

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
        topBar = {
            TopAppBar(
                title = { Text("Özet") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.CashFlow.route) }) {
                        Icon(Icons.Default.Event, contentDescription = "Nakit Akışı")
                    }
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
            is DashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DashboardUiState.Success -> {
                val summary = state.summary
                val isFuture = selectedMonth.isAfter(YearMonth.now())

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isFuture) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Gelecek ay projeksiyonu",
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (summary.warnings.isNotEmpty()) {
                        items(summary.warnings) { warning ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = warning.message, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        SummaryCard(
                            title = "Maaş",
                            amount = MoneyFormatter.format(summary.salaryAmount),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { navController.navigate(Screen.SalaryManagement.route) }
                        )
                    }
                    item {
                        SummaryCard(
                            title = "Ek Gelirler",
                            amount = MoneyFormatter.format(summary.additionalIncomeAmount),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { navController.navigate(Screen.Income.route) }
                        )
                    }
                    item {
                        SummaryCard(
                            title = "Toplam Gelir",
                            amount = MoneyFormatter.format(summary.totalIncomeAmount),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                    item {
                        SummaryCard(
                            title = "Birikim Hedefi",
                            amount = MoneyFormatter.format(summary.savingGoalAmount),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { showSavingGoalDialog = true }
                        )
                    }
                    
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Harcamalar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { navController.navigate(Screen.CategoryBudgets.route) }) {
                                Text("Bütçeler")
                            }
                        }
                    }

                    item {
                        SummaryCard(
                            title = "Bu Ay Toplam Harcama",
                            amount = MoneyFormatter.format(summary.totalExpenseAmount),
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                    
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "Kart Özetleri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Bu Ay Kartla Yapılan Harcama:", style = MaterialTheme.typography.bodySmall)
                                    Text(text = MoneyFormatter.format(summary.calendarCreditCardSpendingAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Ödenecek Kart Ekstreleri:", style = MaterialTheme.typography.bodySmall)
                                    Text(text = MoneyFormatter.format(summary.creditCardPaymentAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Planlanan Sabit Ödemeler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { navController.navigate(Screen.DebtTracking.route) }) {
                                Text("Borçlar")
                            }
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard(
                                title = "Abonelikler",
                                amount = MoneyFormatter.format(summary.subscriptionPlannedAmount),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate(Screen.Subscriptions.route) }
                            )
                            SummaryCard(
                                title = "Kredi Ödemeleri",
                                amount = MoneyFormatter.format(summary.loanPaymentAmount),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate(Screen.Loans.route) }
                            )
                        }
                    }

                    item {
                        Text(text = "Projeksiyon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Sabit Ödemeler Sonrası Kalan:", style = MaterialTheme.typography.bodySmall)
                                    Text(text = MoneyFormatter.format(summary.remainingAfterFixedPayments), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Birikim + Sabit Ödemeler Sonrası:", style = MaterialTheme.typography.bodySmall)
                                    Text(text = MoneyFormatter.format(summary.remainingAfterSavingAndFixedPayments), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (summary.categorySummaries.isNotEmpty()) {
                        item {
                            Text(
                                text = "Kategorilere Göre Harcama",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(summary.categorySummaries) { category ->
                            CategorySummaryRow(category)
                        }
                    } else {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(text = "Bu ay henüz harcama yok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthChange: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMonthChange) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Ay")
        }
        Text(
            text = DateUtils.formatMonthYear(selectedMonth),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Ay")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryCard(
    title: String,
    amount: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick ?: {}
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }
            Text(text = amount, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategorySummaryRow(summary: CategorySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    IconMapper.getIcon(summary.iconName),
                    contentDescription = null,
                    tint = Color(summary.colorValue)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = summary.categoryName, style = MaterialTheme.typography.bodyLarge)
                if (summary.budgetLimit != null) {
                    Text(
                        text = "Limit: ${MoneyFormatter.format(summary.budgetLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (summary.percentage != null && summary.percentage > 1f) Color.Red else Color.Unspecified
                    )
                }
            }
            Text(
                text = MoneyFormatter.format(summary.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
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
        title = { Text("Birikim Hedefi Belirle") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Hedef Tutar (TL)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = MoneyFormatter.parse(amountText) ?: 0L
                onConfirm(amount)
            }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
