package com.umit.budgettracker.feature.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.umit.budgettracker.core.domain.model.Loan
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Krediler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        if (loans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Henüz kredi eklenmemiş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(loans, key = { it.id }) { loan ->
                    LoanRow(
                        loan = loan,
                        onEdit = {
                            editingLoan = loan
                            showLoanDialog = true
                        },
                        selectedMonth = selectedMonth,
                        isPaidForSelectedMonth = loan.id in paidLoanIds,
                        onMarkPaymentAsPaid = { viewModel.markPaymentAsPaid(loan) },
                        onCloseEarly = { loanToClose = loan },
                        onDelete = { loanToDelete = loan }
                    )
                }
            }
        }
    }

    if (showLoanDialog) {
        LoanDialog(
            existingLoan = editingLoan,
            onDismiss = { showLoanDialog = false },
            onConfirm = {
                viewModel.saveLoan(it)
                showLoanDialog = false
            }
        )
    }

    loanToClose?.let { loan ->
        AlertDialog(
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
        AlertDialog(
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

@Composable
private fun LoanRow(
    loan: Loan,
    onEdit: () -> Unit,
    selectedMonth: YearMonth,
    isPaidForSelectedMonth: Boolean,
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
                Column {
                    Text(loan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Aylık ödeme: ${MoneyFormatter.format(loan.monthlyPaymentAmount)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Taksit sayısı: ${loan.installmentCount} • Başlangıç: ${loan.startMonth}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val status = when {
                        loan.closedAt != null -> "Erken kapatıldı • ${loan.closedAt.format(closeDateFormatter)}"
                        loan.isActive -> "Aktif"
                        else -> "Pasif"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (loan.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
            if (loan.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isPaidForSelectedMonth) {
                        Text(
                            text = "${selectedMonth} ödemesi yapıldı",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    } else {
                        TextButton(onClick = onMarkPaymentAsPaid) {
                            Text("${selectedMonth} ödemesini işaretle")
                        }
                    }
                    Button(onClick = onCloseEarly, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Text("Erken Kapat", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanDialog(
    existingLoan: Loan?,
    onDismiss: () -> Unit,
    onConfirm: (Loan) -> Unit
) {
    var title by remember(existingLoan) { mutableStateOf(existingLoan?.title.orEmpty()) }
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
        startMonth != null

    AlertDialog(
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
                OutlinedTextField(
                    value = startMonthText,
                    onValueChange = { startMonthText = it },
                    label = { Text("Başlangıç Ayı (YYYY-MM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it },
                    label = { Text("Ödeme Günü (1-31)") },
                    modifier = Modifier.fillMaxWidth()
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
                            categoryId = existingLoan?.categoryId,
                            paymentAccountId = existingLoan?.paymentAccountId,
                            note = existingLoan?.note,
                            isActive = existingLoan?.isActive ?: true,
                            closedAt = existingLoan?.closedAt,
                            category = existingLoan?.category,
                            account = existingLoan?.account
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
