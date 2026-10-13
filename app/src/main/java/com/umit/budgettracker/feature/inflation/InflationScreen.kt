package com.umit.budgettracker.feature.inflation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.calculator.InflationRules
import com.umit.budgettracker.core.domain.model.InflationRate
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

@Composable
fun InflationScreen(
    onBack: () -> Unit,
    viewModel: InflationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<InflationRate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<InflationRate?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Enflasyon",
        subtitle = "Aylık TÜFE ve reel değer",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = viewModel::fetchFromTcmb) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "TCMB'den eksikleri getir")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ay ekle") }
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
                    label = "Son 12 ay (bileşik)",
                    value = state.last12Bp?.let { InflationRules.formatBp(it) } ?: "—",
                    supporting = state.latest?.let {
                        "Son girilen: ${DateUtils.formatMonthYear(it.month)} · aylık ${InflationRules.formatBp(it.monthlyRateBp)}" +
                            (it.annualRateBp?.let { annual -> " · yıllık ${InflationRules.formatBp(annual)}" } ?: "")
                    } ?: "Henüz oran girilmedi",
                    trailing = if (state.last12Estimated) {
                        { StatusPill("TAHMİNİ", MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f), MaterialTheme.colorScheme.onPrimary) }
                    } else null
                )
            }

            if (state.missingMonths.isNotEmpty() || state.inconsistencies.isNotEmpty()) {
                item {
                    FinanceCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(10.dp))
                                Text("Kontrol et", style = MaterialTheme.typography.titleSmall)
                            }
                            if (state.missingMonths.isNotEmpty()) {
                                Text(
                                    "Eksik aylar: ${state.missingMonths.joinToString { DateUtils.formatMonthYear(it) }}. " +
                                        "Hesaplarda son bilinen oran kullanılır ve sonuç \"tahmini\" etiketlenir.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            state.inconsistencies.forEach { issue ->
                                Text(
                                    "${DateUtils.formatMonthYear(issue.month)}: girilen yıllık ${InflationRules.formatBp(issue.enteredBp)}, " +
                                        "aylıklardan hesaplanan ${InflationRules.formatBp(issue.compoundedBp)}. Rakamları kontrol et.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (state.realLines.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Reel değer", subtitle = "Bugünün parasıyla karşılaştırma") }
                items(state.realLines) { line ->
                    ListRowCard(
                        title = line.title,
                        subtitle = line.detail + if (line.estimated) " (tahmini)" else "",
                        icon = Icons.Default.Balance,
                        trailing = {
                            line.changeBp?.let { bp ->
                                Text(
                                    (if (bp > 0) "+" else "") + InflationRules.formatBp(bp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (bp >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }

            item { RealValueCalculator(state.rates) }

            item {
                FinanceSectionHeader(
                    title = "Aylık oranlar",
                    subtitle = "${state.rates.size} ay · dokunarak düzenle"
                )
            }
            if (state.rates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.TrendingUp,
                        title = "Henüz oran yok",
                        message = "TÜİK her ayın 3'ünde açıklar. Aylık ve yıllık oranı gir ya da üstteki bulut simgesiyle TCMB tablosundan getir.",
                        actionLabel = "Ay ekle",
                        onAction = { creating = true }
                    )
                }
            }
            items(state.rates, key = { it.month.toString() }) { rate ->
                ListRowCard(
                    title = DateUtils.formatMonthYear(rate.month),
                    subtitle = listOfNotNull(
                        rate.annualRateBp?.let { "Yıllık ${InflationRules.formatBp(it)}" },
                        rate.source,
                        rate.note
                    ).joinToString(" · ").ifEmpty { null },
                    onClick = { editing = rate },
                    trailing = {
                        Text("Aylık ${InflationRules.formatBp(rate.monthlyRateBp)}", style = MaterialTheme.typography.titleSmall)
                    }
                )
            }
        }
    }

    if (creating || editing != null) {
        InflationDialog(
            existing = editing,
            suggestedMonth = state.rates.maxOfOrNull { it.month }?.plusMonths(1) ?: YearMonth.now().minusMonths(1),
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { rate -> { deleting = rate; editing = null } },
            onSave = { rate ->
                viewModel.save(rate)
                creating = false
                editing = null
            }
        )
    }
    deleting?.let { rate ->
        ConfirmDialog(
            title = "Oranı sil",
            message = "${DateUtils.formatMonthYear(rate.month)} oranı silinecek. Reel değer hesapları bu ay için tahmini olur.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.delete(rate.month); deleting = null },
            onDismiss = { deleting = null }
        )
    }
    suggestions?.let { list ->
        TcmbSuggestionsDialog(
            suggestions = list,
            onDismiss = viewModel::dismissSuggestions,
            onApply = viewModel::applySuggestions
        )
    }
}

@Composable
private fun RealValueCalculator(rates: List<InflationRate>) {
    var amountText by remember { mutableStateOf("1000") }
    var month by remember { mutableStateOf<YearMonth?>(YearMonth.now().minusMonths(12)) }
    val amount = MoneyInput.parse(amountText)
    val target = YearMonth.now().minusMonths(1)
    val result = if (amount != null && month != null) InflationRules.realValue(rates, amount, month!!, target) else null
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hesaplayıcı", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(value = amountText, onValueChange = { amountText = it }, label = "Tutar", modifier = Modifier.weight(1f))
                MonthField(label = "O tarihte", value = month, onChange = { month = it }, modifier = Modifier.weight(1f))
            }
            Text(
                when {
                    result == null -> "Bu dönem için oran bulunamadı."
                    else -> "${DateUtils.formatMonthYear(target)} parasıyla ${MoneyFormatter.format(result.amount)} " +
                        "(fiyatlar ${InflationRules.formatBp(result.inflationBp)} arttı${if (result.isEstimated) ", tahmini" else ""})."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InflationDialog(
    existing: InflationRate?,
    suggestedMonth: YearMonth,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (InflationRate) -> Unit
) {
    var month by remember { mutableStateOf<YearMonth?>(existing?.month ?: suggestedMonth) }
    var monthlyText by remember { mutableStateOf(existing?.monthlyRateBp?.let { InflationRules.formatBp(it).removePrefix("%") } ?: "") }
    var annualText by remember { mutableStateOf(existing?.annualRateBp?.let { InflationRules.formatBp(it).removePrefix("%") } ?: "") }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val monthly = InflationRules.parseBp(monthlyText)
    val annual = annualText.takeIf { it.isNotBlank() }?.let { InflationRules.parseBp(it) }
    val annualInvalid = annualText.isNotBlank() && annual == null
    FormDialog(
        title = if (existing == null) "Enflasyon gir" else "Oranı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = month != null && monthly != null && !annualInvalid,
        onConfirm = {
            onSave(
                InflationRate(
                    month = month!!,
                    monthlyRateBp = monthly!!,
                    annualRateBp = annual,
                    note = note.ifBlank { null },
                    source = "Elle"
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        if (existing == null) {
            MonthField(label = "Ay", value = month, onChange = { month = it })
        } else {
            Text(DateUtils.formatMonthYear(existing.month), style = MaterialTheme.typography.titleMedium)
        }
        PercentField(value = monthlyText, onChange = { monthlyText = it }, label = "Aylık TÜFE (%)", isError = monthlyText.isNotBlank() && monthly == null)
        PercentField(value = annualText, onChange = { annualText = it }, label = "Yıllık TÜFE (%) — isteğe bağlı", isError = annualInvalid)
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(
            "Aynı ay yeniden girilirse güncellenir. Yıllık oran girersen 12 aylık bileşik hesapla karşılaştırılır.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PercentField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        prefix = { Text("%") },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TcmbSuggestionsDialog(
    suggestions: List<InflationRate>,
    onDismiss: () -> Unit,
    onApply: (List<InflationRate>) -> Unit
) {
    var selected by remember { mutableStateOf(suggestions.map { it.month }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TCMB tablosundan") },
        text = {
            Column {
                Text(
                    "Kayıtlı olmayan ya da farklı olan aylar. Eklemek istediklerini seç.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(suggestions, key = { it.month.toString() }) { rate ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (rate.month in selected) selected - rate.month else selected + rate.month
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = rate.month in selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(DateUtils.formatMonthYear(rate.month), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Aylık ${InflationRules.formatBp(rate.monthlyRateBp)}" + (rate.annualRateBp?.let { " · yıllık ${InflationRules.formatBp(it)}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(suggestions.filter { it.month in selected }) }, enabled = selected.isNotEmpty()) {
                Text("Ekle (${selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
