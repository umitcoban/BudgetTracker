package com.umit.budgettracker.feature.receipt

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.MerchantRule
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.AssistantRepository
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseAttachmentRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.usecase.RecordExpenseUseCase
import com.umit.budgettracker.core.ocr.DocumentTextExtractor
import com.umit.budgettracker.core.parsing.ReceiptDraft
import com.umit.budgettracker.core.parsing.ReceiptParser
import com.umit.budgettracker.core.parsing.StatementMatcher
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: DocumentTextExtractor,
    private val recordExpense: RecordExpenseUseCase,
    private val attachmentRepository: ExpenseAttachmentRepository,
    private val workScheduler: WorkScheduler,
    categoryRepository: CategoryRepository,
    accountRepository: PaymentAccountRepository,
    assistantRepository: AssistantRepository
) : ViewModel() {
    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules: StateFlow<List<MerchantRule>> = assistantRepository.observeMerchantRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _image = MutableStateFlow<Uri?>(null)
    val image: StateFlow<Uri?> = _image.asStateFlow()
    private val _ocrText = MutableStateFlow<String?>(null)
    val ocrText: StateFlow<String?> = _ocrText.asStateFlow()
    private val _draft = MutableStateFlow<ReceiptDraft?>(null)
    val draft: StateFlow<ReceiptDraft?> = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _duplicateWarning = MutableStateFlow<String?>(null)
    val duplicateWarning: StateFlow<String?> = _duplicateWarning.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun newCaptureUri(): Uri {
        val dir = File(context.cacheDir, "receipt_capture").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun onImage(uri: Uri) {
        _image.value = uri
        viewModelScope.launch {
            _busy.value = true
            runCatching { extractor.recognizeImage(uri) }
                .onSuccess { text ->
                    _ocrText.value = text
                    val parsed = ReceiptParser.parse(text)
                    _draft.value = parsed
                    _duplicateWarning.value = if (parsed.total != null && parsed.date != null) {
                        recordExpense.findDuplicate(parsed.date, parsed.total, parsed.merchant.orEmpty())?.let {
                            "Aynı gün aynı tutarda \"${it.title}\" zaten kayıtlı."
                        }
                    } else null
                    if (parsed.total == null) _message.value = "Toplam tutar okunamadı; elle girebilirsin."
                }
                .onFailure {
                    _draft.value = ReceiptDraft(null, null, null)
                    _message.value = "Fiş okunamadı. Daha net ve düz bir fotoğraf dene ya da bilgileri elle gir."
                }
            _busy.value = false
        }
    }

    fun save(title: String, amount: Long, date: LocalDate, categoryId: Long, accountId: Long) {
        val uri = _image.value
        viewModelScope.launch {
            val id = recordExpense(title, amount, date, categoryId, accountId, note = "Fişten")
            if (id != null && id > 0L && uri != null) {
                attachmentRepository.addReceiptPhoto(id, uri)
            }
            withContext(Dispatchers.IO) { File(context.cacheDir, "receipt_capture").listFiles()?.forEach { it.delete() } }
            workScheduler.updateWidgets()
            _message.value = "$title harcamalara eklendi" + if (uri != null) ", fiş fotoğrafı iliştirildi." else "."
            _done.value = true
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun ReceiptScanScreen(
    onBack: () -> Unit,
    viewModel: ReceiptScanViewModel = hiltViewModel()
) {
    val image by viewModel.image.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val ocrText by viewModel.ocrText.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val duplicateWarning by viewModel.duplicateWarning.collectAsState()
    val done by viewModel.done.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    var showOcr by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingCapture?.let(viewModel::onImage)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::onImage) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(done) { if (done) onBack() }

    DetailScaffold(
        title = "Fiş Tara",
        subtitle = "Cihaz içi OCR · her zaman senin onayınla",
        onBack = onBack,
        snackbarHostState = snackbar
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedCard(
                    onClick = {
                        val uri = viewModel.newCaptureUri()
                        pendingCapture = uri
                        camera.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text("Fotoğraf çek", style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedCard(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Text("Galeriden seç", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            image?.let { uri -> ReceiptPreview(uri) }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Fiş okunuyor…")
                }
            }

            if (image == null && !busy) {
                EmptyState(
                    icon = Icons.Default.Receipt,
                    title = "Fişin fotoğrafını çek",
                    message = "Tutar, tarih ve mağaza adı okunur; kategori daha önceki seçimlerinden öğrenilir. Fotoğraf harcamaya iliştirilir."
                )
            }

            draft?.let { parsed ->
                ReceiptForm(
                    draft = parsed,
                    suggestedCategoryId = parsed.merchant?.let { StatementMatcher.suggestCategory(it, rules) },
                    categories = categories,
                    accounts = accounts,
                    warning = duplicateWarning,
                    onSave = viewModel::save
                )
                ocrText?.let { text ->
                    TextButton(onClick = { showOcr = !showOcr }) { Text(if (showOcr) "Okunan metni gizle" else "Okunan metni göster") }
                    if (showOcr) {
                        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptPreview(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = 900f / maxOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Fiş",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp))
        )
    }
}

@Composable
private fun ReceiptForm(
    draft: ReceiptDraft,
    suggestedCategoryId: Long?,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    warning: String?,
    onSave: (String, Long, LocalDate, Long, Long) -> Unit
) {
    var title by remember(draft) { mutableStateOf(draft.merchant.orEmpty()) }
    var amountText by remember(draft) { mutableStateOf(MoneyInput.format(draft.total)) }
    var date by remember(draft) { mutableStateOf(draft.date ?: LocalDate.now()) }
    var category by remember(draft, categories, suggestedCategoryId) {
        mutableStateOf(categories.firstOrNull { it.id == suggestedCategoryId } ?: categories.firstOrNull { it.name == "Market" } ?: categories.firstOrNull())
    }
    var account by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }
    val amount = MoneyInput.parse(amountText)
    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Okunan bilgiler", style = MaterialTheme.typography.titleSmall)
            warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Mağaza") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            AmountField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "Toplam",
                supportingText = if (draft.total == null) "Okunamadı, elle gir" else null,
                isError = draft.total == null && amount == null
            )
            DateField(label = "Tarih", value = date, onChange = { date = it })
            DropdownField(
                label = "Kategori" + if (suggestedCategoryId != null && category?.id == suggestedCategoryId) " (öğrenilen)" else "",
                options = categories,
                selected = category,
                onSelect = { category = it },
                optionLabel = { it.name }
            )
            DropdownField(label = "Ödeme hesabı", options = accounts, selected = account, onSelect = { account = it }, optionLabel = { it.name })
            Button(
                onClick = { onSave(title.ifBlank { "Fiş" }, amount!!, date, category!!.id, account!!.id) },
                enabled = amount != null && amount > 0L && category != null && account != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (amount != null && amount > 0L) "${MoneyFormatter.format(amount)} harcamaya ekle" else "Harcamaya ekle")
            }
        }
    }
}
