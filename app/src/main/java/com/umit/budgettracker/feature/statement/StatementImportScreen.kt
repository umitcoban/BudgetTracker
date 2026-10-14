package com.umit.budgettracker.feature.statement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.AssistantRepository
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.usecase.RecordExpenseUseCase
import com.umit.budgettracker.core.ocr.DocumentTextExtractor
import com.umit.budgettracker.core.parsing.BankCategoryMap
import com.umit.budgettracker.core.parsing.BankStatementParser
import com.umit.budgettracker.core.parsing.StatementComparison
import com.umit.budgettracker.core.parsing.StatementMatch
import com.umit.budgettracker.core.parsing.StatementMatcher
import com.umit.budgettracker.core.parsing.StatementTransaction
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatementImportViewModel @Inject constructor(
    private val extractor: DocumentTextExtractor,
    private val expenseRepository: ExpenseRepository,
    private val assistantRepository: AssistantRepository,
    private val recordExpense: RecordExpenseUseCase,
    private val workScheduler: WorkScheduler,
    categoryRepository: CategoryRepository,
    accountRepository: PaymentAccountRepository
) : ViewModel() {
    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _account = MutableStateFlow<PaymentAccount?>(null)
    val account: StateFlow<PaymentAccount?> = _account.asStateFlow()
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private val _comparison = MutableStateFlow<StatementComparison?>(null)
    val comparison: StateFlow<StatementComparison?> = _comparison.asStateFlow()
    private val _unread = MutableStateFlow<List<String>>(emptyList())
    val unread: StateFlow<List<String>> = _unread.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Last four digits of each card found in the statement (Garanti / QNB files can hold several). */
    private val _cardHints = MutableStateFlow<List<String>>(emptyList())
    val cardHints: StateFlow<List<String>> = _cardHints.asStateFlow()
    private val _selectedCard = MutableStateFlow<String?>(null)
    val selectedCard: StateFlow<String?> = _selectedCard.asStateFlow()

    fun selectAccount(account: PaymentAccount) {
        _account.value = account
        if (_text.value.isNotBlank()) compare()
    }

    fun selectCard(card: String?) {
        _selectedCard.value = card
        if (_text.value.isNotBlank() && _account.value != null) compare()
    }

    fun setText(value: String) {
        _text.value = value
    }

    /** The file is read in memory only; neither the file nor its text is stored. */
    fun load(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { extractor.extract(uri) }
                .onSuccess {
                    _text.value = it
                    prepare()
                    if (_account.value != null) compare()
                    else _message.value = "Ekstre okundu. Hangi karta ait olduğunu seç."
                }
                .onFailure { _message.value = "Dosya okunamadı." }
            _busy.value = false
        }
    }

    /**
     * Reads card sections and, when an account name contains a card's last four digits,
     * preselects that account and card.
     */
    private fun prepare() {
        val hints = BankStatementParser.parse(_text.value).cardHints
        _cardHints.value = hints
        if (_selectedCard.value !in hints) _selectedCard.value = null
        if (_account.value == null) {
            accounts.value.firstNotNullOfOrNull { account ->
                hints.firstOrNull { hint -> account.name.contains(hint) }?.let { account to it }
            }?.let { (account, hint) ->
                _account.value = account
                _selectedCard.value = hint.takeIf { hints.size > 1 }
            }
        }
    }

    fun compare() {
        val account = _account.value ?: run {
            _message.value = "Önce ekstrenin ait olduğu kartı / hesabı seç."
            return
        }
        viewModelScope.launch {
            val result = BankStatementParser.parse(_text.value)
            _cardHints.value = result.cardHints
            val card = _selectedCard.value
            val parsed = result.copy(transactions = result.transactions.filter { card == null || it.cardHint == null || it.cardHint == card })
            _unread.value = parsed.unreadLines
            if (parsed.transactions.isEmpty()) {
                _comparison.value = null
                _message.value = "Tarih ve tutar içeren işlem satırı bulunamadı."
                return@launch
            }
            val start = parsed.transactions.minOf { it.date }.minusDays(StatementMatcher.DATE_TOLERANCE_DAYS)
            val end = parsed.transactions.maxOf { it.date }.plusDays(StatementMatcher.DATE_TOLERANCE_DAYS)
            val expenses = expenseRepository.observeExpensesForDateRange(start, end).first().filter { it.paymentAccountId == account.id }
            _comparison.value = StatementMatcher.compare(parsed.transactions, expenses, assistantRepository.getMerchantRules())
        }
    }

    /** Adds the confirmed new lines; each one goes through the duplicate check again. */
    fun addSelected(items: List<Pair<StatementTransaction, Long>>) {
        val account = _account.value ?: return
        viewModelScope.launch {
            var added = 0
            var skipped = 0
            items.forEach { (tx, categoryId) ->
                if (recordExpense.findDuplicate(tx.date, tx.amount, tx.description) != null) {
                    skipped++
                } else {
                    val note = listOfNotNull("Ekstreden", tx.foreignAmount, tx.installment?.let { "taksit $it" }).joinToString(" · ")
                    recordExpense(tx.description, tx.amount, tx.date, categoryId, account.id, note = note)
                    added++
                }
            }
            workScheduler.updateWidgets()
            _message.value = "$added işlem eklendi" + if (skipped > 0) ", $skipped olası mükerrer atlandı." else "."
            compare()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun StatementImportScreen(
    onBack: () -> Unit,
    viewModel: StatementImportViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val account by viewModel.account.collectAsState()
    val text by viewModel.text.collectAsState()
    val comparison by viewModel.comparison.collectAsState()
    val unread by viewModel.unread.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val cardHints by viewModel.cardHints.collectAsState()
    val selectedCard by viewModel.selectedCard.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showText by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Yeni") }
    val newItems = comparison?.matches?.filterIsInstance<StatementMatch.New>().orEmpty()
    // Instalment lines are better added from Taksitler (the remaining months are generated there).
    var selected by remember(comparison) { mutableStateOf(newItems.indices.filter { newItems[it].transaction.installment == null }.toSet()) }
    var categoryOverrides by remember(comparison) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var pickingCategoryFor by remember { mutableStateOf<Int?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::load) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val fallbackCategory = categories.firstOrNull { it.name == "Diğer" } ?: categories.firstOrNull()
    val categoryPairs = categories.map { it.id to it.name }
    fun suggestedFor(index: Int): Long? = newItems.getOrNull(index)?.let { item ->
        item.suggestedCategoryId ?: BankCategoryMap.suggest(item.transaction.bankCategory, categoryPairs)
    }
    fun categoryFor(index: Int): Long? = categoryOverrides[index] ?: suggestedFor(index) ?: fallbackCategory?.id

    DetailScaffold(
        title = "Ekstre Karşılaştır",
        subtitle = "PDF, Excel, CSV ya da kopyalanan metin",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            if (filter == "Yeni" && newItems.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${selected.size} yeni işlem seçili", style = MaterialTheme.typography.titleSmall)
                            Text(MoneyFormatter.format(selected.sumOf { newItems[it].transaction.amount }), style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { viewModel.addSelected(selected.sorted().mapNotNull { index -> categoryFor(index)?.let { newItems[index].transaction to it } }) },
                            enabled = selected.isNotEmpty() && account != null
                        ) { Text("Harcamalara ekle") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DropdownField(
                    label = "Ekstrenin kartı / hesabı",
                    options = accounts,
                    selected = account,
                    onSelect = viewModel::selectAccount,
                    optionLabel = { it.name },
                    placeholder = "Seç"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = {
                        fileLauncher.launch(
                            arrayOf(
                                "application/pdf", "text/*", "text/csv", "image/*",
                                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream"
                            )
                        )
                    }) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Dosya seç")
                    }
                    OutlinedButton(onClick = { showText = !showText }) { Text(if (showText) "Metni gizle" else "Metin yapıştır") }
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (showText) {
                item {
                    OutlinedTextField(
                        value = text,
                        onValueChange = viewModel::setText,
                        label = { Text("Ekstre satırları") },
                        placeholder = { Text("12.10.2026  MIGROS KADIKOY  450,75") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row { Spacer(Modifier.weight(1f)); Button(onClick = viewModel::compare, enabled = text.isNotBlank()) { Text("Karşılaştır") } }
                }
            }
            if (cardHints.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ekstrede ${cardHints.size} kart var", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selectedCard == null, onClick = { viewModel.selectCard(null) }, label = { Text("Tümü") })
                            cardHints.forEach { hint ->
                                FilterChip(selected = selectedCard == hint, onClick = { viewModel.selectCard(hint) }, label = { Text("•••• $hint") })
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Dosya cihazda okunur ve saklanmaz. Yalnızca onayladığın işlemler harcama olarak kaydedilir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (comparison == null && !busy) {
                item {
                    EmptyState(
                        icon = Icons.Default.CompareArrows,
                        title = "Ekstreni uygulamayla karşılaştır",
                        message = "1) Kartı / hesabı seç  2) Bankadan indirdiğin PDF, Excel ya da CSV ekstreyi seç veya satırları yapıştır  3) Eşleşmeyen yeni işlemleri kontrol edip tek dokunuşla ekle."
                    )
                }
            }
            comparison?.let { result ->
                item { ComparisonSummary(result) }
                item {
                    SegmentedChoice(
                        options = listOf("Yeni", "Eşleşen", "Uyumsuz", "Ekstrede yok"),
                        selected = filter,
                        onSelect = { filter = it },
                        label = {
                            when (it) {
                                "Yeni" -> "➕ ${result.newCount}"
                                "Eşleşen" -> "✓ ${result.matchedCount}"
                                "Uyumsuz" -> "? ${result.mismatchCount}"
                                else -> "− ${result.missingFromStatement.size}"
                            }
                        }
                    )
                }
                when (filter) {
                    "Yeni" -> items(newItems.indices.toList()) { index ->
                        val item = newItems[index]
                        val checked = index in selected
                        val categoryName = categories.firstOrNull { it.id == categoryFor(index) }?.name ?: "Kategori seç"
                        FinanceCard(modifier = Modifier.clickable { selected = if (checked) selected - index else selected + index }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.transaction.description, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOfNotNull(
                                            DateUtils.formatDate(item.transaction.date),
                                            item.transaction.cardHint?.takeIf { cardHints.size > 1 }?.let { "•••• $it" },
                                            item.transaction.foreignAmount,
                                            item.transaction.bankCategory
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    item.transaction.installment?.let { installment ->
                                        Text(
                                            "Taksit $installment · kalan taksitler için Taksitler ekranını kullanman önerilir",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    AssistChip(
                                        onClick = { pickingCategoryFor = index },
                                        label = {
                                            Text(
                                                categoryName + when {
                                                    categoryOverrides[index] != null -> ""
                                                    item.suggestedCategoryId != null -> " · öğrenilen"
                                                    suggestedFor(index) != null -> " · bankanın etiketi"
                                                    else -> ""
                                                }
                                            )
                                        }
                                    )
                                }
                                Text(MoneyFormatter.format(item.transaction.amount), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    "Eşleşen" -> items(result.matches.filterIsInstance<StatementMatch.Matched>()) { match ->
                        ListRowCard(
                            title = match.transaction.description,
                            subtitle = "${DateUtils.formatDate(match.transaction.date)} ↔ ${match.expense.title} (${DateUtils.formatDate(match.expense.expenseDate)})",
                            icon = Icons.Default.CheckCircle,
                            trailing = { Text(MoneyFormatter.format(match.transaction.amount), style = MaterialTheme.typography.titleSmall) }
                        )
                    }
                    "Uyumsuz" -> items(result.matches.filterIsInstance<StatementMatch.Mismatch>()) { match ->
                        ListRowCard(
                            title = match.transaction.description,
                            subtitle = "${DateUtils.formatDate(match.transaction.date)} · ${match.reason}" +
                                (match.expense?.let { " · uygulamada ${it.title} ${MoneyFormatter.format(it.amount)}" } ?: ""),
                            icon = Icons.Default.HelpOutline,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            trailing = {
                                Text(
                                    (if (match.transaction.isCredit) "+" else "") + MoneyFormatter.format(match.transaction.amount),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                    else -> items(result.missingFromStatement) { expense ->
                        ListRowCard(
                            title = expense.title,
                            subtitle = "${DateUtils.formatDate(expense.expenseDate)} · uygulamada var, ekstrede yok",
                            icon = Icons.Default.RemoveCircleOutline,
                            iconTint = MaterialTheme.colorScheme.error,
                            trailing = { Text(MoneyFormatter.format(expense.amount), style = MaterialTheme.typography.titleSmall) }
                        )
                    }
                }
            }
            if (unread.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Okunamadı", subtitle = "${unread.size} satır") }
                items(unread) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pickingCategoryFor?.let { index ->
        AppAlertDialog(
            scrollable = false,
            onDismissRequest = { pickingCategoryFor = null },
            title = { Text("Kategori") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(categories) { category ->
                        Text(
                            category.name,
                            modifier = Modifier.fillMaxWidth().clickable {
                                categoryOverrides = categoryOverrides + (index to category.id)
                                pickingCategoryFor = null
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingCategoryFor = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun ComparisonSummary(result: StatementComparison) {
    val period = result.matches.map { it.transaction.date }
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (period.isNotEmpty()) "${DateUtils.formatDate(period.min())} – ${DateUtils.formatDate(period.max())} · ${result.matches.size} işlem" else "İşlem yok",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "✓ ${result.matchedCount} eşleşti · ➕ ${result.newCount} yeni · ? ${result.mismatchCount} uyumsuz",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(label = "Uygulama", value = MoneyFormatter.format(result.appTotal), modifier = Modifier.weight(1f))
                MetricTile(label = "Ekstre", value = MoneyFormatter.format(result.statementTotal), modifier = Modifier.weight(1f))
                MetricTile(
                    label = "Fark",
                    value = MoneyFormatter.format(kotlin.math.abs(result.difference)),
                    modifier = Modifier.weight(1f),
                    valueColor = if (result.difference == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
