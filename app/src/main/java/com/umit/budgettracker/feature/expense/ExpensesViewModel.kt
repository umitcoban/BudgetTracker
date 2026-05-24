package com.umit.budgettracker.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
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
    private val attachmentRepository: ExpenseAttachmentRepository
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
}
