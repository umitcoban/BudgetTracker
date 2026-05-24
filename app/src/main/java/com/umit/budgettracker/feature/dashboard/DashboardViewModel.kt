package com.umit.budgettracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.MonthlySavingGoal
import com.umit.budgettracker.core.domain.repository.DebtRepository
import com.umit.budgettracker.core.domain.repository.SavingGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val calculator: MonthlyBudgetCalculator,
    private val savingGoalRepository: SavingGoalRepository,
    private val debtRepository: DebtRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = _selectedMonth
        .flatMapLatest { month ->
            combine(
                calculator.getSummaryForMonth(month),
                debtRepository.observeOpenDebtRecords()
            ) { summary, debts ->
                DashboardUiState.Success(summary, debts)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun currentMonth() {
        _selectedMonth.value = YearMonth.now()
    }

    fun updateSavingGoal(amount: Long) {
        viewModelScope.launch {
            savingGoalRepository.upsertSavingGoal(
                MonthlySavingGoal(
                    yearMonth = _selectedMonth.value,
                    amount = amount,
                    note = null
                )
            )
        }
    }

    fun applySuggestedSaving(amount: Long) {
        if (amount <= 0L) return
        updateSavingGoal(amount)
    }
}

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val summary: MonthlyBudgetSummary, val openDebts: List<DebtRecord>) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}
