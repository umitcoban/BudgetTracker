package com.umit.budgettracker.feature.subscriptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthlyPayments by viewModel.monthlyPayments.collectAsState()
    val allSubscriptions by viewModel.allSubscriptions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDialog by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<Subscription?>(null) }
    var currentEditAmount by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abonelikler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingSub = null
                currentEditAmount = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        if (monthlyPayments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Bu ay için abonelik ödemesi yok.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(monthlyPayments) { payment ->
                    SubscriptionPaymentRow(
                        payment = payment,
                        selectedMonth = selectedMonth,
                        onMarkPaid = { viewModel.markAsPaid(payment) },
                        onEdit = {
                            editingSub = allSubscriptions.find { it.id == payment.subscriptionId }
                            currentEditAmount = payment.amount
                            showDialog = true
                        }
                    )
                }
            }
        }

        if (showDialog) {
            SubscriptionDialog(
                existingSub = editingSub,
                currentAmount = currentEditAmount,
                currentMonth = selectedMonth,
                onDismiss = { showDialog = false },
                onConfirmAdd = { title, amount, bDay, catId, accId, month ->
                    viewModel.addSubscription(title, amount, bDay, catId, accId, month)
                    showDialog = false
                },
                onConfirmUpdate = { sub, amount, month ->
                    viewModel.updateSubscription(sub, amount, month)
                    showDialog = false
                },
                onCancelSub = { sub ->
                    viewModel.cancelSubscription(sub)
                    showDialog = false
                },
                onDeactivateSub = { sub ->
                    viewModel.deactivateSubscription(sub)
                    showDialog = false
                },
                onDeleteSub = { sub ->
                    viewModel.deleteSubscription(sub)
                    showDialog = false
                },
                categories = viewModel.categories.collectAsState().value,
                accounts = viewModel.accounts.collectAsState().value
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPaymentRow(
    payment: SubscriptionMonthlyPayment,
    selectedMonth: YearMonth,
    onMarkPaid: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (payment.isPaid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = payment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val billingDate = selectedMonth.atDay(payment.billingDay.coerceAtMost(selectedMonth.lengthOfMonth()))
                Text(text = "Ödeme tarihi: $billingDate • ${payment.category?.name ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (payment.isPaid) "Harcamalara işlendi" else "Planlandı",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (payment.isPaid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
                Text(text = MoneyFormatter.format(payment.amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (payment.isPaid) {
                Icon(Icons.Default.Check, contentDescription = "Ödendi", tint = Color(0xFF4CAF50))
            } else {
                Button(onClick = onMarkPaid) {
                    Text("${selectedMonth} Harcamalara İşle")
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Düzenle")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDialog(
    existingSub: Subscription?,
    currentAmount: Long?,
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirmAdd: (String, Long, Int, Long, Long, YearMonth) -> Unit,
    onConfirmUpdate: (Subscription, Long?, YearMonth?) -> Unit,
    onCancelSub: (Subscription) -> Unit,
    onDeactivateSub: (Subscription) -> Unit,
    onDeleteSub: (Subscription) -> Unit,
    categories: List<Category>,
    accounts: List<PaymentAccount>
) {
    var title by remember { mutableStateOf(existingSub?.title ?: "") }
    var amountText by remember { mutableStateOf(currentAmount?.let { (it / 100.0).toString() } ?: "") }
    var billingDay by remember { mutableStateOf(existingSub?.billingDay?.toString() ?: "1") }
    var selectedCategory by remember { mutableStateOf(existingSub?.category ?: categories.firstOrNull()) }
    var selectedAccount by remember { mutableStateOf(existingSub?.account ?: accounts.firstOrNull()) }
    var effectiveMonthText by remember { mutableStateOf(currentMonth.toString()) }
    var note by remember { mutableStateOf(existingSub?.note.orEmpty()) }
    var isActive by remember { mutableStateOf(existingSub?.isActive ?: true) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showDeactivateConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    val amount = remember(amountText) { MoneyFormatter.parse(amountText) }
    val bDay = remember(billingDay) { billingDay.toIntOrNull() ?: 0 }
    val month = remember(effectiveMonthText) { try { YearMonth.parse(effectiveMonthText) } catch (e: Exception) { null } }
    val priceChanged = existingSub == null || ((amount ?: 0L) > 0 && amount != currentAmount)
    
    val isFormValid = if (existingSub == null) {
        title.isNotBlank() && (amount ?: 0L) > 0 && bDay in 1..31 && selectedCategory != null && selectedAccount != null && month != null
    } else {
        title.isNotBlank() &&
            bDay in 1..31 &&
            selectedCategory != null &&
            selectedAccount != null &&
            (!priceChanged || ((amount ?: 0L) > 0 && month != null))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingSub != null) "Aboneliği Düzenle" else "Yeni Abonelik") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Başlık") },
                        isError = title.isBlank(),
                        supportingText = { if (title.isBlank()) Text("Başlık boş olamaz.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { 
                    OutlinedTextField(
                        value = amountText, 
                        onValueChange = { amountText = it }, 
                        label = { Text(if (existingSub != null) "Geçerli/Yeni Fiyat (TL)" else "Fiyat (TL)") },
                        isError = amountText.isNotBlank() && (amount ?: 0L) <= 0,
                        supportingText = {
                            if (amountText.isNotBlank() && (amount ?: 0L) <= 0) Text("Tutar geçerli değil.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) 
                }
                item {
                    OutlinedTextField(
                        value = effectiveMonthText,
                        onValueChange = { effectiveMonthText = it },
                        label = { Text("Fiyatın Başladığı Ay (YYYY-MM)") },
                        isError = priceChanged && month == null,
                        supportingText = { if (priceChanged && month == null) Text("Fiyat değişikliği için geçerli ay seçilmelidir.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = billingDay,
                        onValueChange = { billingDay = it },
                        label = { Text("Faturalama Günü (1-31)") },
                        isError = bDay !in 1..31,
                        supportingText = { if (bDay !in 1..31) Text("Faturalama günü 1 ile 31 arasında olmalıdır.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Kategori Seçin",
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
                            value = selectedAccount?.name ?: "Hesap Seçin",
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

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Not") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (existingSub != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = isActive, onCheckedChange = { isActive = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aktif")
                        }
                    }
                    item {
                        Button(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Aboneliği İptal Et")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = { showDeactivateConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pasifleştir")
                        }
                    }
                    item {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aboneliği Tamamen Sil", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isFormValid,
                onClick = {
                    if (existingSub == null) {
                        onConfirmAdd(title, amount!!, bDay, selectedCategory!!.id, selectedAccount!!.id, month!!)
                    } else {
                        onConfirmUpdate(
                            existingSub.copy(
                                title = title,
                                billingDay = bDay,
                                categoryId = selectedCategory!!.id,
                                paymentAccountId = selectedAccount!!.id,
                                note = note.ifBlank { null },
                                isActive = isActive
                            ),
                            if (priceChanged) amount else null,
                            if (priceChanged) month else null
                        )
                    }
                }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )

    if (existingSub != null && showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Aboneliği İptal Et") },
            text = { Text("${currentMonth} ayından itibaren planlanan abonelik ödemeleri durdurulacak. Geçmiş harcamalar silinmez.") },
            confirmButton = { TextButton(onClick = { onCancelSub(existingSub) }) { Text("İptal Et") } },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Vazgeç") } }
        )
    }

    if (existingSub != null && showDeactivateConfirm) {
        AlertDialog(
            onDismissRequest = { showDeactivateConfirm = false },
            title = { Text("Aboneliği Pasifleştir") },
            text = { Text("Bu abonelik gelecekte planlanan ödemelerde görünmeyecek. Geçmiş harcamalar korunur.") },
            confirmButton = { TextButton(onClick = { onDeactivateSub(existingSub) }) { Text("Pasifleştir") } },
            dismissButton = { TextButton(onClick = { showDeactivateConfirm = false }) { Text("Vazgeç") } }
        )
    }

    if (existingSub != null && showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Aboneliği Sil") },
            text = { Text("Bu işlem aboneliği ve fiyat geçmişini kaldırabilir. Bağlı geçmiş harcama varsa silme engellenir.") },
            confirmButton = { TextButton(onClick = { onDeleteSub(existingSub) }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Vazgeç") } }
        )
    }
}
