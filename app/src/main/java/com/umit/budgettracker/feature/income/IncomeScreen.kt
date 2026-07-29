package com.umit.budgettracker.feature.income

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.umit.budgettracker.core.domain.model.Income
import com.umit.budgettracker.core.domain.model.IncomeType
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    onBack: () -> Unit,
    viewModel: IncomeViewModel = hiltViewModel()
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    var editingIncome by remember { mutableStateOf<Income?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ek Gelirler") },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingIncome = null
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Gelir Ekle")
            }
        }
    ) { padding ->
        if (incomes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Bu ay için ek gelir yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val groupedIncomes = incomes
                .groupBy { it.incomeDate }
                .toSortedMap(compareByDescending { it })
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    IncomeMonthSummary(incomes)
                }
                item {
                    FinanceSectionHeader(
                        title = "Gelir hareketleri",
                        subtitle = "${incomes.size} kayıt"
                    )
                }
                groupedIncomes.forEach { (date, dayIncomes) ->
                    item {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("d MMMM")),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(dayIncomes) { income ->
                        IncomeRow(
                            income = income,
                            onEdit = {
                                editingIncome = income
                                showDialog = true
                            },
                            onDelete = { viewModel.deleteIncome(income) }
                        )
                    }
                }
            }
        }

        if (showDialog) {
            IncomeDialog(
                existingIncome = editingIncome,
                selectedMonth = selectedMonth,
                onDismiss = { showDialog = false },
                onConfirm = {
                    viewModel.saveIncome(it)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun IncomeRow(
    income: Income,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(income.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    income.type.toTurkishLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = MoneyFormatter.format(income.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Gelir seçenekleri")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Düzenle") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Geliri Sil") },
            text = { Text("Bu gelir kaydını silmek istediğinizden emin misiniz?") },
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

@Composable
private fun IncomeMonthSummary(incomes: List<Income>) {
    val total = incomes.sumOf { it.amount }
    val largest = incomes.maxByOrNull { it.amount }
    FinanceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "Bu ay ek gelir",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                MoneyFormatter.format(total),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                largest?.let {
                    "En yüksek: ${it.title} · ${MoneyFormatter.format(it.amount)}"
                } ?: "Henüz kayıt yok",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeDialog(
    existingIncome: Income?,
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (Income) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    var title by remember { mutableStateOf(existingIncome?.title ?: "") }
    var amountText by remember { mutableStateOf(existingIncome?.amount?.formatMinor() ?: "") }
    var incomeDateText by remember {
        mutableStateOf((existingIncome?.incomeDate ?: selectedMonth.defaultIncomeDate()).format(dateFormatter))
    }
    var note by remember { mutableStateOf(existingIncome?.note.orEmpty()) }
    var selectedType by remember { mutableStateOf(existingIncome?.type ?: IncomeType.EXTRA) }
    var typeExpanded by remember { mutableStateOf(false) }

    val amount = MoneyFormatter.parse(amountText)
    val incomeDate = runCatching { LocalDate.parse(incomeDateText, dateFormatter) }.getOrNull()
    val isValid = title.isNotBlank() && (amount ?: 0L) > 0 && incomeDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingIncome == null) "Gelir Ekle" else "Geliri Düzenle") },
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
                    value = incomeDateText,
                    onValueChange = { incomeDateText = it },
                    label = { Text("Tarih (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.toTurkishLabel(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gelir Türü") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        IncomeType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.toTurkishLabel()) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        Income(
                            id = existingIncome?.id ?: 0L,
                            title = title,
                            amount = amount!!,
                            incomeDate = incomeDate!!,
                            type = selectedType,
                            note = note.ifBlank { null }
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

private fun YearMonth.defaultIncomeDate(): LocalDate {
    val today = LocalDate.now()
    return if (this == YearMonth.from(today)) today else atDay(1)
}

private fun IncomeType.toTurkishLabel(): String {
    return when (this) {
        IncomeType.EXTRA -> "Ek Gelir"
        IncomeType.BONUS -> "Prim"
        IncomeType.FREELANCE -> "Freelance"
        IncomeType.SALE -> "Satış"
        IncomeType.DEBT_COLLECTION -> "Borç Tahsilatı"
        IncomeType.OTHER -> "Diğer"
    }
}

private fun Long.formatMinor(): String {
    return BigDecimal(this)
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
