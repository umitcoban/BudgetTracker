package com.umit.budgettracker.feature.installments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.InstallmentGroup
import com.umit.budgettracker.core.util.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(
    onBack: () -> Unit,
    viewModel: InstallmentsViewModel = hiltViewModel()
) {
    val groups by viewModel.installmentGroups.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taksitler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Aktif taksit bulunamadı.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups) { group ->
                    InstallmentGroupRow(group = group, onDelete = { viewModel.deleteInstallmentGroup(group.id) })
                }
            }
        }
    }
}

@Composable
fun InstallmentGroupRow(group: InstallmentGroup, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "Toplam: ${MoneyFormatter.format(group.totalAmount)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Taksit Sayısı: ${group.installmentCount}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Kategori: ${group.category?.name ?: "-"}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Sil")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Taksit Grubunu Sil") },
            text = { Text("Bu taksit grubunu ve tüm ilgili harcamaları silmek istediğinizden emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
