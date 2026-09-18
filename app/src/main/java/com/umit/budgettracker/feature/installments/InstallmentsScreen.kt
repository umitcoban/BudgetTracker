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
import com.umit.budgettracker.core.domain.calculator.InstallmentProgress
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.FinanceSectionHeader
import com.umit.budgettracker.core.ui.components.StatusPill
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(
    onBack: () -> Unit,
    viewModel: InstallmentsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Taksitler")
                        Text(
                            "Taksitli alışverişler ve kalan ödemeler",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FinanceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Devam eden taksitlerin kalan toplamı",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MoneyFormatter.format(state.activeRemainingAmount),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Her taksit, tarihi geldiği ay harcamalarda ve kart ekstresinde ayrı bir kayıt olarak yer alır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                FinanceSectionHeader(
                    title = "Devam edenler",
                    subtitle = if (state.active.isEmpty()) "Devam eden taksit yok" else "${state.active.size} taksitli alışveriş"
                )
            }
            items(state.active, key = { it.group.id }) { progress ->
                InstallmentGroupRow(progress = progress, onDelete = { viewModel.deleteInstallmentGroup(progress.group.id) })
            }

            if (state.completedCount > 0) {
                item {
                    FinanceSectionHeader(
                        title = "Tamamlananlar",
                        subtitle = "${state.completedCount} alışverişin tüm taksitleri geçti",
                        action = {
                            FilterChip(
                                selected = showCompleted,
                                onClick = viewModel::toggleShowCompleted,
                                label = { Text(if (showCompleted) "Gizle" else "Göster") }
                            )
                        }
                    )
                }
                items(state.completed, key = { it.group.id }) { progress ->
                    InstallmentGroupRow(progress = progress, onDelete = { viewModel.deleteInstallmentGroup(progress.group.id) })
                }
            }
        }
    }
}

@Composable
fun InstallmentGroupRow(progress: InstallmentProgress, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val group = progress.group

    FinanceCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(group.category?.name, group.account?.name).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progress.isCompleted) {
                    StatusPill(
                        text = "Tamamlandı",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    StatusPill(
                        text = "${progress.elapsedCount} / ${group.installmentCount}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
                }
            }
            LinearProgressIndicator(
                progress = { progress.elapsedCount.toFloat() / group.installmentCount.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Toplam ${MoneyFormatter.format(group.totalAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (progress.isCompleted) {
                        "Başlangıç ${DateUtils.formatMonthYear(YearMonth.from(group.startDate))}"
                    } else {
                        "Kalan ${MoneyFormatter.format(progress.remainingAmount)} · sonraki ${DateUtils.formatDate(progress.nextInstallmentDate!!)}"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
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
