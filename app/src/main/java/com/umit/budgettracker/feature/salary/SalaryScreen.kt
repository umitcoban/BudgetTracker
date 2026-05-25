package com.umit.budgettracker.feature.salary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.util.MoneyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryScreen(
    onBack: () -> Unit,
    viewModel: SalaryViewModel = hiltViewModel()
) {
    val rules by viewModel.salaryRules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<SalaryRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maaş Kuralları") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Maaş Değişikliği Ekle")
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
                    SalaryRuleRow(
                        rule = rule,
                        onEdit = { editingRule = rule },
                        onDelete = { viewModel.deleteSalaryRule(rule) }
                    )
                }
            }
        }

        if (showAddDialog) {
            SalaryRuleDialog(
                existingRule = null,
                onDismiss = { showAddDialog = false },
                onConfirm = { amount, month, note ->
                    viewModel.saveSalaryRule(null, amount, month, note)
                    showAddDialog = false
                }
            )
        }
        editingRule?.let { rule ->
            SalaryRuleDialog(
                existingRule = rule,
                onDismiss = { editingRule = null },
                onConfirm = { amount, month, note ->
                    viewModel.saveSalaryRule(rule, amount, month, note)
                    editingRule = null
                }
            )
        }
    }
}

@Composable
fun SalaryRuleRow(rule: SalaryRule, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                Text(text = "${rule.effectiveStartMonth} ayından itibaren geçerli", style = MaterialTheme.typography.bodyMedium)
                if (!rule.note.isNullOrBlank()) {
                    Text(text = rule.note, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Düzenle")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil")
            }
        }
    }
}

@Composable
fun SalaryRuleDialog(
    existingRule: SalaryRule?,
    onDismiss: () -> Unit,
    onConfirm: (Long, YearMonth, String?) -> Unit
) {
    var amountText by remember { mutableStateOf(existingRule?.amount?.formatMinor().orEmpty()) }
    var monthText by remember { mutableStateOf(existingRule?.effectiveStartMonth?.toString() ?: YearMonth.now().toString()) }
    var note by remember { mutableStateOf(existingRule?.note.orEmpty()) }
    val amount = MoneyFormatter.parse(amountText)
    val month = runCatching { YearMonth.parse(monthText) }.getOrNull()
    val isValid = (amount ?: 0L) > 0 && month != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingRule == null) "Maaş Değişikliği Ekle" else "Maaş Kuralını Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Miktar (TL)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = monthText, onValueChange = { monthText = it }, label = { Text("Başlangıç Ayı (YYYY-MM)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not (Opsiyonel)") }, modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (existingRule == null) {
                        "Bu tutar seçilen aydan itibaren geçerli olur. Önceki ayların maaşı değişmez."
                    } else {
                        "Başlangıç ayını değiştirirsen eski maaş kuralı korunur ve yeni bir kural eklenir."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(amount!!, month!!, note.ifBlank { null })
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

private fun Long.formatMinor(): String {
    return BigDecimal(this)
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
