package com.umit.budgettracker.feature.cards

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.MetricTile
import com.umit.budgettracker.core.ui.components.StatusPill
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

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

    val totalStatement = statements.sumOf { it.totalAmount }
    val unpaidStatement = statements.filterNot { it.isPaid }.sumOf { it.totalAmount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kartlar", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Ekstreler ve ödeme durumları",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Installments.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Taksitler")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Kart ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "Toplam ekstre",
                        value = MoneyFormatter.format(totalStatement),
                        supportingText = "${statements.size} kredi kartı",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Ödenecek",
                        value = MoneyFormatter.format(unpaidStatement),
                        supportingText = "${statements.count { !it.isPaid }} bekleyen",
                        modifier = Modifier.weight(1f),
                        valueColor = if (unpaidStatement > 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            item {
                FinanceSectionHeader(
                    title = "Hesapların",
                    subtitle = "${accounts.size} aktif hesap"
                )
            }
            if (accounts.isEmpty()) {
                item {
                    FinanceCard {
                        Column(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Henüz kart veya hesap yok", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "İlk kredi kartını ekleyerek ekstre takibine başla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
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
        }

        if (showAddDialog) {
            AddCreditCardDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, statementDay, dueDay ->
                    viewModel.addCreditCard(name, statementDay, dueDay)
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
    var menuExpanded by remember { mutableStateOf(false) }
    FinanceCard(
        containerColor = if (account.type == AccountType.CREDIT_CARD) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (account.type == AccountType.CREDIT_CARD) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Icon(
                        when (account.type) {
                            AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                            AccountType.BANK_ACCOUNT -> Icons.Default.AccountBalance
                            AccountType.CASH -> Icons.Default.Payments
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(21.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        account.type.turkishLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (account.type == AccountType.CREDIT_CARD && statement != null) {
                    StatusPill(
                        text = if (statement.isPaid) "ÖDENDİ" else "BEKLİYOR",
                        containerColor = if (statement.isPaid) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (statement.isPaid) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (account.type == AccountType.CREDIT_CARD) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Kart seçenekleri")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Kesim ve ödeme tarihleri") },
                                onClick = {
                                    menuExpanded = false
                                    onEditDates()
                                }
                            )
                        }
                    }
                }
            }
            if (account.type == AccountType.CREDIT_CARD) {
                statement?.let {
                    CreditCardStatementBlock(
                        statement = it,
                        selectedMonth = it.summary.paymentMonth,
                        onMarkPaid = onMarkPaid,
                        onMarkUnpaid = onMarkUnpaid
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCardStatementBlock(
    statement: CardStatementUiModel,
    selectedMonth: YearMonth,
    onMarkPaid: (Long) -> Unit,
    onMarkUnpaid: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM") }
    var showExpenses by remember(statement.accountId, selectedMonth) { mutableStateOf(false) }
    val remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), statement.summary.dueDate)
    val dueLabel = when {
        remainingDays < 0 -> "${remainingDays.absoluteValue} gün gecikti"
        remainingDays == 0L -> "Bugün son gün"
        else -> "$remainingDays gün kaldı"
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(15.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                "Bu ay ödenecek",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                MoneyFormatter.format(statement.totalAmount),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dueLabel, style = MaterialTheme.typography.titleSmall)
            Text(
                "Son ödeme ${statement.summary.dueDate.format(dateFormatter)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "${statement.summary.statementStartDate.format(dateFormatter)} – " +
            "${statement.summary.statementEndDate.format(dateFormatter)} hesap dönemi",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            enabled = statement.summary.expenses.isNotEmpty(),
            onClick = { showExpenses = !showExpenses },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("${statement.summary.expenses.size} ekstre işlemi")
            Spacer(Modifier.width(4.dp))
            Icon(
                if (showExpenses) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
        if (statement.isPaid) {
            TextButton(onClick = onMarkUnpaid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("Ödemeyi geri al")
            }
        } else {
            Button(
                enabled = statement.totalAmount > 0L,
                onClick = { onMarkPaid(statement.totalAmount) }
            ) {
                Text("Ödendi işaretle")
            }
        }
    }

    if (showExpenses) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            statement.summary.expenses.forEach { expense ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${expense.expenseDate.format(dateFormatter)} • " +
                                (expense.category?.name ?: "Kategorisiz"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        MoneyFormatter.format(statement.netAmount(expense)),
                        style = MaterialTheme.typography.titleSmall
                    )
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
        title = { Text("Ekstre tarihleri") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Bu kural seçilen ay ve sonrasındaki ekstreleri etkiler.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    effectiveMonth,
                    { effectiveMonth = it },
                    label = { Text("Geçerlilik ayı (YYYY-MM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    statementDay,
                    { statementDay = it },
                    label = { Text("Kesim günü") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dueDay,
                    { dueDay = it },
                    label = { Text("Son ödeme günü") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onConfirm(parsedMonth!!, parsedStatementDay!!, parsedDueDay!!) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
fun AddCreditCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    val valid = name.isNotBlank() &&
        statementDay.toIntOrNull() in 1..31 &&
        dueDay.toIntOrNull() in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni kredi kartı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Kart adı") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    statementDay,
                    { statementDay = it },
                    label = { Text("Hesap kesim günü") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dueDay,
                    { dueDay = it },
                    label = { Text("Son ödeme günü") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onConfirm(name, statementDay.toInt(), dueDay.toInt()) }
            ) { Text("Kartı ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

private fun AccountType.turkishLabel(): String = when (this) {
    AccountType.CASH -> "Nakit"
    AccountType.BANK_ACCOUNT -> "Banka hesabı"
    AccountType.CREDIT_CARD -> "Kredi kartı"
}
