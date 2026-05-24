package com.umit.budgettracker.feature.networth

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
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    onBack: () -> Unit,
    viewModel: NetWorthViewModel = hiltViewModel()
) {
    val snapshots by viewModel.snapshots.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Net Varlık") },
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
        if (snapshots.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Henüz kayıt bulunamadı.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(snapshots) { snapshot ->
                    NetWorthRow(snapshot)
                }
            }
        }

        if (showAddDialog) {
            AddSnapshotDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { month, cash, bank, invest, card, loan ->
                    viewModel.addSnapshot(month, cash, bank, invest, card, loan)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun NetWorthRow(snapshot: NetWorthSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = snapshot.yearMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = MoneyFormatter.format(snapshot.netWorth), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(text = "Nakit: ${MoneyFormatter.format(snapshot.cashAmount)} • Banka: ${MoneyFormatter.format(snapshot.bankAmount)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AddSnapshotDialog(onDismiss: () -> Unit, onConfirm: (YearMonth, Long, Long, Long, Long, Long) -> Unit) {
    var monthText by remember { mutableStateOf(YearMonth.now().toString()) }
    var cashText by remember { mutableStateOf("") }
    var bankText by remember { mutableStateOf("") }
    var investText by remember { mutableStateOf("") }
    var cardText by remember { mutableStateOf("") }
    var loanText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Varlık Kaydı") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = monthText, onValueChange = { monthText = it }, label = { Text("Ay (YYYY-MM)") }) }
                item { OutlinedTextField(value = cashText, onValueChange = { cashText = it }, label = { Text("Nakit") }) }
                item { OutlinedTextField(value = bankText, onValueChange = { bankText = it }, label = { Text("Banka") }) }
                item { OutlinedTextField(value = investText, onValueChange = { investText = it }, label = { Text("Yatırım") }) }
                item { OutlinedTextField(value = cardText, onValueChange = { cardText = it }, label = { Text("Kart Borcu") }) }
                item { OutlinedTextField(value = loanText, onValueChange = { loanText = it }, label = { Text("Kredi Borcu") }) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val month = try { YearMonth.parse(monthText) } catch (e: Exception) { YearMonth.now() }
                onConfirm(
                    month,
                    MoneyFormatter.parse(cashText) ?: 0L,
                    MoneyFormatter.parse(bankText) ?: 0L,
                    MoneyFormatter.parse(investText) ?: 0L,
                    MoneyFormatter.parse(cardText) ?: 0L,
                    MoneyFormatter.parse(loanText) ?: 0L
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
