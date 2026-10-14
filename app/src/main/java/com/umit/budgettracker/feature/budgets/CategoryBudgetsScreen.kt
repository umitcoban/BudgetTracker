package com.umit.budgettracker.feature.budgets

import androidx.compose.material.icons.filled.PieChart
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.ui.components.AppAlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.CategoryBudget
import com.umit.budgettracker.core.domain.model.CategoryType
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.feature.dashboard.MonthSelector
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetsScreen(
    onBack: () -> Unit,
    viewModel: CategoryBudgetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    var deleting by remember { mutableStateOf<CategoryBudget?>(null) }

    DetailScaffold(
        title = "Kategori Bütçeleri",
        subtitle = "Aylık harcama limitleri · harcama günü esas",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                selectedBudget = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthSelector(
                    selectedMonth = selectedMonth,
                    onMonthChange = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (val state = uiState) {
                is CategoryBudgetsUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is CategoryBudgetsUiState.Success -> {
                    if (state.budgets.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.PieChart,
                                title = "Bu ay bütçe yok",
                                message = "Kategorilere aylık limit koy; aşınca ve %80'e gelince uyarırım.",
                                actionLabel = "Geçen ayınkini kopyala",
                                onAction = viewModel::copyFromPreviousMonth
                            )
                        }
                    }
                    items(state.budgets, key = { it.id }) { budget ->
                        val spent = state.summary.categorySummaries.find { it.categoryId == budget.categoryId }?.amount ?: 0L
                        BudgetRow(
                            budget = budget,
                            spent = spent,
                            onClick = {
                                selectedBudget = budget
                                showDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    val state = uiState
    if (showDialog && state is CategoryBudgetsUiState.Success) {
        val budgetedCategoryIds = state.budgets
            .map { it.categoryId }
            .filter { it != selectedBudget?.categoryId }
            .toSet()
        CategoryBudgetDialog(
            existingBudget = selectedBudget,
            onDismiss = { showDialog = false },
            onConfirm = { catId, amount ->
                viewModel.saveBudget(selectedBudget, catId, amount, selectedMonth)
                showDialog = false
            },
            onDelete = selectedBudget?.let { budget -> { deleting = budget; showDialog = false } },
            categories = state.categories
                .filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.SYSTEM }
                .filter { it.id !in budgetedCategoryIds }
        )
    }
    deleting?.let { budget ->
        ConfirmDialog(
            title = "Bütçeyi sil",
            message = "${budget.category?.name ?: "Bu kategori"} için bu ayın limiti silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.deleteBudget(budget); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetRow(budget: CategoryBudget, spent: Long, onClick: () -> Unit) {
    val progress = if (budget.limitAmount > 0) spent.toFloat() / budget.limitAmount else 0f
    val color = when {
        progress > 1f -> Color.Red
        progress > 0.8f -> Color(0xFFFFA000)
        else -> Color(0xFF4CAF50)
    }

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = budget.category?.name ?: "Bilinmeyen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "${MoneyFormatter.format(spent)} / ${MoneyFormatter.format(budget.limitAmount)}", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
        }
    }
}

@Composable
fun CategoryBudgetDialog(
    existingBudget: CategoryBudget?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
    onDelete: (() -> Unit)?,
    categories: List<Category>
) {
    var amountText by remember { mutableStateOf(MoneyInput.format(existingBudget?.limitAmount)) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it.id == existingBudget?.categoryId } ?: categories.firstOrNull()) }
    val amount = MoneyInput.parse(amountText)

    FormDialog(
        title = if (existingBudget != null) "Bütçeyi düzenle" else "Yeni bütçe",
        onDismiss = onDismiss,
        confirmEnabled = (amount ?: 0L) > 0L && selectedCategory != null,
        onConfirm = { onConfirm(selectedCategory!!.id, amount!!) },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        if (categories.isEmpty()) {
            Text(text = "Bütçe konmamış aktif kategori kalmadı.", color = MaterialTheme.colorScheme.error)
        } else {
            DropdownField(label = "Kategori", options = categories, selected = selectedCategory, onSelect = { selectedCategory = it }, optionLabel = { it.name })
            AmountField(value = amountText, onValueChange = { amountText = it }, label = "Aylık limit", modifier = Modifier.fillMaxWidth())
        }
    }
}
