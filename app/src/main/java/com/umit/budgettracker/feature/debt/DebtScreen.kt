package com.umit.budgettracker.feature.debt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.DebtType
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.LocalDate

@Composable
fun DebtScreen(
    onBack: () -> Unit,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val debts by viewModel.debts.collectAsState()
    var editing by remember { mutableStateOf<DebtRecord?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DebtRecord?>(null) }
    var showClosed by remember { mutableStateOf(false) }
    val open = debts.filter { !it.isPaid }.sortedWith(compareBy(nullsLast()) { it.dueDate })
    val closed = debts.filter { it.isPaid }

    DetailScaffold(
        title = "Borç / Alacak",
        subtitle = "Kişilerle aranızdaki açık hesaplar",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (debts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Handshake,
                        title = "Kayıt yok",
                        message = "Birine borcunu ya da senden alacaklı olanları ekle; kapanınca \"Kapandı\" diye işaretle."
                    )
                }
            }
            if (open.isNotEmpty()) {
                val owe = open.filter { it.type == DebtType.I_OWE }.sumOf { it.amount }
                val owed = open.filter { it.type == DebtType.OWED_TO_ME }.sumOf { it.amount }
                item { FinanceSectionHeader(title = "Açık", subtitle = "Borcun ${MoneyFormatter.format(owe)} · alacağın ${MoneyFormatter.format(owed)}") }
                items(open, key = { it.id }) { debt ->
                    DebtRow(debt, onClick = { editing = debt; showDialog = true }, onTogglePaid = { viewModel.setPaid(debt, true) })
                }
            }
            if (closed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showClosed = !showClosed }) {
                        Text("Kapananlar (${closed.size}) " + if (showClosed) "▲" else "▼")
                    }
                }
                if (showClosed) items(closed, key = { it.id }) { debt ->
                    DebtRow(debt, onClick = { editing = debt; showDialog = true }, onTogglePaid = { viewModel.setPaid(debt, false) })
                }
            }
        }
    }

    if (showDialog) {
        DebtDialog(
            existing = editing,
            onDismiss = { showDialog = false },
            onSave = { viewModel.save(it); showDialog = false },
            onDelete = editing?.let { record -> { deleting = record; showDialog = false } }
        )
    }
    deleting?.let { record ->
        ConfirmDialog(
            title = "Kaydı sil",
            message = "\"${record.title}\" silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.delete(record); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun DebtRow(debt: DebtRecord, onClick: () -> Unit, onTogglePaid: () -> Unit) {
    val owe = debt.type == DebtType.I_OWE
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val details = listOfNotNull(
                    debt.personName?.takeIf { it.isNotBlank() },
                    if (owe) "Borç" else "Alacak",
                    debt.dueDate?.let { "vade ${DateUtils.formatDate(it)}" }
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    MoneyFormatter.format(debt.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (owe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            if (debt.isPaid) {
                TextButton(onClick = onTogglePaid) { Text("Yeniden aç") }
            } else {
                FilledTonalButton(onClick = onTogglePaid) { Text("Kapandı") }
            }
        }
    }
}

@Composable
private fun DebtDialog(
    existing: DebtRecord?,
    onDismiss: () -> Unit,
    onSave: (DebtRecord) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var person by remember { mutableStateOf(existing?.personName.orEmpty()) }
    var amountText by remember { mutableStateOf(MoneyInput.format(existing?.amount)) }
    var type by remember { mutableStateOf(existing?.type ?: DebtType.I_OWE) }
    var dueDate by remember { mutableStateOf(existing?.dueDate) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val amount = MoneyInput.parse(amountText)

    FormDialog(
        title = if (existing == null) "Yeni borç / alacak" else "Kaydı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = title.isNotBlank() && (amount ?: 0L) > 0L,
        onConfirm = {
            onSave(
                DebtRecord(
                    id = existing?.id ?: 0L,
                    title = title.trim(),
                    personName = person.trim().ifBlank { null },
                    amount = amount ?: 0L,
                    type = type,
                    dueDate = dueDate,
                    isPaid = existing?.isPaid ?: false,
                    note = note.trim().ifBlank { null }
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        SegmentedChoice(
            options = listOf(DebtType.I_OWE, DebtType.OWED_TO_ME),
            selected = type,
            onSelect = { type = it },
            label = { if (it == DebtType.I_OWE) "Borcum" else "Alacağım" }
        )
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("Kişi (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Tutar", modifier = Modifier.fillMaxWidth())
        DateField(label = "Vade (isteğe bağlı)", value = dueDate, onChange = { dueDate = it }, modifier = Modifier.fillMaxWidth())
        if (dueDate != null) {
            TextButton(onClick = { dueDate = null }) { Text("Vadeyi kaldır") }
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, modifier = Modifier.fillMaxWidth())
    }
}
