package com.umit.budgettracker.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.MetricTile
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import kotlin.math.absoluteValue

private val reportTabs = listOf("Genel", "Harcamalar", "Nakit Akışı")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Raporlar", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Rakamların ne anlattığını gör",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            ReportsUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ReportsUiState.Success -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        divider = {}
                    ) {
                        reportTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> OverviewReport(state)
                        1 -> ExpenseReport(state.currentMonth, state.previousMonth)
                        else -> CashFlowReport(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewReport(state: ReportsUiState.Success) {
    val current = state.currentMonth
    val previous = state.previousMonth
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MonthlyResultHero(current, previous) }
        item { RatioMetrics(current) }
        item {
            FinanceSectionHeader(
                title = "6 aylık görünüm",
                subtitle = "Gelir ve harcama eğilimi"
            )
        }
        item { TrendChartCard(state.trend) }
        item {
            FinanceSectionHeader(
                title = "Öne çıkanlar",
                subtitle = "Geçen aya göre önemli değişimler"
            )
        }
        item { DecisionSummaryCard(current, previous) }
        if (state.netWorth != null) {
            item {
                FinanceSectionHeader(
                    title = "Net varlık",
                    subtitle = "Varlıkların ve borçlarının net değeri"
                )
            }
            item { NetWorthCard(state.netWorth.netWorth) }
        }
    }
}

@Composable
private fun ExpenseReport(
    current: MonthlyBudgetSummary,
    previous: MonthlyBudgetSummary
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExpenseSummaryHeader(current, previous)
        }
        item {
            FinanceSectionHeader(
                title = "Kategori dağılımı",
                subtitle = "Toplam harcamadaki pay ve bütçe durumu"
            )
        }
        if (current.categorySummaries.isEmpty()) {
            item {
                FinanceCard {
                    Text(
                        "Bu ay kategori raporu oluşturacak harcama bulunmuyor.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(current.categorySummaries) { category ->
                CategoryReportRow(category, current.totalExpenseAmount)
            }
        }
        item {
            FinanceSectionHeader(
                title = "Ödeme kanalları",
                subtitle = "Harcamaların hangi kaynaktan geldiği"
            )
        }
        item { SpendingChannelsCard(current) }
    }
}

@Composable
private fun CashFlowReport(state: ReportsUiState.Success) {
    val current = state.currentMonth
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FinanceSectionHeader(
                title = "Aylık akış",
                subtitle = "Gelirden plan sonrası kalana"
            )
        }
        item { CashFlowWaterfall(current) }
        item {
            FinanceSectionHeader(
                title = "Aylara göre kalan",
                subtitle = "Birikim ve sabit ödemeler sonrasında"
            )
        }
        item { RemainingTrendCard(state.trend) }
    }
}

@Composable
private fun MonthlyResultHero(
    current: MonthlyBudgetSummary,
    previous: MonthlyBudgetSummary
) {
    val result = current.remainingAfterSavingAndFixedPayments
    val diff = result - previous.remainingAfterSavingAndFixedPayments
    FinanceCard(
        containerColor = if (result >= 0L) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Ayın net sonucu",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                MoneyFormatter.format(result),
                style = MaterialTheme.typography.headlineLarge,
                color = if (result >= 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (diff >= 0L) {
                        Icons.AutoMirrored.Filled.TrendingUp
                    } else {
                        Icons.AutoMirrored.Filled.TrendingDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (diff >= 0L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (diff >= 0L) {
                        "Önceki aya göre ${MoneyFormatter.format(diff)} daha iyi"
                    } else {
                        "Önceki aya göre ${MoneyFormatter.format(diff.absoluteValue)} daha düşük"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RatioMetrics(summary: MonthlyBudgetSummary) {
    val income = summary.totalIncomeAmount
    val savingRate = if (income > 0L) summary.savingGoalAmount * 100 / income else 0L
    val expenseRate = if (income > 0L) summary.totalExpenseAmount * 100 / income else 0L
    val fixedRate = if (income > 0L) summary.projectedFixedPaymentsAmount * 100 / income else 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "Tasarruf oranı",
                value = "%$savingRate",
                supportingText = MoneyFormatter.format(summary.savingGoalAmount),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Harcama oranı",
                value = "%$expenseRate",
                supportingText = MoneyFormatter.format(summary.totalExpenseAmount),
                modifier = Modifier.weight(1f),
                valueColor = if (expenseRate > 80L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        MetricTile(
            label = "Sabit ödeme yükü",
            value = "%$fixedRate",
            supportingText = "${MoneyFormatter.format(summary.projectedFixedPaymentsAmount)} planlı ödeme",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TrendChartCard(trend: List<MonthlyTrendPoint>) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val maxAmount = trend.maxOfOrNull { maxOf(it.incomeAmount, it.expenseAmount) }
        ?.coerceAtLeast(1L) ?: 1L

    FinanceCard {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChartLegend("Gelir", incomeColor)
                ChartLegend("Harcama", expenseColor)
            }
            Spacer(Modifier.height(18.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                if (trend.size < 2) return@Canvas
                val stepX = size.width / (trend.size - 1)
                val usableHeight = size.height - 12.dp.toPx()

                fun buildPath(amount: (MonthlyTrendPoint) -> Long): Path {
                    val path = Path()
                    trend.forEachIndexed { index, point ->
                        val x = stepX * index
                        val y = usableHeight -
                            (amount(point).toFloat() / maxAmount.toFloat() * usableHeight)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }

                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, usableHeight),
                    end = Offset(size.width, usableHeight),
                    strokeWidth = 1.dp.toPx()
                )
                drawPath(
                    buildPath { it.incomeAmount },
                    color = incomeColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    buildPath { it.expenseAmount },
                    color = expenseColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                trend.forEachIndexed { index, point ->
                    val x = stepX * index
                    val incomeY = usableHeight -
                        (point.incomeAmount.toFloat() / maxAmount.toFloat() * usableHeight)
                    val expenseY = usableHeight -
                        (point.expenseAmount.toFloat() / maxAmount.toFloat() * usableHeight)
                    drawCircle(incomeColor, 4.dp.toPx(), Offset(x, incomeY))
                    drawCircle(expenseColor, 4.dp.toPx(), Offset(x, expenseY))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                trend.forEach {
                    Text(
                        DateUtils.formatMonthYear(it.month).take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ExpenseSummaryHeader(
    current: MonthlyBudgetSummary,
    previous: MonthlyBudgetSummary
) {
    val diff = current.totalExpenseAmount - previous.totalExpenseAmount
    FinanceCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Bu ay toplam harcama",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                MoneyFormatter.format(current.totalExpenseAmount),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                when {
                    previous.totalExpenseAmount == 0L -> "Önceki ay karşılaştırması bulunmuyor"
                    diff > 0L -> "Geçen aya göre ${MoneyFormatter.format(diff)} artış"
                    else -> "Geçen aya göre ${MoneyFormatter.format(diff.absoluteValue)} azalış"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (diff > 0L) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DecisionSummaryCard(
    current: MonthlyBudgetSummary,
    previous: MonthlyBudgetSummary
) {
    val previousCategories = previous.categorySummaries.associateBy { it.categoryId }
    val topChange = current.categorySummaries
        .map { category ->
            category to (category.amount - (previousCategories[category.categoryId]?.amount ?: 0L))
        }
        .maxByOrNull { it.second }
    val result = current.remainingAfterSavingAndFixedPayments

    FinanceCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InsightRow(
                title = if (result >= 0L) "Plan sürdürülebilir görünüyor" else "Aylık plan açık veriyor",
                text = if (result >= 0L) {
                    "Birikim ve planlı ödemelerden sonra ${MoneyFormatter.format(result)} kalıyor."
                } else {
                    "Planlanan tüm kalemlerden sonra ${MoneyFormatter.format(result.absoluteValue)} açık oluşuyor."
                },
                positive = result >= 0L
            )
            if (topChange != null && topChange.second != 0L) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InsightRow(
                    title = "${topChange.first.categoryName} öne çıkıyor",
                    text = if (topChange.second > 0L) {
                        "Geçen aya göre ${MoneyFormatter.format(topChange.second)} daha fazla harcandı."
                    } else {
                        "Geçen aya göre ${MoneyFormatter.format(topChange.second.absoluteValue)} daha az harcandı."
                    },
                    positive = topChange.second <= 0L
                )
            }
        }
    }
}

@Composable
private fun InsightRow(title: String, text: String, positive: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Lightbulb,
            contentDescription = null,
            tint = if (positive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NetWorthCard(amount: Long) {
    FinanceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoGraph,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                MoneyFormatter.format(amount),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun SpendingChannelsCard(summary: MonthlyBudgetSummary) {
    val total = summary.totalExpenseAmount.coerceAtLeast(1L)
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelBar(
                "Nakit / banka",
                summary.directExpenseAmount,
                summary.directExpenseAmount.toFloat() / total
            )
            ChannelBar(
                "Kredi kartı ekstresi",
                summary.creditCardPaymentAmount,
                summary.creditCardPaymentAmount.toFloat() / total
            )
        }
    }
}

@Composable
private fun ChannelBar(label: String, amount: Long, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.titleSmall)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun CashFlowWaterfall(summary: MonthlyBudgetSummary) {
    FinanceCard {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            FlowRow("Toplam gelir", summary.totalIncomeAmount, positive = true)
            FlowRow("Harcamalar", -summary.totalExpenseAmount, positive = false)
            FlowRow("Planlı ödemeler", -summary.projectedFixedPaymentsAmount, positive = false)
            FlowRow("Birikim hedefi", -summary.savingGoalAmount, positive = false)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ay sonu kalan", style = MaterialTheme.typography.titleMedium)
                Text(
                    MoneyFormatter.format(summary.remainingAfterSavingAndFixedPayments),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (summary.remainingAfterSavingAndFixedPayments >= 0L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun FlowRow(label: String, amount: Long, positive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            (if (amount > 0L) "+" else "") + MoneyFormatter.format(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = if (positive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RemainingTrendCard(trend: List<MonthlyTrendPoint>) {
    val maxAbsolute = trend.maxOfOrNull { it.remainingAmount.absoluteValue }?.coerceAtLeast(1L) ?: 1L
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trend.forEach { point ->
                val positive = point.remainingAmount >= 0L
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            DateUtils.formatMonthYear(point.month),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            MoneyFormatter.format(point.remainingAmount),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (positive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            point.remainingAmount.absoluteValue.toFloat() / maxAbsolute.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (positive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryReportRow(
    summary: CategorySummary,
    totalExpenseAmount: Long
) {
    val share = if (totalExpenseAmount > 0L) {
        summary.amount.toFloat() / totalExpenseAmount
    } else {
        0f
    }
    val progress = summary.percentage ?: share
    val color = when {
        summary.percentage != null && summary.percentage > 1f -> MaterialTheme.colorScheme.error
        summary.percentage != null && summary.percentage >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> Color(summary.colorValue)
    }

    FinanceCard {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Color(summary.colorValue).copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        IconMapper.getIcon(summary.iconName),
                        contentDescription = null,
                        tint = Color(summary.colorValue),
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(summary.categoryName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary.budgetLimit?.let {
                            "Bütçe ${MoneyFormatter.format(summary.amount)} / ${MoneyFormatter.format(it)}"
                        } ?: "Toplamın %${(share * 100).toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(MoneyFormatter.format(summary.amount), style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
