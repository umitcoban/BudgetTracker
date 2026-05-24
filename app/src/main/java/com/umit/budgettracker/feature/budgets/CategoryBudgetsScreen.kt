package com.umit.budgettracker.feature.budgets

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategori Bütçeleri") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onMonthChange = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                selectedBudget = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is CategoryBudgetsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is CategoryBudgetsUiState.Success -> {
                if (state.budgets.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(text = "Bu ay için bütçe tanımlanmamış.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.budgets) { budget ->
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
                
                if (showDialog) {
                    CategoryBudgetDialog(
                        existingBudget = selectedBudget,
                        onDismiss = { showDialog = false },
                        onConfirm = { catId, amount ->
                            viewModel.addBudget(catId, amount, selectedMonth)
                            showDialog = false
                        },
                        categories = state.categories.filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.SYSTEM }
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBudgetDialog(
    existingBudget: CategoryBudget?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
    categories: List<Category>
) {
    var amountText by remember { mutableStateOf(existingBudget?.let { (it.limitAmount / 100).toString() } ?: "") }
    var selectedCategory by remember { mutableStateOf(existingBudget?.category ?: categories.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingBudget != null) "Bütçeyi Düzenle" else "Yeni Bütçe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) {
                    Text(text = "Aktif kategori bulunamadı.", color = MaterialTheme.colorScheme.error)
                } else {
                    OutlinedTextField(
                        value = amountText, 
                        onValueChange = { amountText = it }, 
                        label = { Text("Limit (TL)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "Kategori Seçin",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amount = MoneyFormatter.parse(amountText) ?: 0L
            TextButton(
                enabled = amount > 0 && selectedCategory != null,
                onClick = { onConfirm(selectedCategory!!.id, amount) }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
