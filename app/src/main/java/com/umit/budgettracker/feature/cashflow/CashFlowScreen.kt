package com.umit.budgettracker.feature.cashflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    viewModel: CashFlowViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nakit Akışı") },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "Bu ay için nakit akışı bulunamadı.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    CashFlowRow(event)
                }
            }
        }
    }
}

@Composable
fun CashFlowRow(event: CashFlowEvent) {
    val typeLabel = when (event.type) {
        CashFlowEventType.EXPENSE -> "Harcama"
        CashFlowEventType.CREDIT_CARD_PAYMENT -> "Kart Ekstresi"
        CashFlowEventType.INSTALLMENT -> "Taksit"
        CashFlowEventType.SUBSCRIPTION -> "Abonelik"
        CashFlowEventType.LOAN -> "Kredi"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (event.type) {
                CashFlowEventType.CREDIT_CARD_PAYMENT -> MaterialTheme.colorScheme.errorContainer
                CashFlowEventType.SUBSCRIPTION, CashFlowEventType.LOAN -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "${event.date} • $typeLabel", style = MaterialTheme.typography.bodySmall)
                if (!event.description.isNullOrBlank()) {
                    Text(text = event.description, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = MoneyFormatter.format(event.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
