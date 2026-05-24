package com.umit.budgettracker.feature.templates

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
import com.umit.budgettracker.core.domain.model.ExpenseTemplate
import com.umit.budgettracker.core.util.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTemplatesScreen(
    onBack: () -> Unit,
    viewModel: ExpenseTemplatesViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hızlı Harcama Şablonları") },
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
        if (templates.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Henüz şablon eklenmemiş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates) { template ->
                    TemplateRow(template)
                }
            }
        }

        if (showAddDialog) {
            AddTemplateDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, amount, catId, accId ->
                    viewModel.addTemplate(title, amount, catId, accId)
                    showAddDialog = false
                },
                categories = viewModel.categories.collectAsState().value,
                accounts = viewModel.accounts.collectAsState().value
            )
        }
    }
}

@Composable
fun TemplateRow(template: ExpenseTemplate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Varsayılan: ${template.defaultAmount?.let { MoneyFormatter.format(it) } ?: "Girilmedi"} • ${template.category?.name ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Long, Long?) -> Unit,
    categories: List<com.umit.budgettracker.core.domain.model.Category>,
    accounts: List<com.umit.budgettracker.core.domain.model.PaymentAccount>
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Şablon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") })
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Varsayılan Tutar (Opsiyonel)") })
                Text("Kategori: ${selectedCategory?.name ?: "-"}")
            }
        },
        confirmButton = {
            val amount = if (amountText.isNotBlank()) MoneyFormatter.parse(amountText) else null
            TextButton(
                enabled = title.isNotBlank() && selectedCategory != null,
                onClick = { onConfirm(title, amount, selectedCategory!!.id, null) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
