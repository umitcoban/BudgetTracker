package com.umit.budgettracker.feature.fixedexpenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FixedExpensesViewModel @Inject constructor(
    private val repository: FixedExpenseRepository
) : ViewModel() {
    val fixedExpenses: StateFlow<List<FixedExpense>> = repository.observeAllFixedExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
