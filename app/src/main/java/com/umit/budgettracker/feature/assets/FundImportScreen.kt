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
import com.umit.budgettracker.core.domain.usecase.ImportFundPortfolioUseCase
import com.umit.budgettracker.core.ocr.DocumentTextExtractor
import com.umit.budgettracker.core.parsing.FundParseResult
import com.umit.budgettracker.core.parsing.FundPortfolioParser
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FundImportViewModel @Inject constructor(
    private val extractor: DocumentTextExtractor,
    private val importFunds: ImportFundPortfolioUseCase
) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()
    private val _result = MutableStateFlow<FundParseResult?>(null)
    val result: StateFlow<FundParseResult?> = _result.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setText(value: String) {
        _text.value = value
        _result.value = null
    }

    fun parse() {
        _result.value = FundPortfolioParser.parse(_text.value).also {
            if (it.rows.isEmpty()) _message.value = "Tabloda fon satırı bulunamadı. Kod, adet, fiyat ve tutar sütunlarının olduğundan emin ol."
        }
    }

    /** OCR / file read happens on device; the text is shown so the user can fix OCR mistakes. */
    fun load(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { extractor.extract(uri) }
                .onSuccess {
                    _text.value = it
                    parse()
                }
                .onFailure { _message.value = "Dosya okunamadı." }
            _busy.value = false
        }
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
        subtitle = "Ekran görüntüsü, PDF, Excel/CSV veya metin",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            val rows = result?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
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
                        fileLauncher.launch(arrayOf("text/*", "application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "image/*"))
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
                    minLines = 5,
                    maxLines = 12,
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
                                Text("Kapalıysa yalnızca fiyatlar kaydedilir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text(
                                        "${ValueFormats.quantity(row.quantity)} pay × ${ValueFormats.price(row.price, "FUND:")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
