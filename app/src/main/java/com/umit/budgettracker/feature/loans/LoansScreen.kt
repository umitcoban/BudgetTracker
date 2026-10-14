package com.umit.budgettracker.feature.loans

import com.umit.budgettracker.core.ui.components.DateField
import com.umit.budgettracker.core.ui.components.MonthField
import androidx.compose.material.icons.filled.AccountBalance
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.EmptyState
import com.umit.budgettracker.core.ui.components.DetailScaffold
import com.umit.budgettracker.core.domain.calculator.LoanPaymentRules
import com.umit.budgettracker.core.domain.calculator.LoanMonthStatus
import com.umit.budgettracker.core.ui.components.AppAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.calculator.LoanPaymentCalculator
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val loans by viewModel.loans.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val paidLoanIds by viewModel.paidLoanIds.collectAsState()
    val paymentsByLoanId by viewModel.paymentsByLoanId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var showLoanDialog by remember { mutableStateOf(false) }
    var loanToClose by remember { mutableStateOf<Loan?>(null) }
    var loanToDelete by remember { mutableStateOf<Loan?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val grouped = loans.groupBy { LoanPaymentRules.statusFor(it, selectedMonth, it.id in paidLoanIds) }
    val due = grouped[LoanMonthStatus.DUE].orEmpty()
    val paid = grouped[LoanMonthStatus.PAID].orEmpty()
    val inactive = grouped[LoanMonthStatus.NOT_STARTED].orEmpty() + grouped[LoanMonthStatus.FINISHED].orEmpty() +
        grouped[LoanMonthStatus.CLOSED].orEmpty()
    var showInactive by remember { mutableStateOf(false) }

    @Composable
    fun LoanItem(loan: Loan) = LoanRow(
        loan = loan,
        status = LoanPaymentRules.statusFor(loan, selectedMonth, loan.id in paidLoanIds),
        onEdit = {
            editingLoan = loan
            showLoanDialog = true
        },
        selectedMonth = selectedMonth,
        payments = paymentsByLoanId[loan.id].orEmpty(),
        onMarkPaymentAsPaid = { viewModel.markPaymentAsPaid(loan) },
        onCloseEarly = { loanToClose = loan },
        onDelete = { loanToDelete = loan }
    )

    DetailScaffold(
        title = "Krediler",
        subtitle = "Taksitler ve ödeme durumu",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingLoan = null
                    showLoanDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Kredi Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthSelector(
                    selectedMonth = selectedMonth,
                    onMonthChange = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (loans.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalance,
                        title = "Henüz kredi yok",
                        message = "Kredini ekle; aylık taksit plana ve nakit akışına otomatik girer."
                    )
                }
            }
            if (due.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Bu ay ödenecek", subtitle = "${due.size} kredi · ${MoneyFormatter.format(due.sumOf { it.monthlyPaymentAmount })}") }
                items(due, key = { it.id }) { LoanItem(it) }
            }
            if (paid.isNotEmpty()) {
                item { FinanceSectionHeader(title = "Bu ay ödendi", subtitle = "${paid.size} kredi") }
                items(paid, key = { it.id }) { LoanItem(it) }
            }
            if (loans.isNotEmpty() && due.isEmpty() && paid.isEmpty()) {
                item {
                    Text(
                        "${DateUtils.formatMonthYear(selectedMonth)} için ödenecek kredi taksiti yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (inactive.isNotEmpty()) {
                item {
                    TextButton(onClick = { showInactive = !showInactive }) {
                        Text("Biten, kapatılan ve başlamamış (${inactive.size}) " + if (showInactive) "▲" else "▼")
                    }
                }
                if (showInactive) items(inactive, key = { it.id }) { LoanItem(it) }
            }
        }
    }

    if (showLoanDialog) {
        LoanDialog(
            existingLoan = editingLoan,
            categories = categories,
            accounts = accounts,
            onDismiss = { showLoanDialog = false },
            onConfirm = {
                viewModel.saveLoan(it)
                showLoanDialog = false
            }
        )
    }

    loanToClose?.let { loan ->
        AppAlertDialog(
            onDismissRequest = { loanToClose = null },
            title = { Text("Krediyi Erken Kapat") },
            text = {
                Text(
                    "Bu işlem gelecekteki planlı taksitleri durdurur. Daha önce kaydedilmiş harcamalar silinmez. Devam etmek istiyor musunuz?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.closeLoanEarly(loan)
                        loanToClose = null
                    }
                ) {
                    Text("Erken Kapat")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToClose = null }) { Text("Vazgeç") }
            }
        )
    }

    loanToDelete?.let { loan ->
        AppAlertDialog(
            onDismissRequest = { loanToDelete = null },
            title = { Text("Krediyi Sil") },
            text = {
                Text(
                    "Bu kredi kaydını silmek istediğinizden emin misiniz? Krediye bağlı harcamalar varsa kayıt silinmez."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLoan(loan)
                        loanToDelete = null
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoanRow(
    loan: Loan,
    status: LoanMonthStatus,
    onEdit: () -> Unit,
    selectedMonth: YearMonth,
    payments: List<LoanPayment>,
    onMarkPaymentAsPaid: () -> Unit,
    onCloseEarly: () -> Unit,
    onDelete: () -> Unit
) {
    val closeDateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("tr-TR"))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(loan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Aylık ödeme: ${MoneyFormatter.format(loan.monthlyPaymentAmount)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Taksit sayısı: ${loan.installmentCount} • Başlangıç: ${DateUtils.formatMonthYear(loan.startMonth)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val number = LoanPaymentRules.installmentNumber(loan, selectedMonth)
                    val statusText = when (status) {
                        LoanMonthStatus.CLOSED -> loan.closedAt?.let { "Erken kapatıldı • ${it.format(closeDateFormatter)}" } ?: "Pasif"
                        LoanMonthStatus.FINISHED -> "Tamamlandı • son taksit ${DateUtils.formatMonthYear(LoanPaymentRules.endMonth(loan))}"
                        LoanMonthStatus.NOT_STARTED -> "Henüz başlamadı"
                        LoanMonthStatus.DUE, LoanMonthStatus.PAID ->
                            "$number/${loan.installmentCount}. taksit • kalan ${MoneyFormatter.format((loan.installmentCount - number).toLong() * loan.monthlyPaymentAmount)}"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            LoanMonthStatus.DUE -> MaterialTheme.colorScheme.error
                            LoanMonthStatus.PAID -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
            if (status == LoanMonthStatus.DUE || status == LoanMonthStatus.PAID) {
                // FlowRow: the two actions wrap to a second line on narrow screens instead of
                // squeezing a button into one letter per line.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(onClick = onCloseEarly) { Text("Erken kapat") }
                    if (status == LoanMonthStatus.PAID) {
                        Row(Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${DateUtils.formatMonthYear(selectedMonth)} ödendi",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    } else {
                        Button(onClick = onMarkPaymentAsPaid) { Text("Ödendi işaretle") }
                    }
                }
            }
            if (payments.isNotEmpty()) {
                Text(
                    text = "İşlenen ödemeler",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                payments.take(3).forEach { payment ->
                    Text(
                        text = "${DateUtils.formatMonthYear(payment.paymentMonth)}: ${MoneyFormatter.format(payment.amount)} • ${payment.paidAt.format(closeDateFormatter)} ödendi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (payments.size > 3) {
                    Text(
                        text = "+${payments.size - 3} eski ödeme",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val DEFAULT_LOAN_CATEGORY_NAME = "Kredi"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanDialog(
    existingLoan: Loan?,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    onDismiss: () -> Unit,
    onConfirm: (Loan) -> Unit
) {
    var title by remember(existingLoan) { mutableStateOf(existingLoan?.title.orEmpty()) }
    var selectedCategory by remember(existingLoan, categories) {
        mutableStateOf(
            categories.firstOrNull { it.id == existingLoan?.categoryId }
                ?: categories.firstOrNull { it.name == DEFAULT_LOAN_CATEGORY_NAME }
                ?: categories.firstOrNull()
        )
    }
    var selectedAccount by remember(existingLoan, accounts) {
        mutableStateOf(
            accounts.firstOrNull { it.id == existingLoan?.paymentAccountId }
                ?: accounts.firstOrNull { it.type == AccountType.BANK_ACCOUNT }
                ?: accounts.firstOrNull()
        )
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var principalText by remember(existingLoan) { mutableStateOf(existingLoan?.principalAmount?.toAmountInput().orEmpty()) }
    var countText by remember(existingLoan) { mutableStateOf(existingLoan?.installmentCount?.toString() ?: "12") }
    var startMonthText by remember(existingLoan) { mutableStateOf(existingLoan?.startMonth?.toString() ?: YearMonth.now().toString()) }
    var dayText by remember(existingLoan) { mutableStateOf(existingLoan?.paymentDay?.toString() ?: "1") }

    val principal = MoneyFormatter.parse(principalText)
    val count = countText.toIntOrNull()
    val day = dayText.toIntOrNull()
    val startMonth = runCatching { YearMonth.parse(startMonthText) }.getOrNull()
    val monthlyPayment = LoanPaymentCalculator.calculateMonthlyPayment(principal ?: 0L, count ?: 0)
    val isValid = title.isNotBlank() &&
        (principal ?: 0L) > 0 &&
        monthlyPayment != null &&
        (count ?: 0) > 0 &&
        (day ?: 0) in 1..31 &&
        startMonth != null &&
        selectedCategory != null &&
        selectedAccount != null

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingLoan == null) "Yeni Kredi" else "Krediyi Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Başlık") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text("Anapara (TL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it },
                    label = { Text("Taksit Sayısı") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = monthlyPayment?.let {
                        "Hesaplanan aylık ödeme: ${MoneyFormatter.format(it)}"
                    } ?: "Aylık ödeme hesaplanabilmesi için geçerli ana para ve taksit sayısı girin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MonthField(label = "İlk taksit ayı", value = runCatching { java.time.YearMonth.parse(startMonthText) }.getOrNull(), onChange = { startMonthText = it?.toString().orEmpty() }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it },
                    label = { Text("Ödeme Günü (1-31)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                Text(
                    "Taksit \"ödendi\" işaretlendiğinde bu kategori ve hesapla bir harcama kaydı oluşur.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (existingLoan != null) {
                    Text(
                        "Geçmişte kaydedilmiş harcamalar değişmeden korunur.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        Loan(
                            id = existingLoan?.id ?: 0L,
                            title = title.trim(),
                            principalAmount = principal!!,
                            monthlyPaymentAmount = monthlyPayment!!,
                            installmentCount = count!!,
                            startMonth = startMonth!!,
                            paymentDay = day!!,
                            categoryId = selectedCategory?.id,
                            paymentAccountId = selectedAccount?.id,
                            note = existingLoan?.note,
                            isActive = existingLoan?.isActive ?: true,
                            closedAt = existingLoan?.closedAt,
                            category = selectedCategory,
                            account = selectedAccount
                        )
                    )
                }
            ) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}

private fun Long.toAmountInput(): String {
    return (this / 100).toString() + "." + (this % 100).toString().padStart(2, '0')
}
