package com.umit.budgettracker.feature.debt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.DebtType
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    onBack: () -> Unit,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val debts by viewModel.debts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Borç / Alacak") },
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
        if (debts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Henüz kayıt bulunamadı.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(debts) { debt ->
                    DebtRow(debt, onMarkPaid = { viewModel.markAsPaid(debt.id) })
                }
            }
        }

        if (showAddDialog) {
            AddDebtDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, person, amount, type ->
                    viewModel.addDebt(title, person, amount, type, null)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun DebtRow(debt: DebtRecord, onMarkPaid: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = debt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!debt.personName.isNullOrBlank()) {
                    Text(text = debt.personName, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = if (debt.type == DebtType.I_OWE) "Borç" else "Alacak",
                    color = if (debt.type == DebtType.I_OWE) Color.Red else Color(0xFF4CAF50),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(text = MoneyFormatter.format(debt.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (!debt.isPaid) {
                IconButton(onClick = onMarkPaid) {
                    Icon(Icons.Default.Check, contentDescription = "Ödendi")
                }
            }
        }
    }
}

@Composable
fun AddDebtDialog(onDismiss: () -> Unit, onConfirm: (String, String?, Long, DebtType) -> Unit) {
    var title by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(DebtType.I_OWE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Borç/Alacak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") })
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Kişi (Opsiyonel)") })
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Miktar (TL)") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == DebtType.I_OWE, onClick = { type = DebtType.I_OWE })
                    Text("Borç")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = type == DebtType.OWED_TO_ME, onClick = { type = DebtType.OWED_TO_ME })
                    Text("Alacak")
                }
            }
        },
        confirmButton = {
            val amount = MoneyFormatter.parse(amountText) ?: 0L
            TextButton(
                enabled = title.isNotBlank() && amount > 0,
                onClick = { onConfirm(title, person.ifBlank { null }, amount, type) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
