package com.umit.budgettracker.feature.salary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryScreen(
    onBack: () -> Unit,
    viewModel: SalaryViewModel = hiltViewModel()
) {
    val rules by viewModel.salaryRules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maaş Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Maaş Ekle")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Henüz maaş kuralı eklenmemiş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules) { rule ->
                    SalaryRuleRow(rule = rule, onDelete = { viewModel.deleteSalaryRule(rule) })
                }
            }
        }

        if (showAddDialog) {
            AddSalaryDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { amount, month, note ->
                    viewModel.addSalaryRule(amount, month, note)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun SalaryRuleRow(rule: SalaryRule, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = MoneyFormatter.format(rule.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = "Başlangıç: ${rule.effectiveStartMonth}", style = MaterialTheme.typography.bodyMedium)
                if (!rule.note.isNullOrBlank()) {
                    Text(text = rule.note, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil")
            }
        }
    }
}

@Composable
fun AddSalaryDialog(onDismiss: () -> Unit, onConfirm: (Long, YearMonth, String?) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var monthText by remember { mutableStateOf(YearMonth.now().toString()) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Maaş Kuralı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Miktar (TL)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = monthText, onValueChange = { monthText = it }, label = { Text("Başlangıç Ayı (YYYY-MM)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not (Opsiyonel)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = MoneyFormatter.parse(amountText) ?: 0L
                val month = try { YearMonth.parse(monthText) } catch (e: Exception) { YearMonth.now() }
                onConfirm(amount, month, note.ifBlank { null })
            }) {
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
