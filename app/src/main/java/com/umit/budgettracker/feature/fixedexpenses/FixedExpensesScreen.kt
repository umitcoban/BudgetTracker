package com.umit.budgettracker.feature.fixedexpenses

import com.umit.budgettracker.core.ui.components.DateField
import com.umit.budgettracker.core.ui.components.MonthField
import androidx.compose.material.icons.filled.EventRepeat
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.EmptyState
import com.umit.budgettracker.core.ui.components.DetailScaffold
import com.umit.budgettracker.core.ui.components.AppAlertDialog
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
import com.umit.budgettracker.core.util.DateUtils
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

    val processedIds by viewModel.processedIds.collectAsState()
    val applies = { e: FixedExpense ->
        e.isActive && !selectedMonth.isBefore(e.startMonth) && (e.endMonth == null || !selectedMonth.isAfter(e.endMonth))
    }
    val current = fixedExpenses.filter(applies)
    val toProcess = current.filter { it.id !in processedIds }
    val processed = current.filter { it.id in processedIds }
    val others = fixedExpenses.filterNot(applies)
    var showOthers by remember { mutableStateOf(false) }

    @Composable
    fun FixedItem(expense: FixedExpense) = FixedExpenseRow(
        expense = expense,
        isProcessed = expense.id in processedIds,
        applies = applies(expense),
        onEdit = {
            editingExpense = expense
            showDialog = true
        },
        onDelete = { viewModel.deleteFixedExpense(expense) },
        onMarkAsPaid = { viewModel.markAsPaid(expense) }
    )

    DetailScaffold(
        title = "Sabit Giderler",
        subtitle = "Kira, aidat, fatura gibi düzenli ödemeler",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthSelector(
                    selectedMonth = selectedMonth,
                    onMonthChange = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (fixedExpenses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.EventRepeat,
                        title = "Henüz sabit gider yok",
                        message = "Kira, aidat gibi her ay tekrarlayan ödemeleri ekle; plana ve nakit akışına girer."
                    )
                }
            }
            if (toProcess.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Bu ay işlenecek", subtitle = "${toProcess.size} kalem · ${MoneyFormatter.format(toProcess.sumOf { it.amount })}") }
                items(toProcess, key = { it.id }) { FixedItem(it) }
            }
            if (processed.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Bu ay işlendi", subtitle = "${processed.size} kalem") }
                items(processed, key = { it.id }) { FixedItem(it) }
            }
            if (others.isNotEmpty()) {
                item {
                    TextButton(onClick = { showOthers = !showOthers }) {
                        Text("Bu ay geçerli olmayanlar (${others.size}) " + if (showOthers) "▲" else "▼")
                    }
                }
                if (showOthers) items(others, key = { it.id }) { FixedItem(it) }
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
    isProcessed: Boolean,
    applies: Boolean,
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
                    // The amount sits under the title: beside it, it left the title a narrow column.
                    Text(MoneyFormatter.format(expense.amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Her ay ${expense.dayOfMonth}. gün • " + (expense.endMonth?.let { "${DateUtils.formatMonthYear(expense.startMonth)} – ${DateUtils.formatMonthYear(it)}" }
                            ?: "Başlangıç: ${DateUtils.formatMonthYear(expense.startMonth)}"),
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
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
            when {
                isProcessed -> Text(
                    "✓ Bu ay harcamalara işlendi",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
                applies -> Button(onClick = onMarkAsPaid, modifier = Modifier.align(Alignment.End)) {
                    Text("Harcamalara işle")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AppAlertDialog(
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

    AppAlertDialog(
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
                MonthField(label = "Başlangıç ayı", value = runCatching { java.time.YearMonth.parse(startMonthText) }.getOrNull(), onChange = { startMonthText = it?.toString().orEmpty() }, modifier = Modifier.fillMaxWidth())
                MonthField(label = "Bitiş ayı (isteğe bağlı)", value = endMonth, onChange = { endMonthText = it?.toString().orEmpty() }, allowClear = true, modifier = Modifier.fillMaxWidth())
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
                if (existingExpense != null) {
                    Text(
                        text = "Bu sabit giderden daha önce harcama oluşturulduysa, o harcamalar değişmeden korunur.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
