package com.umit.budgettracker.feature.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    navController: NavController,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val statements by viewModel.statementUiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEditDates by remember { mutableStateOf<PaymentAccount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartlar ve Hesaplar") },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                    IconButton(onClick = { navController.navigate(Screen.Installments.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Taksitler")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Kart Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                AccountRow(
                    account = account,
                    statement = statements.find { it.accountId == account.id },
                    onEditDates = { accountToEditDates = account },
                    onMarkPaid = { amount -> viewModel.markStatementPaid(account.id, amount) },
                    onMarkUnpaid = { viewModel.markStatementUnpaid(account.id) }
                )
            }
        }

        if (showAddDialog) {
            AddCreditCardDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, sDay, dDay ->
                    viewModel.addCreditCard(name, sDay, dDay)
                    showAddDialog = false
                }
            )
        }

        accountToEditDates?.let { account ->
            StatementDatesDialog(
                account = account,
                selectedMonth = selectedMonth,
                onDismiss = { accountToEditDates = null },
                onConfirm = { month, statementDay, dueDay ->
                    viewModel.saveStatementRule(account.id, month, statementDay, dueDay)
                    accountToEditDates = null
                }
            )
        }
    }
}

@Composable
fun AccountRow(
    account: PaymentAccount,
    statement: CardStatementUiModel?,
    onEditDates: () -> Unit,
    onMarkPaid: (Long) -> Unit,
    onMarkUnpaid: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (account.type == AccountType.CREDIT_CARD) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (account.type) {
                    AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                    AccountType.BANK_ACCOUNT -> Icons.Default.AccountBalance
                    AccountType.CASH -> Icons.Default.Payments
                },
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (account.type == AccountType.CREDIT_CARD) {
                    Text(
                        text = "Kesim: ${account.statementDay}. gün • Son Ödeme: ${account.dueDay}. gün",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onEditDates, contentPadding = PaddingValues(0.dp)) {
                        Text("Kesim / ödeme tarihi düzenle")
                    }
                    statement?.let {
                        CreditCardStatementBlock(
                            statement = it,
                            selectedMonth = it.summary.paymentMonth,
                            onMarkPaid = onMarkPaid,
                            onMarkUnpaid = onMarkUnpaid
                        )
                    }
                } else {
                    Text(text = "Hesap Türü: ${account.type.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatementDatesDialog(
    account: PaymentAccount,
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth, Int, Int) -> Unit
) {
    var effectiveMonth by remember { mutableStateOf(selectedMonth.toString()) }
    var statementDay by remember { mutableStateOf(account.statementDay?.toString().orEmpty()) }
    var dueDay by remember { mutableStateOf(account.dueDay?.toString().orEmpty()) }
    val parsedMonth = runCatching { YearMonth.parse(effectiveMonth) }.getOrNull()
    val parsedStatementDay = statementDay.toIntOrNull()
    val parsedDueDay = dueDay.toIntOrNull()
    val valid = parsedMonth != null && parsedStatementDay in 1..31 && parsedDueDay in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ekstre Tarihi Kuralı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bu kural yalnızca seçilen ay ve sonrasındaki ekstreleri etkiler.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(effectiveMonth, { effectiveMonth = it }, label = { Text("Geçerlilik Ayı (YYYY-MM)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(statementDay, { statementDay = it }, label = { Text("Kesim Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dueDay, { dueDay = it }, label = { Text("Son Ödeme Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(parsedMonth!!, parsedStatementDay!!, parsedDueDay!!) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun CreditCardStatementBlock(
    statement: CardStatementUiModel,
    selectedMonth: YearMonth,
    onMarkPaid: (Long) -> Unit,
    onMarkUnpaid: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var showExpenses by remember(statement.accountId, selectedMonth) { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Bu Ay Ödenecek Ekstre: ${MoneyFormatter.format(statement.totalAmount)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "${statement.summary.statementStartDate.format(dateFormatter)} - ${statement.summary.statementEndDate.format(dateFormatter)}",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        text = "Son ödeme: ${statement.summary.dueDate.format(dateFormatter)}",
        style = MaterialTheme.typography.bodySmall
    )

    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (statement.isPaid) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ödendi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMarkUnpaid) {
                Text("Geri Al")
            }
        } else {
            Text(
                text = "Ödenmedi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = statement.totalAmount > 0,
                onClick = { onMarkPaid(statement.totalAmount) }
            ) {
                Text("Ödendi")
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        enabled = statement.summary.expenses.isNotEmpty(),
        onClick = { showExpenses = !showExpenses },
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        Text("Ekstre Harcamaları (${statement.summary.expenses.size})")
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (showExpenses) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null
        )
    }

    if (showExpenses) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            statement.summary.expenses.forEach { expense ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = expense.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${expense.expenseDate.format(dateFormatter)} • ${expense.category?.name ?: "Kategorisiz"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = MoneyFormatter.format(statement.netAmount(expense)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AddCreditCardDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Kredi Kartı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kart Adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = statementDay, onValueChange = { statementDay = it }, label = { Text("Hesap Kesim Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Son Ödeme Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && statementDay.toIntOrNull() in 1..31 && dueDay.toIntOrNull() in 1..31,
                onClick = {
                    onConfirm(name, statementDay.toInt(), dueDay.toInt())
                }
            ) {
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
