package com.umit.budgettracker.feature.assets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.network.sources.PriceCatalog
import com.umit.budgettracker.core.ui.charts.ChartSlice
import com.umit.budgettracker.core.ui.charts.DonutChart
import com.umit.budgettracker.core.ui.charts.DonutLegend
import com.umit.budgettracker.core.ui.charts.foldTail
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats

@Composable
fun AssetsScreen(
    onBack: () -> Unit,
    onOpenFundImport: () -> Unit,
    viewModel: AssetsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Asset?>(null) }
    var creating by remember { mutableStateOf(false) }
    var transactionFor by remember { mutableStateOf<Asset?>(null) }
    var deleting by remember { mutableStateOf<Asset?>(null) }
    var priceEditing by remember { mutableStateOf<MarketPrice?>(null) }
    var alertEditing by remember { mutableStateOf<PriceAlert?>(null) }
    var creatingAlert by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Varlıklarım",
        subtitle = freshnessLabel(state.prices.maxOfOrNull { it.updatedAt }),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = onOpenFundImport) { Icon(Icons.Default.DocumentScanner, contentDescription = "Fon tablosu içe aktar") }
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            } else {
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Fiyatları güncelle") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Varlık ekle") }
            )
        }
    ) { padding ->
        val portfolio = state.portfolio
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    label = "Toplam varlık değeri",
                    value = MoneyFormatter.format(portfolio.totalValue),
                    supporting = if (portfolio.totalCost > 0L) {
                        "Kâr/zarar ${ValueFormats.signedMoney(portfolio.totalProfit)}" +
                            (portfolio.totalProfitBp?.let { " (${ValueFormats.signedPercent(it)})" } ?: "") +
                            " · maliyet ${MoneyFormatter.format(portfolio.totalCost)}"
                    } else {
                        "Maliyet girersen kâr/zarar burada görünür"
                    },
                    footer = {
                        HeroMetricRow(
                            listOf(
                                "Acil fon" to MoneyFormatter.format(portfolio.emergencyFundValue),
                                "Kalem" to "${portfolio.valuations.size}",
                                "Fiyat" to (portfolio.oldestPriceUpdate?.let { freshnessLabel(it).removePrefix("Son güncelleme: ") } ?: "—")
                            )
                        )
                    }
                )
            }

            if (state.isLoaded && portfolio.valuations.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Diamond,
                        title = "Henüz varlık yok",
                        message = "Altın, döviz, fon, mevduat ve banka bakiyelerini ekle. Bakiye eklemek akıllı nakit uyarısını ve acil fon göstergesini de açar.",
                        actionLabel = "Varlık ekle",
                        onAction = { creating = true }
                    )
                }
            }

            if (portfolio.allocation.size >= 2) {
                item { AllocationCard(portfolio.allocation) }
            }

            portfolio.valuations.groupBy { it.asset.type }.toSortedMap(compareBy { it.ordinal }).forEach { (type, valuations) ->
                item(key = "header_$type") {
                    FinanceSectionHeader(
                        title = type.label,
                        subtitle = "${valuations.size} kalem · ${MoneyFormatter.format(valuations.sumOf { it.value })}"
                    )
                }
                items(valuations, key = { it.asset.id }) { valuation ->
                    AssetRow(
                        valuation = valuation,
                        onClick = { editing = valuation.asset },
                        onTransaction = { transactionFor = valuation.asset }
                    )
                }
            }

            val inactive = state.assets.filter { !it.isActive }
            if (inactive.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Pasif", subtitle = "Toplama dahil değil") }
                items(inactive, key = { "inactive_${it.id}" }) { asset ->
                    ListRowCard(title = asset.name, subtitle = asset.type.label, icon = Icons.Default.VisibilityOff, iconTint = MaterialTheme.colorScheme.outline, onClick = { editing = asset })
                }
            }

            item {
                FinanceSectionHeader(
                    title = "Fiyatlar",
                    subtitle = "Dokunarak elle güncelle",
                    action = { TextButton(onClick = onOpenFundImport) { Text("Fon tablosu") } }
                )
            }
            if (state.relevantPrices.isEmpty()) {
                item {
                    Text(
                        "Henüz fiyat yok. Yenile simgesine dokun ya da fon fiyatlarını tablodan içe aktar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.relevantPrices, key = { "price_${it.code}" }) { price ->
                PriceRow(price) { priceEditing = price }
            }

            item {
                FinanceSectionHeader(
                    title = "Fiyat alarmları",
                    subtitle = "Hedefe ulaşınca bildirim",
                    action = { TextButton(onClick = { creatingAlert = true }) { Text("Ekle") } }
                )
            }
            items(state.alerts, key = { "alert_${it.id}" }) { alert ->
                ListRowCard(
                    title = PriceCatalog.displayName(alert.priceCode),
                    subtitle = "${alert.condition.label}: ${ValueFormats.price(alert.threshold)}" + if (!alert.isActive) " · kapalı" else "",
                    icon = Icons.Default.NotificationsActive,
                    iconTint = if (alert.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    onClick = { alertEditing = alert }
                )
            }
        }
    }

    if (creating || editing != null) {
        AssetDialog(
            existing = editing,
            prices = state.prices,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { asset -> { deleting = asset; editing = null } },
            onSave = { asset, manualPrice ->
                viewModel.saveAsset(asset, manualPrice)
                creating = false
                editing = null
            }
        )
    }
    transactionFor?.let { asset ->
        TransactionDialog(
            asset = asset,
            onDismiss = { transactionFor = null },
            onConfirm = { quantity, amount ->
                viewModel.addTransaction(asset, quantity, amount)
                transactionFor = null
            }
        )
    }
    deleting?.let { asset ->
        ConfirmDialog(
            title = "Varlığı sil",
            message = "${asset.name} silinecek. Geçmiş net varlık kayıtları değişmez. Yalnızca pasif yapmak istersen düzenle ekranındaki anahtarı kullan.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.deleteAsset(asset); deleting = null },
            onDismiss = { deleting = null }
        )
    }
    priceEditing?.let { price ->
        ManualPriceDialog(
            price = price,
            onDismiss = { priceEditing = null },
            onSave = { value ->
                viewModel.setManualPrice(price.code, value)
                priceEditing = null
            }
        )
    }
    if (creatingAlert || alertEditing != null) {
        PriceAlertDialog(
            existing = alertEditing,
            codes = (state.assets.mapNotNull { it.priceCode } + PriceCatalog.gold.map { it.code } + PriceCatalog.currencies.map { it.code }).distinct(),
            currentPrices = state.prices.associateBy { it.code },
            onDismiss = { creatingAlert = false; alertEditing = null },
            onDelete = alertEditing?.let { alert -> { viewModel.deleteAlert(alert); alertEditing = null } },
            onSave = { alert ->
                viewModel.saveAlert(alert)
                creatingAlert = false
                alertEditing = null
            }
        )
    }
}

@Composable
private fun AllocationCard(allocation: List<Pair<String, Long>>) {
    val palette = chartPalette()
    val slices = remember(allocation, palette) {
        allocation.mapIndexed { index, (label, amount) -> ChartSlice(label, amount, palette[index % palette.size]) }
            .foldTail(keep = 5, otherColor = palette.last())
    }
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dağılım", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DonutChart(slices = slices, selectedIndex = selected, onSelect = { selected = it }, modifier = Modifier.size(132.dp))
                DonutLegend(slices = slices, selectedIndex = selected, onSelect = { selected = it }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun chartPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    Color(0xFFD9A93F),
    Color(0xFF4F7CAC),
    Color(0xFFB3667A),
    Color(0xFF7A9E5E),
    MaterialTheme.colorScheme.outline
)

private fun AssetType.icon(): ImageVector = when (this) {
    AssetType.GOLD -> Icons.Default.WorkspacePremium
    AssetType.CURRENCY -> Icons.Default.CurrencyExchange
    AssetType.FUND -> Icons.Default.ShowChart
    AssetType.DEPOSIT -> Icons.Default.Savings
    AssetType.CASH -> Icons.Default.AccountBalanceWallet
    AssetType.CRYPTO -> Icons.Default.CurrencyBitcoin
    AssetType.STOCK -> Icons.Default.CandlestickChart
    AssetType.OTHER -> Icons.Default.Category
}

@Composable
private fun AssetRow(valuation: AssetValuation, onClick: () -> Unit, onTransaction: () -> Unit) {
    val asset = valuation.asset
    val detail = when {
        asset.type == AssetType.DEPOSIT -> buildString {
            append(MoneyFormatter.format(asset.manualValue ?: 0L))
            asset.interestRateBp?.let { append(" · ${ValueFormats.signedPercent(it).removePrefix("+")}") }
            valuation.daysToMaturity?.let { days -> append(if (days >= 0) " · $days gün kaldı" else " · vadesi doldu") }
        }
        asset.type.isPriced && valuation.unitPrice != null -> "${ValueFormats.quantity(asset.quantity)} ${unitLabel(asset)} × ${ValueFormats.price(valuation.unitPrice, asset.priceCode)}"
        asset.type.isPriced -> "${ValueFormats.quantity(asset.quantity)} ${unitLabel(asset)} · fiyat bekleniyor"
        else -> asset.note ?: asset.type.label
    }
    ListRowCard(
        title = asset.name + if (asset.isEmergencyFund) " · acil fon" else "",
        subtitle = detail,
        icon = asset.type.icon(),
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyFormatter.format(valuation.value), style = MaterialTheme.typography.titleSmall)
                val profit = valuation.profit
                if (profit != null && (asset.costAmount != null || asset.type == AssetType.DEPOSIT)) {
                    Text(
                        ValueFormats.signedMoney(profit) + (valuation.profitBp?.let { " (${ValueFormats.signedPercent(it)})" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        below = if (asset.type.isPriced) {
            {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onTransaction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Alım / satış")
                    }
                }
            }
        } else null
    )
}

private fun unitLabel(asset: Asset): String = when (asset.type) {
    AssetType.FUND, AssetType.STOCK -> "pay"
    else -> asset.priceCode?.let { PriceCatalog.find(it)?.unitLabel } ?: ""
}

@Composable
private fun PriceRow(price: MarketPrice, onClick: () -> Unit) {
    val change = price.previousPrice?.takeIf { it > 0L }?.let { previous ->
        ((price.price - previous) * 10_000L / previous).toInt()
    }
    ListRowCard(
        title = price.name,
        subtitle = "${price.source} · ${freshnessLabel(price.updatedAt).removePrefix("Son güncelleme: ")}",
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(ValueFormats.price(price.price, price.code), style = MaterialTheme.typography.titleSmall)
                change?.let {
                    Text(
                        ValueFormats.signedPercent(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (it >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
