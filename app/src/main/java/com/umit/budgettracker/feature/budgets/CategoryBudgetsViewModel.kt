package com.umit.budgettracker.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.CategoryBudgetRepository
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryBudgetsViewModel @Inject constructor(
    private val repository: CategoryBudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val calculator: MonthlyBudgetCalculator
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val uiState: StateFlow<CategoryBudgetsUiState> = _selectedMonth.flatMapLatest { month ->
        combine(
            repository.observeBudgetsForMonth(month),
            calculator.getSummaryForMonth(month),
            categoryRepository.observeActiveCategories()
        ) { budgets, summary, categories ->
            CategoryBudgetsUiState.Success(
                budgets = budgets,
                summary = summary,
                categories = categories.filter { it.type == CategoryType.EXPENSE || it.type == CategoryType.SYSTEM }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryBudgetsUiState.Loading)

    fun saveBudget(existingBudget: CategoryBudget?, categoryId: Long, amount: Long, month: YearMonth) {
        viewModelScope.launch {
            repository.upsertCategoryBudget(
                CategoryBudget(
                    id = existingBudget?.id ?: 0,
                    categoryId = categoryId,
                    yearMonth = month,
                    limitAmount = amount,
                    note = null
                )
            )
        }
    }

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
}

sealed interface CategoryBudgetsUiState {
    data object Loading : CategoryBudgetsUiState
    data class Success(
        val budgets: List<CategoryBudget>,
        val summary: MonthlyBudgetSummary,
        val categories: List<Category>
    ) : CategoryBudgetsUiState
}
