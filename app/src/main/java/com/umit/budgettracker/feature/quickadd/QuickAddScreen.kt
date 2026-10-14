package com.umit.budgettracker.feature.quickadd

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.usecase.RecordExpenseUseCase
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.ui.IconMapper
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    categoryRepository: CategoryRepository,
    accountRepository: PaymentAccountRepository,
    expenseRepository: ExpenseRepository,
    private val recordExpense: RecordExpenseUseCase,
    private val workScheduler: WorkScheduler
) : ViewModel() {
    val preselectedCategoryId: Long? = savedStateHandle.get<String>(Screen.QuickAdd.ARG_CATEGORY)?.toLongOrNull()

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The account used for the most recent expense, so the common case is one tap. */
    val lastAccountId: StateFlow<Long?> = expenseRepository.observeExpensesForDateRange(LocalDate.now().minusDays(60), LocalDate.now())
        .map { list -> list.maxByOrNull { it.id }?.paymentAccountId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _saved = MutableStateFlow<String?>(null)
    val saved: StateFlow<String?> = _saved.asStateFlow()

    fun save(title: String, amount: Long, date: LocalDate, category: Category, account: PaymentAccount) {
        viewModelScope.launch {
            recordExpense(title.ifBlank { category.name }, amount, date, category.id, account.id, learnMerchant = title.isNotBlank())
            workScheduler.updateWidgets()
            _saved.value = "${MoneyFormatter.format(amount)} · ${category.name} kaydedildi."
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddScreen(
    onBack: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val lastAccountId by viewModel.lastAccountId.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var categoryId by remember { mutableStateOf(viewModel.preselectedCategoryId) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    val amount = MoneyInput.parse(amountText)
    val category = categories.firstOrNull { it.id == categoryId }
    val account = accounts.firstOrNull { it.id == (accountId ?: lastAccountId) } ?: accounts.firstOrNull()
    val canSave = amount != null && amount > 0L && category != null && account != null

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(saved) { if (saved != null) onBack() }

    DetailScaffold(
        title = "Hızlı Ekle",
        subtitle = "Tutar yaz, kategori seç, kaydet",
        onBack = onBack,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { viewModel.save(title, amount!!, date, category!!, account!!) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(52.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (amount != null && amount > 0L) "${MoneyFormatter.format(amount)} kaydet" else "Kaydet")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { input -> amountText = input.filter { it.isDigit() || it == ',' || it == '.' } },
                placeholder = { Text("0", style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
                suffix = { Text("₺", fontSize = 28.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) viewModel.save(title, amount!!, date, category!!, account!!) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Text("Kategori", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { item ->
                    FilterChip(
                        selected = item.id == categoryId,
                        onClick = { categoryId = item.id },
                        leadingIcon = { Icon(IconMapper.getIcon(item.iconName), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(item.name) }
                    )
                }
            }
            Text("Ödeme", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { item ->
                    FilterChip(selected = item.id == account?.id, onClick = { accountId = item.id }, label = { Text(item.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = date == LocalDate.now(), onClick = { date = LocalDate.now() }, label = { Text("Bugün") })
                FilterChip(selected = date == LocalDate.now().minusDays(1), onClick = { date = LocalDate.now().minusDays(1) }, label = { Text("Dün") })
                DateField(label = "Tarih", value = date, onChange = { date = it }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Açıklama (isteğe bağlı)") },
                placeholder = { Text(category?.name ?: "ör. Migros") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}
