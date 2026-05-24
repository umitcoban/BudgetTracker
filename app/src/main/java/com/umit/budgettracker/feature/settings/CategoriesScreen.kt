package com.umit.budgettracker.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.CategoryType
import com.umit.budgettracker.core.ui.IconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategoriler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingCategory = Category(0, "", IconMapper.allIconNames.first(), 0xFF9E9E9E.toInt(), CategoryType.EXPENSE, false, true, 0) }) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (categories.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Henüz kategori eklenmedi.")
                    }
                }
            }
            items(categories) { category ->
                CategoryRow(
                    category = category,
                    onToggle = { viewModel.toggleCategory(category) },
                    onEdit = { editingCategory = category }
                )
            }
        }

        editingCategory?.let { category ->
            CategoryDialog(
                category = category,
                onDismiss = { editingCategory = null },
                onConfirm = { updated ->
                    if (updated.id == 0L) {
                        viewModel.addCategory(updated.name, updated.iconName)
                    } else {
                        viewModel.updateCategory(updated)
                    }
                    editingCategory = null
                },
                onDelete = {
                    viewModel.requestDelete(category)
                    editingCategory = null
                }
            )
        }
    }
}

@Composable
fun CategoryRow(category: Category, onToggle: () -> Unit, onEdit: () -> Unit) {
    ListItem(
        headlineContent = { Text(category.name) },
        supportingContent = { Text(if (category.isActive) "Aktif" else "Pasif") },
        leadingContent = { Icon(IconMapper.getIcon(category.iconName), contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                Switch(checked = category.isActive, onCheckedChange = { onToggle() })
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var selectedIcon by remember { mutableStateOf(category.iconName) }
    var selectedType by remember { mutableStateOf(category.type) }
    var colorValueText by remember { mutableStateOf(category.colorValue.toString()) }
    var sortOrderText by remember { mutableStateOf(category.sortOrder.toString()) }
    var isActive by remember { mutableStateOf(category.isActive) }
    var iconExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    val isValid = name.isNotBlank() && selectedIcon.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.id == 0L) "Yeni Kategori" else "Kategoriyi Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kategori Adı") },
                    isError = name.isBlank(),
                    supportingText = { if (name.isBlank()) Text("Kategori adı boş olamaz.") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = iconExpanded,
                    onExpandedChange = { iconExpanded = !iconExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedIcon,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Simge") },
                        leadingIcon = { Icon(IconMapper.getIcon(selectedIcon), contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = iconExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = iconExpanded,
                        onDismissRequest = { iconExpanded = false }
                    ) {
                        IconMapper.allIconNames.forEach { iconName ->
                            DropdownMenuItem(
                                text = { Text(iconName) },
                                leadingIcon = { Icon(IconMapper.getIcon(iconName), contentDescription = null) },
                                onClick = {
                                    selectedIcon = iconName
                                    iconExpanded = false
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tür") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        CategoryType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = colorValueText,
                    onValueChange = { colorValueText = it },
                    label = { Text("Renk Değeri") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sortOrderText,
                    onValueChange = { sortOrderText = it },
                    label = { Text("Sıralama") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktif")
                }
                if (category.id != 0L) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(
                        category.copy(
                            name = name.trim(),
                            iconName = selectedIcon,
                            colorValue = colorValueText.toIntOrNull() ?: category.colorValue,
                            type = selectedType,
                            isActive = isActive,
                            sortOrder = sortOrderText.toIntOrNull() ?: category.sortOrder
                        )
                    )
                }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
