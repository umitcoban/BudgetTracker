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
    val expenses by viewModel.expenses.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harcamalar") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.ExpenseTemplates.route) }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Şablonlar")
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
                categories = viewModel.categories.collectAsState().value,
                accounts = viewModel.accounts.collectAsState().value,
                onAddAttachment = { uri -> 
                    editingExpense?.let { viewModel.addAttachment(it.id, uri) }
                },
                attachmentsFlow = editingExpense?.let { viewModel.getAttachments(it.id) } ?: flowOf(emptyList<ExpenseAttachment>()),
                onDeleteAttachment = { viewModel.deleteAttachment(it) }
            )
        }
    }
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
    onAddAttachment: (android.net.Uri) -> Unit,
    attachmentsFlow: kotlinx.coroutines.flow.Flow<List<ExpenseAttachment>>,
    onDeleteAttachment: (ExpenseAttachment) -> Unit
) {
    var title by remember { mutableStateOf(existingExpense?.title ?: "") }
    var amountText by remember { mutableStateOf(existingExpense?.let { (it.amount / 100).toString() } ?: "") }
    var selectedCategory by remember { mutableStateOf(existingExpense?.category ?: categories.firstOrNull()) }
    var selectedAccount by remember { mutableStateOf(existingExpense?.account ?: accounts.firstOrNull()) }
    var selectedDate by remember(existingExpense?.id, selectedMonth) {
        mutableStateOf(existingExpense?.expenseDate ?: selectedMonth.defaultExpenseDate())
    }
    
    var isInstallment by remember { mutableStateOf(false) }
    var installmentCountText by remember { mutableStateOf("2") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<ExpenseAttachment?>(null) }

    val attachments by attachmentsFlow.collectAsState(emptyList())
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onAddAttachment(it) }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

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
                item { OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text(if (isInstallment) "Toplam Tutar (TL)" else "Tutar (TL)") }, modifier = Modifier.fillMaxWidth()) }
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
            val amount = MoneyFormatter.parse(amountText) ?: 0L
            val iCount = installmentCountText.toIntOrNull() ?: 0
            val isValid = title.isNotBlank() && amount > 0 && selectedCategory != null && selectedAccount != null && (!isInstallment || iCount > 1)
            
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
                                loanId = existingExpense?.loanId
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
}

@Composable
fun Color.warningContainer(): Color = Color(0xFFFFF9C4) // Simple yellow for warnings

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
