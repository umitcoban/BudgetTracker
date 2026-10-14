package com.umit.budgettracker.feature.yearend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CategoryTrendRules
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.YearEndReport
import com.umit.budgettracker.core.domain.usecase.YearEndReportUseCase
import com.umit.budgettracker.core.export.PdfExportService
import com.umit.budgettracker.core.export.PdfReportData
import com.umit.budgettracker.core.ui.charts.ChartSeries
import com.umit.budgettracker.core.ui.charts.StackedBarChart
import com.umit.budgettracker.core.ui.charts.StackedBarGroup
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearEndViewModel @Inject constructor(
    private val reportUseCase: YearEndReportUseCase,
    private val calculator: MonthlyBudgetCalculator,
    private val pdfExportService: PdfExportService
) : ViewModel() {
    private val _year = MutableStateFlow(YearMonth.now().year)
    val year: StateFlow<Int> = _year.asStateFlow()

    val report: StateFlow<YearEndReport?> = _year
        .flatMapLatest { reportUseCase.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun previousYear() { _year.value -= 1 }
    fun nextYear() { if (_year.value < YearMonth.now().year) _year.value += 1 }

    fun exportPdf(uri: Uri) {
        viewModelScope.launch {
            val report = report.value ?: return@launch
            val lastMonth = report.lastMonth ?: YearMonth.of(report.year, 12)
            val months = (PdfReportData.HISTORY_MONTH_COUNT - 1 downTo 0).map { lastMonth.minusMonths(it.toLong()) }
            val history = calculator.getSummariesForMonths(months).first()
            val data = PdfReportData(history.last(), history, CategoryTrendRules.buildTrends(history), yearEnd = report)
            _message.value = if (pdfExportService.exportReportToPdf(uri, data)) "PDF raporu oluşturuldu." else "PDF raporu oluşturulamadı."
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun YearEndScreen(
    onBack: () -> Unit,
    viewModel: YearEndViewModel = hiltViewModel()
) {
    val year by viewModel.year.collectAsState()
    val report by viewModel.report.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selectedBar by remember(report) { mutableStateOf<Int?>(null) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(viewModel::exportPdf)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Yıl Sonu Raporu",
        subtitle = report?.lastMonth?.let { "Ocak – ${DateUtils.formatMonthYear(it)}" },
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = { pdfLauncher.launch("budgettracker_yil_$year.pdf") }, enabled = report != null) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF olarak kaydet")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previousYear) { Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki yıl") }
                        Text("$year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = viewModel::nextYear, enabled = year < YearMonth.now().year) { Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki yıl") }
                    }
                }
            }
            val r = report
            if (r == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                return@LazyColumn
            }
            item {
                HeroCard(
                    label = "Toplam harcama",
                    value = MoneyFormatter.format(r.totalExpense),
                    supporting = buildString {
                        append("Gelir ${MoneyFormatter.format(r.totalIncome)}")
                        if (r.totalIncome > 0L) append(" · gelirin %${r.totalExpense * 100L / r.totalIncome}'i harcandı")
                    },
                    footer = {
                        HeroMetricRow(
                            listOf(
                                "Plan sonrası kalan" to MoneyFormatter.format(r.totalLeftover),
                                "Birikim hedefi" to MoneyFormatter.format(r.totalSavingGoal),
                                "Abonelik" to MoneyFormatter.format(r.subscriptionTotal)
                            )
                        )
                    }
                )
            }
            if (r.months.any { it.totalExpenseAmount > 0L || it.totalIncomeAmount > 0L }) {
                item {
                    FinanceCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Aylık harcama", style = MaterialTheme.typography.titleSmall)
                            val primary = MaterialTheme.colorScheme.primary
                            val card = MaterialTheme.colorScheme.tertiary
                            StackedBarChart(
                                groups = r.months.map { StackedBarGroup(DateUtils.formatMonthYear(it.yearMonth).take(3), listOf(it.directExpenseAmount, it.creditCardPaymentAmount)) },
                                series = listOf(ChartSeries("Nakit / banka", primary), ChartSeries("Kredi kartı", card)),
                                selectedIndex = selectedBar,
                                onSelect = { selectedBar = it }
                            )
                            val selected = selectedBar?.let { r.months.getOrNull(it) }
                            Text(
                                selected?.let { "${DateUtils.formatMonthYear(it.yearMonth)}: ${MoneyFormatter.format(it.totalExpenseAmount)} harcama, ${MoneyFormatter.format(it.totalIncomeAmount)} gelir" }
                                    ?: "Bir ayın değerlerini görmek için çubuğa dokun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        label = "En pahalı ay",
                        value = r.mostExpensiveMonth?.let { DateUtils.formatMonthYear(it.yearMonth) } ?: "—",
                        supportingText = r.mostExpensiveMonth?.let { MoneyFormatter.format(it.totalExpenseAmount) },
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "En az harcanan ay",
                        value = r.cheapestMonth?.let { DateUtils.formatMonthYear(it.yearMonth) } ?: "—",
                        supportingText = r.cheapestMonth?.let { MoneyFormatter.format(it.totalExpenseAmount) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                FinanceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Geçen yılın aynı dönemine göre", style = MaterialTheme.typography.titleSmall)
                        ComparisonLine("Harcama", r.previousExpense, r.totalExpense, r.expenseChangePercent, r.realExpenseChangeBp, higherIsBetter = false)
                        ComparisonLine("Gelir", r.previousIncome, r.totalIncome, r.incomeChangePercent, r.realIncomeChangeBp, higherIsBetter = true)
                        Text(
                            r.inflationBp?.let { "Aynı dönemde fiyatlar ${ValueFormats.signedPercent(it).removePrefix("+")} arttı" + if (r.inflationEstimated) " (tahmini)." else "." }
                                ?: "Enflasyon verisi eksik; reel karşılaştırma için Enflasyon ekranından oranları gir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (r.feeCount > 0) {
                item {
                    ListRowCard(
                        title = "Faiz, komisyon ve ücretler",
                        subtitle = "${r.feeCount} kayıt (başlıkta faiz/komisyon/ücret/BSMV/KKDF geçenler)",
                        icon = Icons.Default.AccountBalance,
                        iconTint = MaterialTheme.colorScheme.error,
                        trailing = { Text(MoneyFormatter.format(r.feeTotal), style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }
            if (r.topCategories.isNotEmpty()) {
                item { FinanceSectionHeader(title = "En çok harcanan kategoriler") }
                items(r.topCategories.take(8)) { category ->
                    val share = if (r.totalExpense > 0L) category.amount.toFloat() / r.totalExpense else 0f
                    FinanceCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row {
                                Text(category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.format(category.amount), style = MaterialTheme.typography.titleSmall)
                            }
                            LabeledProgress(progress = share, color = Color(category.colorValue), startLabel = "%${(category.amount * 100L / r.totalExpense.coerceAtLeast(1L))}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonLine(label: String, previous: Long, current: Long, nominalPercent: Int?, realBp: Int?, higherIsBetter: Boolean) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${MoneyFormatter.format(previous)} → ${MoneyFormatter.format(current)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(nominalPercent?.let { (if (it > 0) "+" else "") + "%$it nominal" } ?: "—", style = MaterialTheme.typography.labelMedium)
            realBp?.let {
                val good = if (higherIsBetter) it >= 0 else it <= 0
                Text(
                    "${ValueFormats.signedPercent(it)} reel",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
