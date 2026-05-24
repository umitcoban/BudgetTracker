package com.umit.budgettracker.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.network.ExchangeRateResult
import com.umit.budgettracker.core.network.ExchangeRateService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val installmentRepository: InstallmentRepository,
    private val attachmentRepository: ExpenseAttachmentRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val exchangeRateService: ExchangeRateService
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val expenses: StateFlow<List<Expense>> = _selectedMonth
        .flatMapLatest { month ->
            expenseRepository.observeExpensesForMonth(month)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exchangeRateState = MutableStateFlow<ExchangeRateUiState>(ExchangeRateUiState.Idle)
    val exchangeRateState: StateFlow<ExchangeRateUiState> = _exchangeRateState.asStateFlow()

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.insertExpense(expense)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }

    fun addInstallmentPurchase(group: InstallmentGroup, expenses: List<Expense>) {
        viewModelScope.launch {
            installmentRepository.insertInstallmentGroup(group, expenses)
        }
    }

    fun getAttachments(expenseId: Long): Flow<List<ExpenseAttachment>> {
        return attachmentRepository.observeForExpense(expenseId)
    }

    fun addAttachment(expenseId: Long, uri: android.net.Uri) {
        viewModelScope.launch {
            attachmentRepository.addReceiptPhoto(expenseId, uri)
        }
    }

    fun deleteAttachment(attachment: ExpenseAttachment) {
        viewModelScope.launch {
            attachmentRepository.deleteAttachment(attachment)
        }
    }

    fun getAdjustments(expenseId: Long): Flow<List<ExpenseAdjustment>> {
        return adjustmentRepository.observeForExpense(expenseId)
    }

    fun addRefund(expenseId: Long, amount: Long, note: String?) {
        viewModelScope.launch {
            adjustmentRepository.addAdjustment(
                ExpenseAdjustment(
                    id = 0,
                    expenseId = expenseId,
                    amount = amount,
                    type = ExpenseAdjustmentType.REFUND,
                    adjustmentDate = java.time.LocalDate.now(),
                    note = note
                )
            )
        }
    }

    fun deleteAdjustment(adjustment: ExpenseAdjustment) {
        viewModelScope.launch {
            adjustmentRepository.deleteAdjustment(adjustment)
        }
    }

    fun fetchExchangeRate(currency: String) {
        if (currency == "TRY") {
            _exchangeRateState.value = ExchangeRateUiState.Idle
            return
        }

        viewModelScope.launch {
            _exchangeRateState.value = ExchangeRateUiState.Loading
            exchangeRateService.fetchRateToTry(currency)
                .onSuccess { _exchangeRateState.value = ExchangeRateUiState.Success(it) }
                .onFailure {
                    _exchangeRateState.value = ExchangeRateUiState.Error(
                        "Kur bilgisi alınamadı. Manuel kur girebilirsiniz."
                    )
                }
        }
    }

    fun clearExchangeRateState() {
        _exchangeRateState.value = ExchangeRateUiState.Idle
    }
}

sealed interface ExchangeRateUiState {
    data object Idle : ExchangeRateUiState
    data object Loading : ExchangeRateUiState
    data class Success(val rate: ExchangeRateResult) : ExchangeRateUiState
    data class Error(val message: String) : ExchangeRateUiState
}
