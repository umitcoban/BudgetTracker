package com.umit.budgettracker.feature.fixedexpenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedExpensesScreen(
    onBack: () -> Unit,
    viewModel: FixedExpensesViewModel = hiltViewModel()
) {
    val fixedExpenses by viewModel.fixedExpenses.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingExpense by remember { mutableStateOf<FixedExpense?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sabit Giderler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingExpense = null
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Sabit Gider Ekle")
            }
        }
    ) { padding ->
        if (fixedExpenses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Henüz sabit gider eklenmedi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fixedExpenses) { expense ->
                    FixedExpenseRow(
                        expense = expense,
                        onEdit = {
                            editingExpense = expense
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteFixedExpense(expense) },
                        onMarkAsPaid = { viewModel.markAsPaid(expense) }
                    )
                }
            }
        }

        if (showDialog) {
            FixedExpenseDialog(
                existingExpense = editingExpense,
                categories = categories,
                accounts = accounts,
                onDismiss = { showDialog = false },
                onConfirm = {
                    viewModel.saveFixedExpense(it)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun FixedExpenseRow(
    expense: FixedExpense,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsPaid: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Her ay ${expense.dayOfMonth}. gün • ${expense.startMonth}${expense.endMonth?.let { " - $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!expense.isActive) {
                        Text("Pasif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "${expense.category?.name ?: "Kategori seçilmedi"} • ${expense.account?.name ?: "Hesap seçilmedi"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(MoneyFormatter.format(expense.amount), fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
            Button(
                onClick = onMarkAsPaid,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Harcamalara İşle")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Sabit Gideri Sil") },
            text = { Text("Bu sabit gideri silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixedExpenseDialog(
    existingExpense: FixedExpense?,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    onDismiss: () -> Unit,
    onConfirm: (FixedExpense) -> Unit
) {
    var title by remember { mutableStateOf(existingExpense?.title ?: "") }
    var amountText by remember { mutableStateOf(existingExpense?.amount?.formatMinor() ?: "") }
    var dayText by remember { mutableStateOf(existingExpense?.dayOfMonth?.toString() ?: "1") }
    var startMonthText by remember { mutableStateOf(existingExpense?.startMonth?.toString() ?: YearMonth.now().toString()) }
    var endMonthText by remember { mutableStateOf(existingExpense?.endMonth?.toString().orEmpty()) }
    var note by remember { mutableStateOf(existingExpense?.note.orEmpty()) }
    var isActive by remember { mutableStateOf(existingExpense?.isActive ?: true) }
    var selectedCategory by remember { mutableStateOf(existingExpense?.category ?: categories.firstOrNull { it.id == existingExpense?.categoryId } ?: categories.firstOrNull()) }
    var selectedAccount by remember { mutableStateOf(existingExpense?.account ?: accounts.firstOrNull { it.id == existingExpense?.paymentAccountId } ?: accounts.firstOrNull()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    val amount = MoneyFormatter.parse(amountText)
    val day = dayText.toIntOrNull() ?: 0
    val startMonth = runCatching { YearMonth.parse(startMonthText) }.getOrNull()
    val endMonth = endMonthText.takeIf { it.isNotBlank() }?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    val endMonthInvalid = endMonthText.isNotBlank() && endMonth == null
    val isValid = title.isNotBlank() &&
        (amount ?: 0L) > 0 &&
        day in 1..31 &&
        startMonth != null &&
        selectedCategory != null &&
        selectedAccount != null &&
        !endMonthInvalid &&
        (endMonth == null || !endMonth.isBefore(startMonth))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingExpense == null) "Sabit Gider Ekle" else "Sabit Gideri Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Tutar (TL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it },
                    label = { Text("Ödeme Günü (1-31)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startMonthText,
                    onValueChange = { startMonthText = it },
                    label = { Text("Başlangıç Ayı (YYYY-MM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endMonthText,
                    onValueChange = { endMonthText = it },
                    label = { Text("Bitiş Ayı (Opsiyonel)") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        leadingIcon = {
                            selectedCategory?.let { Icon(IconMapper.getIcon(it.iconName), contentDescription = null) }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                leadingIcon = { Icon(IconMapper.getIcon(category.iconName), contentDescription = null) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ödeme Hesabı") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccount = account
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktif")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        FixedExpense(
                            id = existingExpense?.id ?: 0L,
                            title = title,
                            amount = amount!!,
                            dayOfMonth = day,
                            startMonth = startMonth!!,
                            endMonth = endMonth,
                            categoryId = selectedCategory!!.id,
                            paymentAccountId = selectedAccount!!.id,
                            note = note.ifBlank { null },
                            isActive = isActive,
                            category = selectedCategory,
                            account = selectedAccount
                        )
                    )
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

private fun Long.formatMinor(): String {
    return BigDecimal(this)
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
