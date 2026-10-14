package com.umit.budgettracker.feature.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.PlannedExpenseRules
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.PlannedExpense
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.PlannedExpenseRepository
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class PlannedExpensesViewModel @Inject constructor(
    private val repository: PlannedExpenseRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {
    val items: StateFlow<List<PlannedExpense>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(item: PlannedExpense) = viewModelScope.launch { repository.upsert(item) }
    fun delete(item: PlannedExpense) = viewModelScope.launch { repository.delete(item.id) }

    fun addSaved(item: PlannedExpense, amount: Long) = viewModelScope.launch {
        repository.upsert(item.copy(savedAmount = (item.savedAmount + amount).coerceAtLeast(0L)))
        _message.value = "Ayrılan tutar güncellendi."
    }

    /** Yearly items roll to next year's date; one-off items are marked done. */
    fun complete(item: PlannedExpense) = viewModelScope.launch {
        if (item.isYearly) {
            val next = PlannedExpenseRules.nextOccurrence(item)
            repository.upsert(next)
            _message.value = "${item.title} tamamlandı; sonraki: ${DateUtils.formatDayMonth(next.dueDate)} ${next.dueDate.year}."
        } else {
            repository.upsert(item.copy(isCompleted = true))
            _message.value = "${item.title} tamamlandı."
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun PlannedExpensesScreen(
    onBack: () -> Unit,
    viewModel: PlannedExpensesViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<PlannedExpense?>(null) }
    var creating by remember { mutableStateOf(false) }
    var savingFor by remember { mutableStateOf<PlannedExpense?>(null) }
    var deleting by remember { mutableStateOf<PlannedExpense?>(null) }
    val currentMonth = YearMonth.now()
    val open = items.filter { !it.isCompleted }
    val done = items.filter { it.isCompleted }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Büyük Harcamalar",
        subtitle = "Tarihli yükler için aylık rezerv",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Plan ekle") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    label = "Bu ay rezerv için ayırman gereken",
                    value = MoneyFormatter.format(PlannedExpenseRules.totalMonthlyReserve(open, currentMonth)),
                    supporting = if (open.isEmpty()) "Kasko, MTV, aidat artışı, düğün hediyesi, beyaz eşya…" else
                        "${open.size} plan · toplam ${MoneyFormatter.format(open.sumOf { it.amount })} · ayrılan ${MoneyFormatter.format(open.sumOf { it.savedAmount })}"
                )
            }
            if (open.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.EventNote,
                        title = "Planlanmış büyük harcama yok",
                        message = "Tarihi belli yükleri ekle; \"N ay sonra X lazım → ayda Y ayır\" hesabını yapıp nakit akışı takviminde gösteririm.",
                        actionLabel = "Plan ekle",
                        onAction = { creating = true }
                    )
                }
            }
            items(open, key = { it.id }) { item ->
                PlannedCard(
                    item = item,
                    category = categories.firstOrNull { it.id == item.categoryId },
                    currentMonth = currentMonth,
                    onClick = { editing = item },
                    onAddSaved = { savingFor = item },
                    onComplete = { viewModel.complete(item) }
                )
            }
            if (done.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Tamamlananlar") }
                items(done, key = { "done_${it.id}" }) { item ->
                    ListRowCard(
                        title = item.title,
                        subtitle = "${DateUtils.formatDayMonth(item.dueDate)} ${item.dueDate.year} · ${MoneyFormatter.format(item.amount)}",
                        icon = Icons.Default.TaskAlt,
                        iconTint = MaterialTheme.colorScheme.outline,
                        onClick = { editing = item }
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        PlannedDialog(
            existing = editing,
            categories = categories,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { item -> { deleting = item; editing = null } },
            onSave = {
                viewModel.save(it)
                creating = false
                editing = null
            }
        )
    }
    savingFor?.let { item ->
        var text by remember { mutableStateOf("") }
        val amount = MoneyInput.parse(text)
        FormDialog(
            title = "${item.title} için ayır",
            onDismiss = { savingFor = null },
            confirmEnabled = amount != null && amount != 0L,
            onConfirm = { viewModel.addSaved(item, amount!!); savingFor = null }
        ) {
            Text("Şu ana kadar ayrılan: ${MoneyFormatter.format(item.savedAmount)}", style = MaterialTheme.typography.bodySmall)
            AmountField(value = text, onValueChange = { text = it }, label = "Eklenecek tutar", supportingText = "Azaltmak için eksi değer gir")
        }
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "Planı sil",
            message = "${item.title} planı silinecek. Harcama kayıtları etkilenmez.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.delete(item); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PlannedCard(
    item: PlannedExpense,
    category: Category?,
    currentMonth: YearMonth,
    onClick: () -> Unit,
    onAddSaved: () -> Unit,
    onComplete: () -> Unit
) {
    val months = PlannedExpenseRules.monthsUntil(item, currentMonth)
    val reserve = PlannedExpenseRules.monthlyReserve(item, currentMonth)
    val overdue = item.dueDate.isBefore(LocalDate.now())
    FinanceCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = Icons.Default.Event, tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            "${DateUtils.formatDayMonth(item.dueDate)} ${item.dueDate.year}",
                            if (item.isYearly) "her yıl" else null,
                            category?.name
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(MoneyFormatter.format(item.amount), style = MaterialTheme.typography.titleSmall)
            }
            LabeledProgress(
                progress = if (item.amount > 0L) item.savedAmount.toFloat() / item.amount else 0f,
                startLabel = "Ayrılan ${MoneyFormatter.format(item.savedAmount)}",
                endLabel = if (overdue) "Tarihi geçti" else "→ ayda ${MoneyFormatter.format(reserve)} ($months ay)"
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onAddSaved) { Text("Ayrılan ekle") }
                TextButton(onClick = onComplete) { Text(if (item.isYearly) "Ödendi · sonraki yıla" else "Tamamlandı") }
            }
        }
    }
}

@Composable
private fun PlannedDialog(
    existing: PlannedExpense?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (PlannedExpense) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var amountText by remember { mutableStateOf(MoneyInput.format(existing?.amount)) }
    var savedText by remember { mutableStateOf(MoneyInput.format(existing?.savedAmount?.takeIf { it > 0 })) }
    var dueDate by remember { mutableStateOf(existing?.dueDate ?: LocalDate.now().plusMonths(6)) }
    var category by remember { mutableStateOf(existing?.categoryId?.let { id -> categories.firstOrNull { it.id == id } }) }
    var yearly by remember { mutableStateOf(existing?.isYearly ?: false) }
    var completed by remember { mutableStateOf(existing?.isCompleted ?: false) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val amount = MoneyInput.parse(amountText)
    val saved = savedText.takeIf { it.isNotBlank() }?.let { MoneyInput.parse(it) } ?: 0L
    val preview = amount?.let {
        PlannedExpenseRules.monthlyReserve(PlannedExpense(0, title, it, dueDate, null, saved, yearly, false, null), YearMonth.now())
    }
    FormDialog(
        title = if (existing == null) "Büyük harcama planla" else "Planı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = title.isNotBlank() && amount != null && amount > 0L,
        onConfirm = {
            onSave(
                PlannedExpense(
                    id = existing?.id ?: 0L,
                    title = title.trim(),
                    amount = amount!!,
                    dueDate = dueDate,
                    categoryId = category?.id,
                    savedAmount = saved,
                    isYearly = yearly,
                    isCompleted = completed,
                    note = note.ifBlank { null }
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Ad (ör. Kasko)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Tutar")
        DateField(label = "Tarih", value = dueDate, onChange = { dueDate = it })
        AmountField(value = savedText, onValueChange = { savedText = it }, label = "Şimdiye kadar ayrılan", supportingText = preview?.let { "→ ayda ${MoneyFormatter.format(it)} ayırmalısın" })
        DropdownField(
            label = "Kategori (isteğe bağlı)",
            options = listOf<Category?>(null) + categories,
            selected = category,
            onSelect = { category = it },
            optionLabel = { it?.name ?: "Yok" },
            placeholder = "Yok"
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Her yıl tekrarlanır", style = MaterialTheme.typography.bodyMedium)
                Text("Kasko, MTV, aidat gibi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = yearly, onCheckedChange = { yearly = it })
        }
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tamamlandı", modifier = Modifier.weight(1f))
                Switch(checked = completed, onCheckedChange = { completed = it })
            }
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
