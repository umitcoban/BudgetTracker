package com.umit.budgettracker.feature.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.ExpenseTemplate
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter

@Composable
fun ExpenseTemplatesScreen(
    onBack: () -> Unit,
    viewModel: ExpenseTemplatesViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var editing by remember { mutableStateOf<ExpenseTemplate?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ExpenseTemplate?>(null) }

    DetailScaffold(
        title = "Hızlı Harcama Şablonları",
        subtitle = "Sık girdiğin harcamalar tek dokunuşla",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Şablon ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Bolt,
                        title = "Henüz şablon yok",
                        message = "\"Sabah kahvesi\" gibi sık girdiğin harcamaları şablon yap; Harcamalar ekranından tek dokunuşla eklersin."
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                Card(onClick = { editing = template; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                template.defaultAmount?.let { MoneyFormatter.format(it) } ?: "Tutar her seferinde girilir",
                                template.category?.name,
                                template.account?.name
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        TemplateDialog(
            existing = editing,
            categories = categories,
            accounts = accounts,
            onDismiss = { showDialog = false },
            onSave = { viewModel.save(it); showDialog = false },
            onDelete = editing?.let { template -> { deleting = template; showDialog = false } }
        )
    }
    deleting?.let { template ->
        ConfirmDialog(
            title = "Şablonu sil",
            message = "\"${template.title}\" şablonu silinecek. Daha önce eklenen harcamalar etkilenmez.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.delete(template); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun TemplateDialog(
    existing: ExpenseTemplate?,
    categories: List<Category>,
    accounts: List<PaymentAccount>,
    onDismiss: () -> Unit,
    onSave: (ExpenseTemplate) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var amountText by remember { mutableStateOf(MoneyInput.format(existing?.defaultAmount)) }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull { it.id == existing?.categoryId } ?: categories.firstOrNull()) }
    var account by remember(accounts) { mutableStateOf(accounts.firstOrNull { it.id == existing?.paymentAccountId }) }
    val amount = amountText.takeIf { it.isNotBlank() }?.let { MoneyInput.parse(it) }
    val amountValid = amountText.isBlank() || (amount != null && amount > 0L)

    FormDialog(
        title = if (existing == null) "Yeni şablon" else "Şablonu düzenle",
        onDismiss = onDismiss,
        confirmEnabled = title.isNotBlank() && category != null && amountValid,
        onConfirm = {
            onSave(
                ExpenseTemplate(
                    id = existing?.id ?: 0L,
                    title = title.trim(),
                    defaultAmount = amount,
                    categoryId = category!!.id,
                    paymentAccountId = account?.id,
                    note = existing?.note,
                    isActive = true
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(
            value = amountText,
            onValueChange = { amountText = it },
            label = "Varsayılan tutar (isteğe bağlı)",
            isError = !amountValid,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownField(label = "Kategori", options = categories, selected = category, onSelect = { category = it }, optionLabel = { it.name })
        DropdownField(
            label = "Ödeme hesabı",
            options = listOf<PaymentAccount?>(null) + accounts,
            selected = account,
            onSelect = { account = it },
            optionLabel = { it?.name ?: "Her seferinde seçilir" }
        )
    }
}
