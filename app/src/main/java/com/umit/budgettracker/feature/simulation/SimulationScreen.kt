package com.umit.budgettracker.feature.simulation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.Scenario
import com.umit.budgettracker.core.domain.calculator.SimulationResult
import com.umit.budgettracker.core.domain.calculator.SimulationRules
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import com.umit.budgettracker.core.domain.repository.LoanRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import com.umit.budgettracker.core.domain.usecase.ApplyRaiseUseCase
import com.umit.budgettracker.core.ui.charts.ChartLegendRow
import com.umit.budgettracker.core.ui.charts.ChartSeries
import com.umit.budgettracker.core.ui.charts.LineComparisonChart
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class SimulationOptions(
    val subscriptions: List<Subscription> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val categories: List<Category> = emptyList(),
    val fixedExpenses: List<FixedExpense> = emptyList()
)

@HiltViewModel
class SimulationViewModel @Inject constructor(
    calculator: MonthlyBudgetCalculator,
    subscriptionRepository: SubscriptionRepository,
    loanRepository: LoanRepository,
    categoryRepository: CategoryRepository,
    fixedExpenseRepository: FixedExpenseRepository,
    private val applyRaise: ApplyRaiseUseCase
) : ViewModel() {
    val currentMonth: YearMonth = YearMonth.now()
    private val past = (3 downTo 1).map { currentMonth.minusMonths(it.toLong()) }
    private val future = (0 until 12).map { currentMonth.plusMonths(it.toLong()) }

    private val _scenarios = MutableStateFlow<List<Scenario>>(emptyList())
    val scenarios: StateFlow<List<Scenario>> = _scenarios.asStateFlow()

    val result: StateFlow<SimulationResult?> = combine(calculator.getSummariesForMonths(past + future), _scenarios) { summaries, scenarios ->
        SimulationRules.simulate(
            future = summaries.drop(past.size),
            past = summaries.take(past.size),
            currentMonth = currentMonth,
            scenarios = scenarios
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val options: StateFlow<SimulationOptions> = combine(
        subscriptionRepository.observeActiveSubscriptions(),
        loanRepository.observeActiveLoans(),
        categoryRepository.observeActiveCategories(),
        fixedExpenseRepository.observeActiveFixedExpenses()
    ) { subscriptions, loans, categories, fixed ->
        SimulationOptions(subscriptions, loans, categories, fixed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SimulationOptions())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(scenario: Scenario) {
        _scenarios.value = _scenarios.value + scenario
    }

    fun remove(scenario: Scenario) {
        _scenarios.value = _scenarios.value - scenario
    }

    /** Explicit, confirmed write: the only place this screen changes stored data. */
    fun applySalaryRaise(scenario: Scenario.SalaryChange) {
        viewModelScope.launch {
            val rule = applyRaise.salary(scenario.percent, scenario.fromMonth)
            _message.value = rule?.let { "${DateUtils.formatMonthYear(it.effectiveStartMonth)} itibarıyla maaş ${MoneyFormatter.format(it.amount)} olarak kaydedildi." }
                ?: "O ay için geçerli bir maaş kuralı yok."
        }
    }

    fun applyFixedRaise(scenario: Scenario.FixedExpenseRaise) {
        viewModelScope.launch {
            val expense = options.value.fixedExpenses.firstOrNull { it.id == scenario.fixedExpenseId } ?: return@launch
            val amount = applyRaise.fixedExpense(expense, scenario.percent, scenario.fromMonth)
            _message.value = "${expense.title}: ${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla ${MoneyFormatter.format(amount)}."
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun SimulationScreen(
    onBack: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsState()
    val scenarios by viewModel.scenarios.collectAsState()
    val options by viewModel.options.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var confirmApply by remember { mutableStateOf<Scenario?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Ya Şöyle Olsaydı?",
        subtitle = "12 aylık birikim senaryosu · kayıtlar değişmez",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Senaryo ekle") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sim = result
            item {
                HeroCard(
                    label = if (scenarios.isEmpty()) "12 ay sonunda tahmini birikim" else "12 ayda birikim farkı",
                    value = when {
                        sim == null -> "…"
                        scenarios.isEmpty() -> MoneyFormatter.format(sim.baselineTotal)
                        else -> ValueFormats.signedMoney(sim.totalDifference)
                    },
                    supporting = sim?.let {
                        if (scenarios.isEmpty()) "Senaryo ekleyerek kredi kapama, abonelik iptali, kategori kısma ya da zam etkisini gör."
                        else "Mevcut plan ${MoneyFormatter.format(it.baselineTotal)} → senaryo ${MoneyFormatter.format(it.scenarioTotal)}"
                    }
                )
            }
            if (scenarios.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Senaryolar") }
                items(scenarios) { scenario ->
                    ListRowCard(
                        title = scenario.label,
                        subtitle = describe(scenario),
                        icon = Icons.Default.Science,
                        trailing = {
                            Row {
                                if (scenario is Scenario.SalaryChange || scenario is Scenario.FixedExpenseRaise) {
                                    TextButton(onClick = { confirmApply = scenario }) { Text("Planla") }
                                }
                                IconButton(onClick = { viewModel.remove(scenario) }) { Icon(Icons.Default.Close, contentDescription = "Kaldır") }
                            }
                        }
                    )
                }
            }
            if (sim != null && sim.months.isNotEmpty()) {
                item {
                    FinanceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Kümülatif birikim", style = MaterialTheme.typography.titleSmall)
                            val primary = MaterialTheme.colorScheme.primary
                            // The baseline is the hero line until a scenario is added, then it steps back.
                            val muted = if (scenarios.isEmpty()) primary else MaterialTheme.colorScheme.outline
                            val series = buildList {
                                add(ChartSeries("Mevcut plan", muted))
                                if (scenarios.isNotEmpty()) add(ChartSeries("Senaryo", primary))
                            }
                            ChartLegendRow(series)
                            LineComparisonChart(
                                series = buildList {
                                    add(sim.months.map { it.baselineCumulative })
                                    if (scenarios.isNotEmpty()) add(sim.months.map { it.scenarioCumulative })
                                },
                                colors = listOf(muted, primary),
                                labels = sim.months.map { DateUtils.formatMonthYear(it.month).take(3) + " " + it.month.year % 100 },
                                selectedIndex = selectedIndex,
                                onSelect = { selectedIndex = it }
                            )
                            selectedIndex?.let { index ->
                                val month = sim.months[index]
                                Text(
                                    "${DateUtils.formatMonthYear(month.month)}: mevcut ${MoneyFormatter.format(month.baselineCumulative)}" +
                                        if (scenarios.isNotEmpty()) " · senaryo ${MoneyFormatter.format(month.scenarioCumulative)}" else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Gelecek aylarda elle girilen harcamalar son 3 ay ortalaması (${MoneyFormatter.format(sim.assumedVariableSpending)}) kabul edildi; maaş kuralları, abonelikler, krediler, sabit giderler ve taksitler kayıtlardan gelir.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { FinanceSectionHeader(title = "Aylık döküm") }
                item {
                    FinanceCard {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            TableRow("Ay", "Mevcut", if (scenarios.isEmpty()) "" else "Senaryo", header = true)
                            sim.months.forEach { month ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                TableRow(
                                    DateUtils.formatMonthYear(month.month),
                                    MoneyFormatter.format(month.baselineNet),
                                    if (scenarios.isEmpty()) "" else MoneyFormatter.format(month.scenarioNet),
                                    negativeSecond = month.baselineNet < 0,
                                    negativeThird = month.scenarioNet < 0
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        ScenarioDialog(
            options = options,
            currentMonth = viewModel.currentMonth,
            onDismiss = { adding = false },
            onAdd = {
                viewModel.add(it)
                adding = false
            }
        )
    }
    confirmApply?.let { scenario ->
        ConfirmDialog(
            title = "Zammı planla",
            message = when (scenario) {
                is Scenario.SalaryChange -> "${DateUtils.formatMonthYear(scenario.fromMonth)} ayından başlayan yeni bir maaş kuralı oluşturulacak. Geçmiş aylar değişmez."
                is Scenario.FixedExpenseRaise -> "${scenario.title} ${DateUtils.formatMonthYear(scenario.fromMonth.minusMonths(1))} sonunda biter, ${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla %${scenario.percent} artmış tutarla yeni kayıt başlar. Geçmiş ödemeler değişmez."
                else -> ""
            },
            confirmLabel = "Kaydet",
            onConfirm = {
                when (scenario) {
                    is Scenario.SalaryChange -> viewModel.applySalaryRaise(scenario)
                    is Scenario.FixedExpenseRaise -> viewModel.applyFixedRaise(scenario)
                    else -> Unit
                }
                viewModel.remove(scenario)
                confirmApply = null
            },
            onDismiss = { confirmApply = null }
        )
    }
}

private fun describe(scenario: Scenario): String = when (scenario) {
    is Scenario.CancelSubscription -> "${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla"
    is Scenario.CloseLoanEarly -> "${DateUtils.formatMonthYear(scenario.month)} · " +
        (scenario.payoffAmount?.let { "kapama ${MoneyFormatter.format(it)}" } ?: "kalan taksitler tek seferde")
    is Scenario.CutCategory -> "Son 3 ay ortalamasına göre her ay"
    is Scenario.SalaryChange -> "${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla"
    is Scenario.FixedExpenseRaise -> "${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla"
    is Scenario.ExtraMonthly -> "${MoneyFormatter.format(kotlin.math.abs(scenario.amount))}/ay · ${DateUtils.formatMonthYear(scenario.fromMonth)} itibarıyla"
}

@Composable
private fun TableRow(first: String, second: String, third: String, header: Boolean = false, negativeSecond: Boolean = false, negativeThird: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(first, modifier = Modifier.weight(1.2f), style = style, color = if (header) muted else MaterialTheme.colorScheme.onSurface)
        Text(second, modifier = Modifier.weight(1f), style = style, textAlign = TextAlign.End, color = if (header) muted else if (negativeSecond) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(third, modifier = Modifier.weight(1f), style = style, textAlign = TextAlign.End, color = if (header) muted else if (negativeThird) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

private enum class ScenarioKind(val label: String) {
    CANCEL_SUB("Aboneliği iptal et"),
    CLOSE_LOAN("Krediyi erken kapat"),
    CUT_CATEGORY("Kategoriyi azalt"),
    SALARY("Maaş zammı"),
    FIXED_RAISE("Kira / sabit gider zammı"),
    EXTRA("Yeni aylık gider / gelir")
}

@Composable
private fun ScenarioDialog(
    options: SimulationOptions,
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onAdd: (Scenario) -> Unit
) {
    var kind by remember { mutableStateOf(ScenarioKind.CANCEL_SUB) }
    var subscription by remember { mutableStateOf(options.subscriptions.firstOrNull()) }
    var loan by remember { mutableStateOf(options.loans.firstOrNull()) }
    var category by remember { mutableStateOf(options.categories.firstOrNull()) }
    var fixed by remember { mutableStateOf(options.fixedExpenses.firstOrNull()) }
    var month by remember { mutableStateOf<YearMonth?>(currentMonth.plusMonths(1)) }
    var percentText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    val percent = percentText.toIntOrNull()
    val amount = MoneyInput.parse(amountText)

    val scenario: Scenario? = when (kind) {
        ScenarioKind.CANCEL_SUB -> subscription?.let { s -> month?.let { Scenario.CancelSubscription(s.id, s.title, it) } }
        ScenarioKind.CLOSE_LOAN -> loan?.let { l -> month?.let { Scenario.CloseLoanEarly(l.id, l.title, it, amount?.takeIf { a -> a > 0L }) } }
        ScenarioKind.CUT_CATEGORY -> category?.let { c -> percent?.takeIf { it in 1..100 }?.let { Scenario.CutCategory(c.id, c.name, it) } }
        ScenarioKind.SALARY -> percent?.takeIf { it in -90..500 }?.let { p -> month?.let { Scenario.SalaryChange(p, it) } }
        ScenarioKind.FIXED_RAISE -> fixed?.let { f -> percent?.takeIf { it in 1..500 }?.let { p -> month?.let { Scenario.FixedExpenseRaise(f.id, f.title, p, it) } } }
        ScenarioKind.EXTRA -> amount?.takeIf { it > 0L }?.let { a ->
            month?.let { Scenario.ExtraMonthly(titleText.ifBlank { if (isIncome) "Yeni gelir" else "Yeni gider" }, if (isIncome) -a else a, it) }
        }
    }

    FormDialog(title = "Senaryo ekle", onDismiss = onDismiss, confirmLabel = "Ekle", confirmEnabled = scenario != null, onConfirm = { scenario?.let(onAdd) }) {
        DropdownField(label = "Senaryo", options = ScenarioKind.entries, selected = kind, onSelect = { kind = it }, optionLabel = { it.label })
        when (kind) {
            ScenarioKind.CANCEL_SUB -> {
                if (options.subscriptions.isEmpty()) Text("Aktif abonelik yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else DropdownField(label = "Abonelik", options = options.subscriptions, selected = subscription, onSelect = { subscription = it }, optionLabel = { it.title })
                MonthField(label = "İptal ayı", value = month, onChange = { month = it })
            }
            ScenarioKind.CLOSE_LOAN -> {
                if (options.loans.isEmpty()) Text("Aktif kredi yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else DropdownField(label = "Kredi", options = options.loans, selected = loan, onSelect = { loan = it }, optionLabel = { it.title })
                MonthField(label = "Kapama ayı", value = month, onChange = { month = it })
                AmountField(value = amountText, onValueChange = { amountText = it }, label = "Kapama tutarı (boşsa kalan taksitler)", supportingText = "Bankanın verdiği erken kapama tutarını yazabilirsin")
            }
            ScenarioKind.CUT_CATEGORY -> {
                DropdownField(label = "Kategori", options = options.categories, selected = category, onSelect = { category = it }, optionLabel = { it.name })
                PercentInput(percentText, { percentText = it }, "Azaltma oranı")
            }
            ScenarioKind.SALARY -> {
                PercentInput(percentText, { percentText = it }, "Zam oranı (eksi değer = azalış)")
                MonthField(label = "Başlangıç ayı", value = month, onChange = { month = it })
            }
            ScenarioKind.FIXED_RAISE -> {
                if (options.fixedExpenses.isEmpty()) Text("Aktif sabit gider yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else DropdownField(label = "Sabit gider", options = options.fixedExpenses, selected = fixed, onSelect = { fixed = it }, optionLabel = { "${it.title} · ${MoneyFormatter.format(it.amount)}" })
                PercentInput(percentText, { percentText = it }, "Zam oranı")
                MonthField(label = "Zam ayı", value = month, onChange = { month = it })
            }
            ScenarioKind.EXTRA -> {
                SegmentedChoice(options = listOf(false, true), selected = isIncome, onSelect = { isIncome = it }, label = { if (it) "Gelir" else "Gider" })
                OutlinedTextField(value = titleText, onValueChange = { titleText = it }, label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AmountField(value = amountText, onValueChange = { amountText = it }, label = "Aylık tutar")
                MonthField(label = "Başlangıç ayı", value = month, onChange = { month = it })
            }
        }
    }
}

@Composable
private fun PercentInput(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '-' }.take(4)) },
        label = { Text(label) },
        prefix = { Text("%") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
