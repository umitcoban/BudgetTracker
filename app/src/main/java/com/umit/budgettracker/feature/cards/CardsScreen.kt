package com.umit.budgettracker.feature.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    navController: NavController,
    viewModel: CardsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartlar ve Hesaplar") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Installments.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Taksitler")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Kart Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts) { account ->
                AccountRow(account)
            }
        }

        if (showAddDialog) {
            AddCreditCardDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, sDay, dDay ->
                    viewModel.addCreditCard(name, sDay, dDay)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AccountRow(account: PaymentAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (account.type == AccountType.CREDIT_CARD) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (account.type) {
                    AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                    AccountType.BANK_ACCOUNT -> Icons.Default.AccountBalance
                    AccountType.CASH -> Icons.Default.Payments
                },
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (account.type == AccountType.CREDIT_CARD) {
                    Text(
                        text = "Kesim: ${account.statementDay}. gün • Son Ödeme: ${account.dueDay}. gün",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(text = "Hesap Türü: ${account.type.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AddCreditCardDialog(onDismiss: () -> Unit, onConfirm: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Kredi Kartı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kart Adı") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = statementDay, onValueChange = { statementDay = it }, label = { Text("Hesap Kesim Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Son Ödeme Günü (1-31)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && statementDay.toIntOrNull() in 1..31 && dueDay.toIntOrNull() in 1..31,
                onClick = {
                    onConfirm(name, statementDay.toInt(), dueDay.toInt())
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
}
