package com.umit.budgettracker.feature.insights

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
import com.umit.budgettracker.core.domain.calculator.RecurringExpenseDetector
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.domain.usecase.RecordExpenseUseCase
import com.umit.budgettracker.core.parsing.StatementMatcher
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class InsightsUiState(
    val suggestions: List<RecurringSuggestion> = emptyList(),
    val drafts: List<ExpenseDraft> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<PaymentAccount> = emptyList(),
    val merchantRules: List<MerchantRule> = emptyList(),
    val dismissedCount: Int = 0
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val assistantRepository: AssistantRepository,
    categoryRepository: CategoryRepository,
    accountRepository: PaymentAccountRepository,
    private val recordExpense: RecordExpenseUseCase
) : ViewModel() {
    private val currentMonth = YearMonth.now()

    val uiState: StateFlow<InsightsUiState> = combine(
        combine(
            expenseRepository.observeExpensesForDateRange(
                currentMonth.minusMonths(RecurringExpenseDetector.LOOKBACK_MONTHS - 1).atDay(1),
                currentMonth.atEndOfMonth()
            ),
            subscriptionRepository.observeAllSubscriptions(),
            fixedExpenseRepository.observeAllFixedExpenses(),
            assistantRepository.observeDismissedSuggestionKeys()
        ) { expenses, subscriptions, fixed, dismissed ->
            RecurringExpenseDetector.detect(expenses, subscriptions, fixed, dismissed, currentMonth) to dismissed.size
        },
        assistantRepository.observeDrafts(),
        categoryRepository.observeActiveCategories(),
        accountRepository.observeActiveAccounts(),
        assistantRepository.observeMerchantRules()
    ) { (suggestions, dismissedCount), drafts, categories, accounts, rules ->
        InsightsUiState(suggestions, drafts, categories, accounts, rules, dismissedCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismiss(suggestion: RecurringSuggestion) = viewModelScope.launch {
        assistantRepository.dismissSuggestion(suggestion.key)
        _message.value = "\"${suggestion.title}\" bir daha önerilmeyecek."
    }

    fun restoreDismissed() = viewModelScope.launch {
        assistantRepository.restoreSuggestions()
        _message.value = "Reddedilen öneriler geri getirildi."
    }

    fun createFixedExpense(title: String, amount: Long, day: Int, startMonth: YearMonth, categoryId: Long, accountId: Long, key: String) =
        viewModelScope.launch {
            fixedExpenseRepository.upsertFixedExpense(
                FixedExpense(
                    id = 0,
                    title = title,
                    amount = amount,
                    dayOfMonth = day,
                    startMonth = startMonth,
                    endMonth = null,
                    categoryId = categoryId,
                    paymentAccountId = accountId,
                    note = "Tekrarlayan harcama önerisinden",
                    isActive = true
                )
            )
            assistantRepository.dismissSuggestion(key)
            _message.value = "$title sabit gider olarak eklendi (${DateUtils.formatMonthYear(startMonth)} itibarıyla)."
        }

    fun createSubscription(title: String, amount: Long, day: Int, startMonth: YearMonth, categoryId: Long, accountId: Long, key: String) =
        viewModelScope.launch {
            subscriptionRepository.createSubscriptionWithPrice(
                Subscription(
                    id = 0,
                    title = title,
                    categoryId = categoryId,
                    paymentAccountId = accountId,
                    billingDay = day,
                    isActive = true,
                    note = "Tekrarlayan harcama önerisinden"
                ),
                amount,
                startMonth
            )
            assistantRepository.dismissSuggestion(key)
            _message.value = "$title abonelik olarak eklendi (${DateUtils.formatMonthYear(startMonth)} itibarıyla)."
        }

    suspend fun duplicateFor(draft: ExpenseDraft): Expense? = recordExpense.findDuplicate(draft.date, draft.amount, draft.title)

    fun approveDraft(draft: ExpenseDraft, title: String, amount: Long, date: LocalDate, categoryId: Long, accountId: Long) =
        viewModelScope.launch {
            recordExpense(title, amount, date, categoryId, accountId, note = "Bildirimden (${draft.sourceLabel})")
            assistantRepository.deleteDraft(draft.id)
            _message.value = "$title harcamalara eklendi."
        }

    fun discardDraft(draft: ExpenseDraft) = viewModelScope.launch { assistantRepository.deleteDraft(draft.id) }

    fun deleteRule(rule: MerchantRule) = viewModelScope.launch { assistantRepository.deleteMerchantRule(rule.id) }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var accepting by remember { mutableStateOf<RecurringSuggestion?>(null) }
    var approving by remember { mutableStateOf<ExpenseDraft?>(null) }
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Öneriler ve Taslaklar",
        subtitle = "Hiçbir şey onayın olmadan kaydedilmez",
        onBack = onBack,
        snackbarHostState = snackbar
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FinanceSectionHeader(
                    title = "Bildirim taslakları",
                    subtitle = if (state.drafts.isEmpty()) "Bekleyen taslak yok" else "${state.drafts.size} harcama onay bekliyor"
                )
            }
            items(state.drafts, key = { "draft_${it.id}" }) { draft ->
                ListRowCard(
                    title = draft.title,
                    subtitle = "${DateUtils.formatDayMonth(draft.date)} · ${draft.sourceLabel}",
                    icon = Icons.Default.MarkEmailUnread,
                    onClick = { approving = draft },
                    trailing = { Text(MoneyFormatter.format(draft.amount), style = MaterialTheme.typography.titleSmall) },
                    below = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.discardDraft(draft) }) { Text("Yok say") }
                            FilledTonalButton(onClick = { approving = draft }) { Text("İncele ve ekle") }
                        }
                    }
                )
            }

            item {
                FinanceSectionHeader(
                    title = "Tekrarlayan harcamalar",
                    subtitle = "En az ${RecurringExpenseDetector.MIN_MONTHS} ay benzer tutarla elle girilenler",
                    action = if (state.dismissedCount > 0) {
                        { TextButton(onClick = { viewModel.restoreDismissed() }) { Text("Reddedilenler (${state.dismissedCount})") } }
                    } else null
                )
            }
            if (state.suggestions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AutoAwesome,
                        title = "Şimdilik öneri yok",
                        message = "Her ay elle girdiğin benzer harcamalar burada \"abonelik / sabit gider yap\" önerisine dönüşür."
                    )
                }
            }
            items(state.suggestions, key = { it.key }) { suggestion ->
                val category = state.categories.firstOrNull { it.id == suggestion.categoryId }
                ListRowCard(
                    title = suggestion.title,
                    subtitle = "${suggestion.monthCount} aydır · ayın ~${suggestion.typicalDay}'i" + (category?.let { " · ${it.name}" } ?: ""),
                    icon = Icons.Default.Repeat,
                    trailing = { Text(MoneyFormatter.format(suggestion.typicalAmount), style = MaterialTheme.typography.titleSmall) },
                    below = {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.dismiss(suggestion) }) { Text("Önerme") }
                            FilledTonalButton(onClick = { accepting = suggestion }) { Text("Düzenli yap") }
                        }
                    }
                )
            }

            item {
                FinanceSectionHeader(
                    title = "Öğrenilen işyeri kuralları",
                    subtitle = "${state.merchantRules.size} kural · fiş ve ekstrede kategori önerir",
                    action = { TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "Gizle" else "Göster") } }
                )
            }
            if (showRules) {
                items(state.merchantRules, key = { "rule_${it.id}" }) { rule ->
                    ListRowCard(
                        title = rule.pattern,
                        subtitle = "→ ${state.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "Silinmiş kategori"} · ${rule.hitCount} kez",
                        trailing = { IconButton(onClick = { viewModel.deleteRule(rule) }) { Icon(Icons.Default.Delete, contentDescription = "Kuralı sil") } }
                    )
                }
            }
        }
    }

    accepting?.let { suggestion ->
        AcceptSuggestionDialog(
            suggestion = suggestion,
            categories = state.categories,
            accounts = state.accounts,
            onDismiss = { accepting = null },
            onConfirm = { asSubscription, title, amount, day, month, categoryId, accountId ->
                if (asSubscription) viewModel.createSubscription(title, amount, day, month, categoryId, accountId, suggestion.key)
                else viewModel.createFixedExpense(title, amount, day, month, categoryId, accountId, suggestion.key)
                accepting = null
            }
        )
    }
    approving?.let { draft ->
        var duplicate by remember(draft) { mutableStateOf<Expense?>(null) }
        LaunchedEffect(draft) { duplicate = viewModel.duplicateFor(draft) }
        ExpenseConfirmDialog(
            title = "Taslağı onayla",
            initialTitle = draft.title,
            initialAmount = draft.amount,
            initialDate = draft.date,
            suggestedCategoryId = StatementMatcher.suggestCategory(draft.title, state.merchantRules),
            categories = state.categories,
            accounts = state.accounts,
            warning = duplicate?.let { "Aynı gün aynı tutarda \"${it.title}\" zaten kayıtlı. Mükerrer olabilir." },
            onDismiss = { approving = null },
            onConfirm = { title, amount, date, categoryId, accountId ->
                viewModel.approveDraft(draft, title, amount, date, categoryId, accountId)
                approving = null
            }
        )
    }
}

@Composable
private fun AcceptSuggestionDialog(
    suggestion: RecurringSuggestion,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String, Long, Int, YearMonth, Long, Long) -> Unit
) {
    var asSubscription by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(suggestion.title) }
    var amountText by remember { mutableStateOf(MoneyInput.format(suggestion.typicalAmount)) }
    var dayText by remember { mutableStateOf(suggestion.typicalDay.toString()) }
    var month by remember { mutableStateOf<YearMonth?>(YearMonth.now().plusMonths(1)) }
    var category by remember { mutableStateOf(categories.firstOrNull { it.id == suggestion.categoryId } ?: categories.firstOrNull()) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == suggestion.paymentAccountId } ?: accounts.firstOrNull()) }
    val amount = MoneyInput.parse(amountText)
    val day = dayText.toIntOrNull()
    FormDialog(
        title = "Düzenli ödemeye çevir",
        onDismiss = onDismiss,
        confirmEnabled = title.isNotBlank() && amount != null && amount > 0L && day in 1..31 && month != null && category != null && account != null,
        onConfirm = { onConfirm(asSubscription, title.trim(), amount!!, day!!, month!!, category!!.id, account!!.id) }
    ) {
        SegmentedChoice(options = listOf(false, true), selected = asSubscription, onSelect = { asSubscription = it }, label = { if (it) "Abonelik" else "Sabit gider" })
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Aylık tutar")
        OutlinedTextField(value = dayText, onValueChange = { dayText = it.filter(Char::isDigit).take(2) }, label = { Text("Ayın günü") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MonthField(label = "Başlangıç ayı", value = month, onChange = { month = it })
        DropdownField(label = "Kategori", options = categories, selected = category, onSelect = { category = it }, optionLabel = { it.name })
        DropdownField(label = "Ödeme hesabı", options = accounts, selected = account, onSelect = { account = it }, optionLabel = { it.name })
        Text(
            "Geçmiş aylardaki elle girilmiş harcamalara dokunulmaz. Çift kayıt olmaması için başlangıç varsayılan olarak gelecek aydır.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Reusable "confirm one expense" form: title, amount, date, category, account. */
@Composable
fun ExpenseConfirmDialog(
    title: String,
    initialTitle: String,
    initialAmount: Long?,
    initialDate: LocalDate?,
    suggestedCategoryId: Long?,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    warning: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, LocalDate, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialTitle) }
    var amountText by remember { mutableStateOf(MoneyInput.format(initialAmount)) }
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    var category by remember(categories, suggestedCategoryId) {
        mutableStateOf(categories.firstOrNull { it.id == suggestedCategoryId } ?: categories.firstOrNull { it.name == "Diğer" } ?: categories.firstOrNull())
    }
    var account by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }
    val amount = MoneyInput.parse(amountText)
    FormDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = "Harcamaya ekle",
        confirmEnabled = name.isNotBlank() && amount != null && amount > 0L && category != null && account != null,
        onConfirm = { onConfirm(name.trim(), amount!!, date, category!!.id, account!!.id) }
    ) {
        warning?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Başlık / işyeri") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Tutar")
        DateField(label = "Tarih", value = date, onChange = { date = it })
        DropdownField(
            label = "Kategori" + if (suggestedCategoryId != null && category?.id == suggestedCategoryId) " (öğrenilen)" else "",
            options = categories,
            selected = category,
            onSelect = { category = it },
            optionLabel = { it.name }
        )
        DropdownField(label = "Ödeme hesabı", options = accounts, selected = account, onSelect = { account = it }, optionLabel = { it.name })
    }
}
