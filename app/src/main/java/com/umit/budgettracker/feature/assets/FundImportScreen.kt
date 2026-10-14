package com.umit.budgettracker.feature.assets

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
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.FundMovementRules
import com.umit.budgettracker.core.domain.calculator.FundPosition
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.usecase.ImportFundPortfolioUseCase
import com.umit.budgettracker.core.ocr.DocumentTextExtractor
import com.umit.budgettracker.core.parsing.FundMovementParser
import com.umit.budgettracker.core.parsing.FundParseResult
import com.umit.budgettracker.core.parsing.FundPortfolioParser
import com.umit.budgettracker.core.parsing.OcrLayout
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Set when the text was a transaction history ("Fon Hareketleri") rather than a portfolio table. */
data class FundHistorySummary(val movementCount: Int, val from: LocalDate, val to: LocalDate, val positions: List<FundPosition>) {
    val incompleteCodes: List<String> get() = positions.filter { it.incompleteHistory }.map { it.code }
    val realizedProfit: Long get() = positions.sumOf { it.realizedProfit }
}

@HiltViewModel
class FundImportViewModel @Inject constructor(
    private val extractor: DocumentTextExtractor,
    private val importFunds: ImportFundPortfolioUseCase,
    private val assetRepository: AssetRepository
) : ViewModel() {
    /** Fund codes the user holds or has prices for; lets the parser repair OCR-mangled codes. */
    private var knownCodes: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            knownCodes = (assetRepository.getAssets().mapNotNull { it.priceCode } + assetRepository.getPrices().map { it.code })
                .filter { it.startsWith("FUND:") }
                .map { it.removePrefix("FUND:") }
                .toSet()
        }
    }
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private val _result = MutableStateFlow<FundParseResult?>(null)
    val result: StateFlow<FundParseResult?> = _result.asStateFlow()
    private val _history = MutableStateFlow<FundHistorySummary?>(null)
    val history: StateFlow<FundHistorySummary?> = _history.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setText(value: String) {
        _text.value = value
        _result.value = null
        _history.value = null
    }

    /** A transaction history wins when it has rows; otherwise the text is read as a portfolio table. */
    fun parse() {
        val movements = FundMovementParser.parse(_text.value)
        if (movements.movements.isNotEmpty()) {
            val positions = FundMovementRules.positions(movements.movements)
            _history.value = FundHistorySummary(
                movementCount = movements.movements.size,
                from = movements.movements.minOf { it.date },
                to = movements.movements.maxOf { it.date },
                positions = positions
            )
            _result.value = FundParseResult(FundMovementRules.toRows(positions), movements.unreadLines)
            return
        }
        _history.value = null
        _result.value = FundPortfolioParser.parse(_text.value, knownCodes).also {
            if (it.rows.isEmpty()) _message.value = "Tabloda fon satırı bulunamadı. Kod, adet, fiyat ve tutar sütunlarının olduğundan emin ol."
        }
    }

    /**
     * Screenshots are rebuilt row by row from the OCR boxes ([OcrLayout.anchoredRows]), because a
     * broker table row spans several text lines. Files go through the generic extractor. The text
     * is shown so the user can fix OCR mistakes.
     */
    fun load(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { if (extractor.isImage(uri)) screenshotText(uri) else extractor.extract(uri) }
                .onSuccess {
                    _text.value = it
                    parse()
                }
                .onFailure { _message.value = "Dosya okunamadı." }
            _busy.value = false
        }
    }

    private suspend fun screenshotText(uri: Uri): String {
        val lines = extractor.recognizeLines(uri)
        val anchored = OcrLayout.anchoredRows(lines).joinToString("\n")
        return if (FundPortfolioParser.parse(anchored).rows.isNotEmpty()) anchored else OcrLayout.rows(lines).joinToString("\n")
    }

    fun apply(codes: Set<String>, updateHoldings: Boolean) {
        val rows = _result.value?.rows?.filter { it.code in codes }.orEmpty()
        viewModelScope.launch {
            val outcome = importFunds(rows, updateHoldings)
            _message.value = "${outcome.pricesSaved} fiyat kaydedildi, ${outcome.created} yeni fon eklendi, ${outcome.updated} fon güncellendi."
            _done.value = true
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun FundImportScreen(
    onBack: () -> Unit,
    viewModel: FundImportViewModel = hiltViewModel()
) {
    val text by viewModel.text.collectAsState()
    val result by viewModel.result.collectAsState()
    val history by viewModel.history.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val done by viewModel.done.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember(result) { mutableStateOf(result?.rows?.map { it.code }?.toSet().orEmpty()) }
    var updateHoldings by remember { mutableStateOf(true) }
    var showUnread by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::load) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::load) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(done) { if (done) onBack() }

    DetailScaffold(
        title = "Fon İçe Aktar",
        subtitle = "Portföy ekran görüntüsü, fon hareketleri (Excel) veya metin",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            val rows = result?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${selected.size} fon seçili", style = MaterialTheme.typography.titleSmall)
                            Text(
                                MoneyFormatter.format(rows.filter { it.code in selected }.sumOf { it.value }),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { viewModel.apply(selected, updateHoldings) }, enabled = selected.isNotEmpty()) { Text("Uygula") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceButton(Icons.Default.Image, "Ekran görüntüsü", Modifier.weight(1f)) {
                        imageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    SourceButton(Icons.Default.UploadFile, "Dosya", Modifier.weight(1f)) {
                        fileLauncher.launch(arrayOf("text/*", "application/pdf", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "image/*"))
                    }
                }
            }
            item {
                Text(
                    "OCR cihazda çalışır, dosya ya da metin saklanmaz. Hesap/müşteri numarası okunmaz; yalnızca fon kodu, adet, fiyat, tutar ve kâr/zarar alınır.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = viewModel::setText,
                    label = { Text("Tablo metni (yapıştır veya düzelt)") },
                    placeholder = { Text("GTL  50.123  7.018,07  0,140017  +700,40") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    // Once read, the text shrinks so the preview below stays reachable on short
                    // screens (the field would otherwise fill the screen and take every scroll).
                    minLines = if (result == null) 5 else 3,
                    maxLines = if (result == null) 12 else 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Okunuyor…", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick = viewModel::parse, enabled = text.isNotBlank() && !busy) { Text("Tabloyu oku") }
                }
            }
            result?.let { parsed ->
                if (parsed.rows.isNotEmpty()) {
                    history?.let { summary -> item { HistoryCard(summary) } }
                    item {
                        FinanceSectionHeader(
                            title = "Önizleme",
                            subtitle = "Toplam ${MoneyFormatter.format(parsed.rows.sumOf { it.value })}" +
                                parsed.rows.mapNotNull { it.profit }.takeIf { it.isNotEmpty() }?.let { " · kâr ${ValueFormats.signedMoney(it.sum())}" }.orEmpty()
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Eldeki adet ve maliyeti güncelle", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (history != null) "Kapalıysa yalnızca fiyatlar kaydedilir. Tamamen satılan fonlar kapatılır." else "Kapalıysa yalnızca fiyatlar kaydedilir",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = updateHoldings, onCheckedChange = { updateHoldings = it })
                        }
                    }
                    items(parsed.rows, key = { it.code }) { row ->
                        val checked = row.code in selected
                        FinanceCard(modifier = Modifier.clickable { selected = if (checked) selected - row.code else selected + row.code }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.code, style = MaterialTheme.typography.titleSmall)
                                    row.name?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        if (row.quantity == 0L) "Tamamı satıldı" else "${ValueFormats.quantity(row.quantity)} pay × ${ValueFormats.price(row.price, "FUND:")}" +
                                            (row.priceDate?.let { " (${it.format(DayFormat)})" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    row.realizedProfit?.let { realized ->
                                        Text(
                                            "Gerçekleşen ${ValueFormats.signedMoney(realized)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (realized >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(MoneyFormatter.format(row.value), style = MaterialTheme.typography.titleSmall)
                                    row.profit?.let { profit ->
                                        val bp = AssetValuationRules.profitBp(row.value, row.cost)
                                        Text(
                                            ValueFormats.signedMoney(profit) + (bp?.let { " (${ValueFormats.signedPercent(it)})" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (parsed.unreadLines.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showUnread = !showUnread }) {
                            Text("Okunamayan ${parsed.unreadLines.size} satır " + if (showUnread) "▲" else "▼")
                        }
                    }
                    if (showUnread) {
                        items(parsed.unreadLines) { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private val DayFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
private fun HistoryCard(summary: FundHistorySummary) {
    FinanceCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("İşlem geçmişi", style = MaterialTheme.typography.titleSmall)
            Text(
                "${summary.movementCount} hareket, ${summary.from.format(DayFormat)} – ${summary.to.format(DayFormat)}. " +
                    "Adet ve maliyet alım/satımlardan ortalama maliyetle hesaplandı; fiyat, fonun son işlem günündeki fiyatıdır " +
                    "(fiyatları yenileyince güncellenir).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary.realizedProfit != 0L) {
                Text("Satışlardan gerçekleşen: ${ValueFormats.signedMoney(summary.realizedProfit)}", style = MaterialTheme.typography.bodySmall)
            }
            if (summary.incompleteCodes.isNotEmpty()) {
                Text(
                    "${summary.incompleteCodes.joinToString()}: dosyadaki satış, dosyadaki alımlardan fazla. Geçmiş eksik; bu fonların adedini portföy ekran görüntüsüyle doğrula.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SourceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
