package com.umit.budgettracker.feature.cashflow

import com.umit.budgettracker.core.ui.components.DetailScaffold
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.model.CashForecast
import com.umit.budgettracker.core.domain.usecase.CashForecastUseCase
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Warning
import java.time.YearMonth
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.MetricTile
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    onBack: () -> Unit,
    onOpenAssets: () -> Unit = {},
    viewModel: CashFlowViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val forecast by viewModel.forecast.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val groupedEvents = events.groupBy { it.date }.toSortedMap()
    val totalIncome = events
        .filter { it.type == CashFlowEventType.INCOME }
        .sumOf { it.amount }
    val totalOutflow = events
        .filter { it.type != CashFlowEventType.INCOME }
        .sumOf { it.amount }
    val netFlow = totalIncome - totalOutflow

    DetailScaffold(
        title = "Nakit Akışı",
        subtitle = "Ay içindeki para hareketleri",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
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
                CashFlowSummary(
                    income = totalIncome,
                    outflow = totalOutflow,
                    netFlow = netFlow
                )
            }
            if (selectedMonth == YearMonth.now()) {
                item {
                    CashForecastCard(forecast = forecast, onOpenAssets = onOpenAssets)
                }
            }
            item {
                FinanceSectionHeader(
                    title = "Aylık takvim",
                    subtitle = "${events.size} planlı ve gerçekleşen hareket"
                )
            }

            if (events.isEmpty()) {
                item {
                    FinanceCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(
                                "Bu ay hareket bulunmuyor",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Gelirler ve planlı ödemeler eklendiğinde burada sıralanır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                groupedEvents.forEach { (date, dayEvents) ->
                    item {
                        DateHeader(date)
                    }
                    items(dayEvents) { event ->
                        CashFlowRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun CashFlowSummary(income: Long, outflow: Long, netFlow: Long) {
    FinanceCard(
        containerColor = if (netFlow >= 0L) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    "Ayın net akışı",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    MoneyFormatter.format(netFlow),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (netFlow >= 0L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = "Giriş",
                    value = MoneyFormatter.format(income),
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
                MetricTile(
                    label = "Çıkış",
                    value = MoneyFormatter.format(outflow),
                    modifier = Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val relativeLabel = when (date) {
        LocalDate.now() -> "BUGÜN"
        LocalDate.now().plusDays(1) -> "YARIN"
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            DateUtils.formatDayMonthWeekday(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (relativeLabel != null) {
            Text(
                relativeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CashFlowRow(event: CashFlowEvent) {
    val metadata = event.type.metadata()
    val isIncome = event.type == CashFlowEventType.INCOME

    FinanceCard {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(metadata.color().copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    metadata.icon,
                    contentDescription = null,
                    tint = metadata.color(),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(metadata.label)
                        if (!event.description.isNullOrBlank() &&
                            event.description != metadata.label
                        ) {
                            append(" · ")
                            append(event.description)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.bankDayNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Text(
                text = (if (isIncome) "+" else "−") + MoneyFormatter.format(event.amount),
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private data class EventMetadata(
    val label: String,
    val icon: ImageVector,
    val color: @Composable () -> Color
)

@Composable
private fun CashFlowEventType.metadata(): EventMetadata = when (this) {
    CashFlowEventType.INCOME -> EventMetadata(
        "Gelir",
        Icons.AutoMirrored.Filled.TrendingUp
    ) { MaterialTheme.colorScheme.primary }
    CashFlowEventType.EXPENSE -> EventMetadata(
        "Harcama",
        Icons.AutoMirrored.Filled.ReceiptLong
    ) { MaterialTheme.colorScheme.error }
    CashFlowEventType.CREDIT_CARD_PAYMENT -> EventMetadata(
        "Kart ekstresi",
        Icons.Default.CreditCard
    ) { MaterialTheme.colorScheme.error }
    CashFlowEventType.INSTALLMENT -> EventMetadata(
        "Taksit",
        Icons.Default.Payments
    ) { MaterialTheme.colorScheme.tertiary }
    CashFlowEventType.SUBSCRIPTION -> EventMetadata(
        "Abonelik",
        Icons.Default.Repeat
    ) { MaterialTheme.colorScheme.tertiary }
    CashFlowEventType.LOAN -> EventMetadata(
        "Kredi",
        Icons.Default.AccountBalance
    ) { MaterialTheme.colorScheme.tertiary }
    CashFlowEventType.FIXED_EXPENSE -> EventMetadata(
        "Sabit gider",
        Icons.Default.SouthWest
    ) { MaterialTheme.colorScheme.secondary }
    CashFlowEventType.PLANNED_EXPENSE -> EventMetadata(
        "Büyük harcama",
        Icons.Default.Flag
    ) { MaterialTheme.colorScheme.tertiary }
}

@Composable
private fun CashForecastCard(forecast: CashForecast?, onOpenAssets: () -> Unit) {
    if (forecast == null) {
        FinanceCard(modifier = Modifier.clickable(onClick = onOpenAssets)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Akıllı nakit uyarısı", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Varlıklar'a nakit / banka bakiyeni eklersen en sıkışık günü önceden gösteririm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Varlıklar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val message = CashForecastUseCase.describe(forecast)
    val isTight = forecast.shortfall > 0L
    FinanceCard(
        containerColor = if (isTight) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isTight) Icons.Default.Warning else Icons.Default.Insights,
                    contentDescription = null,
                    tint = if (isTight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isTight) "Nakit açığı riski" else "Önümüzdeki ${CashForecastUseCase.HORIZON_DAYS} gün",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                message ?: "En düşük tahmini bakiye ${DateUtils.formatDayMonth(forecast.lowestDate)}: " +
                    MoneyFormatter.format(forecast.lowestBalance),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Başlangıç: Varlıklar'daki nakit / banka toplamı ${MoneyFormatter.format(forecast.startBalance)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
