package com.umit.budgettracker.feature.expense

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.util.InstallmentUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    navController: NavController,
    viewModel: ExpensesViewModel = hiltViewModel()
) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val exchangeRateState by viewModel.exchangeRateState.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harcamalar") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ExpenseTemplates.route) }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Şablonlar")
                    }
                    BadgedBox(badge = {
                        if (filter.isActive) {
                            Badge()
                        }
                    }) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtrele")
                        }
                    }
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingExpense = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Harcama Ekle")
            }
        }
    ) { padding ->
        if (expenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Bu ay henüz harcama eklenmemiş.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses) { expense ->
                    ExpenseRow(
                        expense = expense,
                        onClick = {
                            editingExpense = expense
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteExpense(expense) }
                    )
                }
            }
        }

        if (showDialog) {
            ExpenseDialog(
                existingExpense = editingExpense,
                selectedMonth = selectedMonth,
                onDismiss = { showDialog = false },
                onConfirmSingle = { expense ->
                    if (editingExpense != null) {
                        viewModel.updateExpense(expense)
                    } else {
                        viewModel.addExpense(expense)
                    }
                    showDialog = false
                },
                onConfirmInstallment = { group, list ->
                    viewModel.addInstallmentPurchase(group, list)
                    showDialog = false
                },
                categories = categories,
                accounts = accounts,
                exchangeRateState = exchangeRateState,
                onFetchExchangeRate = { viewModel.fetchExchangeRate(it) },
                onClearExchangeRateState = { viewModel.clearExchangeRateState() },
                onAddAttachment = { uri -> 
                    editingExpense?.let { viewModel.addAttachment(it.id, uri) }
                },
                attachmentsFlow = editingExpense?.let { viewModel.getAttachments(it.id) } ?: flowOf(emptyList<ExpenseAttachment>()),
                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                adjustmentsFlow = editingExpense?.let { viewModel.getAdjustments(it.id) } ?: flowOf(emptyList<ExpenseAdjustment>()),
                onAddRefund = { amount, note ->
                    editingExpense?.let { viewModel.addRefund(it.id, amount, note) }
                },
                onDeleteAdjustment = { viewModel.deleteAdjustment(it) }
            )
        }

        if (showFilterDialog) {
            ExpenseFilterDialog(
                initialFilter = filter,
                categories = categories,
                accounts = accounts,
                onDismiss = { showFilterDialog = false },
                onApply = {
                    viewModel.updateFilter(it)
                    showFilterDialog = false
                },
                onClear = {
                    viewModel.clearFilter()
                    showFilterDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFilterDialog(
    initialFilter: ExpenseFilter,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    onDismiss: () -> Unit,
    onApply: (ExpenseFilter) -> Unit,
    onClear: () -> Unit
) {
    var query by remember(initialFilter) { mutableStateOf(initialFilter.query) }
    var selectedCategoryId by remember(initialFilter) { mutableStateOf(initialFilter.categoryId) }
    var selectedAccountId by remember(initialFilter) { mutableStateOf(initialFilter.accountId) }
    var minimumText by remember(initialFilter) { mutableStateOf(initialFilter.minimumAmount?.formatMinor().orEmpty()) }
    var maximumText by remember(initialFilter) { mutableStateOf(initialFilter.maximumAmount?.formatMinor().orEmpty()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Harcamaları Filtrele") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Başlık, not, kategori veya hesap") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = !categoryExpanded }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Tüm kategoriler",
                        onValueChange = {}, readOnly = true, label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        DropdownMenuItem(text = { Text("Tüm kategoriler") }, onClick = { selectedCategoryId = null; categoryExpanded = false })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { selectedCategoryId = category.id; categoryExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(accountExpanded, { accountExpanded = !accountExpanded }) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Tüm hesaplar",
                        onValueChange = {}, readOnly = true, label = { Text("Ödeme hesabı") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(accountExpanded, { accountExpanded = false }) {
                        DropdownMenuItem(text = { Text("Tüm hesaplar") }, onClick = { selectedAccountId = null; accountExpanded = false })
                        accounts.forEach { account ->
                            DropdownMenuItem(text = { Text(account.name) }, onClick = { selectedAccountId = account.id; accountExpanded = false })
                        }
                    }
                }
                OutlinedTextField(minimumText, { minimumText = it }, label = { Text("En düşük tutar (TL)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(maximumText, { maximumText = it }, label = { Text("En yüksek tutar (TL)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(ExpenseFilter(query, selectedCategoryId, selectedAccountId, MoneyFormatter.parse(minimumText), MoneyFormatter.parse(maximumText)))
            }) { Text("Uygula") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Temizle") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = when {
                expense.subscriptionId != null -> MaterialTheme.colorScheme.primaryContainer
                expense.installmentGroupId != null -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${expense.category?.name ?: "Kategorisiz"} • ${expense.account?.name ?: "Hesapsız"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = expense.expenseDate.toString(), style = MaterialTheme.typography.bodySmall)
                if (expense.originalCurrency != null && expense.originalAmount != null) {
                    Text(
                        text = "${expense.originalAmount.formatMinor()} ${expense.originalCurrency} • ${MoneyFormatter.format(expense.amount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (expense.installmentGroupId != null) {
                    Text(text = "Taksitli Harcama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (expense.subscriptionId != null) {
                    Text(text = "Abonelik Ödemesi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = MoneyFormatter.format(expense.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Sil")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Harcamayı Sil") },
            text = { Text("Bu harcamayı silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    existingExpense: Expense?,
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirmSingle: (Expense) -> Unit,
    onConfirmInstallment: (InstallmentGroup, List<Expense>) -> Unit,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    exchangeRateState: ExchangeRateUiState,
    onFetchExchangeRate: (String) -> Unit,
    onClearExchangeRateState: () -> Unit,
    onAddAttachment: (android.net.Uri) -> Unit,
    attachmentsFlow: kotlinx.coroutines.flow.Flow<List<ExpenseAttachment>>,
    onDeleteAttachment: (ExpenseAttachment) -> Unit,
    adjustmentsFlow: kotlinx.coroutines.flow.Flow<List<ExpenseAdjustment>>,
    onAddRefund: (Long, String?) -> Unit,
    onDeleteAdjustment: (ExpenseAdjustment) -> Unit
) {
    val initialCurrency = existingExpense?.originalCurrency ?: "TRY"
    var title by remember { mutableStateOf(existingExpense?.title ?: "") }
    var selectedCurrency by remember { mutableStateOf(initialCurrency) }
    var amountText by remember {
        mutableStateOf(
            existingExpense?.let {
                val displayAmount = if (initialCurrency == "TRY") it.amount else it.originalAmount ?: it.amount
                displayAmount.formatMinor()
            } ?: ""
        )
    }
    var exchangeRateText by remember {
        mutableStateOf(existingExpense?.exchangeRateToTry?.formatRate(existingExpense.exchangeRateScale) ?: "")
    }
    var exchangeRateSource by remember { mutableStateOf(existingExpense?.exchangeRateSource) }
    var exchangeRateUpdatedAt by remember { mutableStateOf(existingExpense?.exchangeRateUpdatedAt) }
    var selectedCategory by remember { mutableStateOf(existingExpense?.category ?: categories.firstOrNull()) }
    var selectedAccount by remember { mutableStateOf(existingExpense?.account ?: accounts.firstOrNull()) }
    var selectedDate by remember(existingExpense?.id, selectedMonth) {
        mutableStateOf(existingExpense?.expenseDate ?: selectedMonth.defaultExpenseDate())
    }
    
    var isInstallment by remember { mutableStateOf(false) }
    var installmentCountText by remember { mutableStateOf("2") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<ExpenseAttachment?>(null) }
    var showRefundDialog by remember { mutableStateOf(false) }

    val attachments by attachmentsFlow.collectAsState(emptyList())
    val adjustments by adjustmentsFlow.collectAsState(emptyList())
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddAttachment(it) }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val supportedCurrencies = remember { listOf("TRY", "USD", "EUR", "GBP") }

    LaunchedEffect(exchangeRateState, selectedCurrency) {
        when (val state = exchangeRateState) {
            is ExchangeRateUiState.Success -> {
                if (state.rate.baseCurrency == selectedCurrency) {
                    exchangeRateText = state.rate.rateToTry.formatRate(state.rate.rateScale)
                    exchangeRateSource = state.rate.source
                    exchangeRateUpdatedAt = System.currentTimeMillis()
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(selectedCurrency) {
        if (selectedCurrency == "TRY") {
            exchangeRateText = ""
            exchangeRateSource = null
            exchangeRateUpdatedAt = null
            onClearExchangeRateState()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingExpense != null) "Harcamayı Düzenle" else if (isInstallment) "Yeni Taksitli Alışveriş" else "Yeni Harcama") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    if (existingExpense?.installmentGroupId != null) {
                        Surface(color = Color(0xFFFFF9C4), shape = MaterialTheme.shapes.small) {
                            Text(
                                text = "Bu harcama bir taksit grubuna ait. Bu işlem yalnızca seçili taksit kaydını günceller.",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    ExposedDropdownMenuBox(
                        expanded = currencyExpanded,
                        onExpandedChange = { currencyExpanded = !currencyExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Para Birimi") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            supportedCurrencies.forEach { currency ->
                                DropdownMenuItem(
                                    text = { Text(currency) },
                                    onClick = {
                                        selectedCurrency = currency
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (isInstallment) "Toplam Tutar ($selectedCurrency)" else "Tutar ($selectedCurrency)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedCurrency != "TRY") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = exchangeRateText,
                                onValueChange = {
                                    exchangeRateText = it
                                    exchangeRateSource = "MANUAL"
                                    exchangeRateUpdatedAt = System.currentTimeMillis()
                                },
                                label = { Text("1 $selectedCurrency kaç TL?") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    enabled = exchangeRateState !is ExchangeRateUiState.Loading,
                                    onClick = { onFetchExchangeRate(selectedCurrency) }
                                ) {
                                    Icon(Icons.Default.CurrencyExchange, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (exchangeRateState is ExchangeRateUiState.Loading) "Alınıyor" else "Kuru Getir")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val tryAmount = calculateTryAmountText(amountText, exchangeRateText)
                                Text(
                                    text = tryAmount ?: "TL karşılığı hesaplanamadı.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tryAmount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            when (val state = exchangeRateState) {
                                is ExchangeRateUiState.Error -> Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                is ExchangeRateUiState.Success -> if (state.rate.baseCurrency == selectedCurrency) {
                                    Text(
                                        text = "Kaynak: ${state.rate.source}${state.rate.date.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Harcama Tarihi: ${selectedDate.format(dateFormatter)}",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.EditCalendar, contentDescription = "Tarih Seç")
                    }
                }
                
                if (existingExpense == null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isInstallment, onCheckedChange = { isInstallment = it })
                            Text("Taksitli alışveriş")
                        }
                    }

                    if (isInstallment) {
                        item {
                            OutlinedTextField(
                                value = installmentCountText, 
                                onValueChange = { installmentCountText = it }, 
                                label = { Text("Taksit Sayısı") }, 
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            leadingIcon = {
                                selectedCategory?.let { Icon(IconMapper.getIcon(it.iconName), contentDescription = null) }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    leadingIcon = { Icon(IconMapper.getIcon(category.iconName), contentDescription = null) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ödeme Hesabı") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        selectedAccount = account
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (existingExpense != null) {
                    item {
                        ExpenseAdjustmentsSection(
                            expenseAmount = existingExpense.amount,
                            adjustments = adjustments,
                            onAddRefund = { showRefundDialog = true },
                            onDeleteAdjustment = onDeleteAdjustment
                        )
                    }

                    item {
                        Text(text = "Fiş Fotoğrafları", style = MaterialTheme.typography.titleSmall)
                        if (attachments.isEmpty()) {
                            Text(
                                text = "Henüz fotoğraf eklenmedi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            attachments.forEach { attachment ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AttachmentThumbnail(
                                        attachment = attachment,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clickable { previewAttachment = attachment }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { previewAttachment = attachment }
                                    ) {
                                        Text(text = attachment.originalFileName ?: "Fotoğraf", style = MaterialTheme.typography.bodySmall)
                                        Text(text = "Görüntülemek için dokunun", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteAttachment(attachment) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil")
                                    }
                                }
                            }
                        }
                        Button(onClick = { photoLauncher.launch("image/*") }) {
                            Text("Fotoğraf Ekle")
                        }
                    }
                } else {
                    item {
                        Text(text = "Fiş fotoğrafı eklemek için harcamayı kaydettikten sonra düzenleyin.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            val originalAmount = MoneyFormatter.parse(amountText) ?: 0L
            val rate = parseRateToScale(exchangeRateText)
            val amount = if (selectedCurrency == "TRY") {
                originalAmount
            } else {
                calculateTryMinorAmount(originalAmount, rate)
            }
            val iCount = installmentCountText.toIntOrNull() ?: 0
            val isValid = title.isNotBlank() &&
                amount > 0 &&
                selectedCategory != null &&
                selectedAccount != null &&
                (selectedCurrency == "TRY" || rate != null) &&
                (!isInstallment || iCount > 1)
            
            TextButton(
                enabled = isValid,
                onClick = {
                    if (isInstallment && existingExpense == null) {
                        val group = InstallmentGroup(
                            id = 0,
                            title = title,
                            totalAmount = amount,
                            installmentCount = iCount,
                            startDate = selectedDate,
                            categoryId = selectedCategory!!.id,
                            paymentAccountId = selectedAccount!!.id,
                            note = null
                        )
                        val list = InstallmentUtils.generateInstallmentExpenses(
                            title = title,
                            totalAmount = amount,
                            count = iCount,
                            startDate = selectedDate,
                            categoryId = selectedCategory!!.id,
                            paymentAccountId = selectedAccount!!.id,
                            paymentSourceType = selectedAccount!!.type,
                            note = null
                        )
                        onConfirmInstallment(group, list)
                    } else {
                        onConfirmSingle(
                            Expense(
                                id = existingExpense?.id ?: 0,
                                title = title,
                                amount = amount,
                                expenseDate = selectedDate,
                                categoryId = selectedCategory!!.id,
                                paymentAccountId = selectedAccount!!.id,
                                paymentSourceType = selectedAccount!!.type,
                                note = existingExpense?.note,
                                installmentGroupId = existingExpense?.installmentGroupId,
                                subscriptionId = existingExpense?.subscriptionId,
                                loanId = existingExpense?.loanId,
                                fixedExpenseId = existingExpense?.fixedExpenseId,
                                originalAmount = originalAmount.takeIf { selectedCurrency != "TRY" },
                                originalCurrency = selectedCurrency.takeIf { selectedCurrency != "TRY" },
                                exchangeRateToTry = rate.takeIf { selectedCurrency != "TRY" },
                                exchangeRateScale = RATE_SCALE.takeIf { selectedCurrency != "TRY" },
                                exchangeRateSource = exchangeRateSource.takeIf { selectedCurrency != "TRY" },
                                exchangeRateUpdatedAt = exchangeRateUpdatedAt.takeIf { selectedCurrency != "TRY" }
                            )
                        )
                    }
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )

    if (showDatePicker) {
        ExpenseDatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = {
                selectedDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            onDismiss = { previewAttachment = null }
        )
    }

    if (showRefundDialog && existingExpense != null) {
        AddRefundDialog(
            remainingAmount = (existingExpense.amount - adjustments.sumOf { it.amount }).coerceAtLeast(0L),
            onDismiss = { showRefundDialog = false },
            onConfirm = { amount, note ->
                onAddRefund(amount, note)
                showRefundDialog = false
            }
        )
    }
}

@Composable
fun Color.warningContainer(): Color = Color(0xFFFFF9C4) // Simple yellow for warnings

@Composable
private fun ExpenseAdjustmentsSection(
    expenseAmount: Long,
    adjustments: List<ExpenseAdjustment>,
    onAddRefund: () -> Unit,
    onDeleteAdjustment: (ExpenseAdjustment) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var adjustmentToDelete by remember { mutableStateOf<ExpenseAdjustment?>(null) }
    val refundTotal = adjustments.sumOf { it.amount }
    val netAmount = (expenseAmount - refundTotal).coerceAtLeast(0L)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "İadeler", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Net tutar: ${MoneyFormatter.format(netAmount)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )

        if (adjustments.isEmpty()) {
            Text(
                text = "Henüz iade eklenmedi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            adjustments.forEach { adjustment ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = MoneyFormatter.format(adjustment.amount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildString {
                                append(adjustment.adjustmentDate.format(dateFormatter))
                                adjustment.note?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { adjustmentToDelete = adjustment }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil")
                    }
                }
            }
        }

        OutlinedButton(
            enabled = netAmount > 0,
            onClick = onAddRefund,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Replay, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("İade Ekle")
        }
    }

    adjustmentToDelete?.let { adjustment ->
        AlertDialog(
            onDismissRequest = { adjustmentToDelete = null },
            title = { Text("İadeyi Sil") },
            text = { Text("Bu iade kaydını silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAdjustment(adjustment)
                        adjustmentToDelete = null
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { adjustmentToDelete = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
private fun AddRefundDialog(
    remainingAmount: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = MoneyFormatter.parse(amountText) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İade Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Kalan iade edilebilir tutar: ${MoneyFormatter.format(remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("İade Tutarı (TL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount > 0 && amount <= remainingAmount,
                onClick = { onConfirm(amount, note.takeIf { it.isNotBlank() }) }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toUtcMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(millis.toLocalDateUtc())
                    } ?: onDismiss()
                }
            ) {
                Text("Seç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: ExpenseAttachment,
    modifier: Modifier = Modifier
) {
    AttachmentImage(
        attachment = attachment,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: ExpenseAttachment,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.originalFileName ?: "Fiş Fotoğrafı") },
        text = {
            AttachmentImage(
                attachment = attachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 520.dp),
                contentScale = ContentScale.Fit
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
private fun AttachmentImage(
    attachment: ExpenseAttachment,
    modifier: Modifier = Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = attachment.localPath
    ) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(context.filesDir, attachment.localPath).absolutePath)?.asImageBitmap()
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = attachment.originalFileName ?: "Fiş fotoğrafı",
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = "Fotoğraf görüntülenemedi",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun YearMonth.defaultExpenseDate(): LocalDate {
    val today = LocalDate.now()
    return if (this == YearMonth.from(today)) {
        today
    } else {
        atDay(today.dayOfMonth.coerceAtMost(lengthOfMonth()))
    }
}

private fun LocalDate.toUtcMillis(): Long {
    return atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}

private fun Long.toLocalDateUtc(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
}

private const val RATE_SCALE = 10_000

private fun parseRateToScale(text: String): Long? {
    return text
        .replace(',', '.')
        .toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?.multiply(BigDecimal(RATE_SCALE))
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.longValueExact()
}

private fun calculateTryMinorAmount(originalAmount: Long, exchangeRateToTry: Long?): Long {
    if (exchangeRateToTry == null) return 0L
    return BigDecimal(originalAmount)
        .multiply(BigDecimal(exchangeRateToTry))
        .divide(BigDecimal(RATE_SCALE), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

private fun calculateTryAmountText(amountText: String, exchangeRateText: String): String? {
    val originalAmount = MoneyFormatter.parse(amountText) ?: return null
    val rate = parseRateToScale(exchangeRateText) ?: return null
    return "TL karşılığı: ${MoneyFormatter.format(calculateTryMinorAmount(originalAmount, rate))}"
}

private fun Long.formatRate(scale: Int?): String {
    val actualScale = scale ?: RATE_SCALE
    return BigDecimal(this)
        .divide(BigDecimal(actualScale), 4, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private fun Long.formatMinor(): String {
    return BigDecimal(this)
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
