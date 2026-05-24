package com.umit.budgettracker.feature.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.Loan
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val loans by viewModel.loans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Krediler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        if (loans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Henüz kredi eklenmemiş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(loans) { loan ->
                    LoanRow(loan)
                }
            }
        }

        if (showAddDialog) {
            AddLoanDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, principal, monthly, count, start, day ->
                    viewModel.addLoan(title, principal, monthly, count, start, day)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun LoanRow(loan: Loan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = loan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Aylık Ödeme: ${MoneyFormatter.format(loan.monthlyPaymentAmount)}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Kalan Taksit: ${loan.installmentCount}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AddLoanDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long, Int, YearMonth, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var monthlyText by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("12") }
    var startMonthText by remember { mutableStateOf(YearMonth.now().toString()) }
    var dayText by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Kredi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") })
                OutlinedTextField(value = principalText, onValueChange = { principalText = it }, label = { Text("Anapara") })
                OutlinedTextField(value = monthlyText, onValueChange = { monthlyText = it }, label = { Text("Aylık Ödeme") })
                OutlinedTextField(value = countText, onValueChange = { countText = it }, label = { Text("Taksit Sayısı") })
                OutlinedTextField(value = startMonthText, onValueChange = { startMonthText = it }, label = { Text("Başlangıç Ayı") })
                OutlinedTextField(value = dayText, onValueChange = { dayText = it }, label = { Text("Ödeme Günü") })
            }
        },
        confirmButton = {
            val principal = MoneyFormatter.parse(principalText) ?: 0L
            val monthly = MoneyFormatter.parse(monthlyText) ?: 0L
            val count = countText.toIntOrNull() ?: 1
            val day = dayText.toIntOrNull() ?: 1
            val month = try { YearMonth.parse(startMonthText) } catch (e: Exception) { YearMonth.now() }

            val isValid = title.isNotBlank() && principal > 0 && monthly > 0 && count > 0 && day in 1..31
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(title, principal, monthly, count, month, day) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
