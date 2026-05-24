package com.umit.budgettracker.feature.fixedexpenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.usecase.MarkFixedExpenseAsPaidResult
import com.umit.budgettracker.core.domain.usecase.MarkFixedExpenseAsPaidUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class FixedExpensesViewModel @Inject constructor(
    private val repository: FixedExpenseRepository,
    categoryRepository: CategoryRepository,
    accountRepository: PaymentAccountRepository,
    private val markFixedExpenseAsPaid: MarkFixedExpenseAsPaidUseCase
) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val fixedExpenses: StateFlow<List<FixedExpense>> = repository.observeAllFixedExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun saveFixedExpense(expense: FixedExpense) {
        viewModelScope.launch {
            repository.upsertFixedExpense(expense)
        }
    }

    fun deleteFixedExpense(expense: FixedExpense) {
        viewModelScope.launch {
            repository.deleteFixedExpense(expense)
        }
    }

    fun markAsPaid(expense: FixedExpense) {
        viewModelScope.launch {
            _message.value = when (markFixedExpenseAsPaid(expense, _selectedMonth.value)) {
                MarkFixedExpenseAsPaidResult.Created -> "Sabit gider harcamalara işlendi."
                MarkFixedExpenseAsPaidResult.AlreadyPaid -> "Bu sabit gider bu ay zaten harcamalara işlenmiş."
                MarkFixedExpenseAsPaidResult.MissingRequiredSelection -> "Kategori ve ödeme hesabı seçilmelidir."
                MarkFixedExpenseAsPaidResult.NotApplicable -> "Bu sabit gider seçili ay için geçerli değil."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
