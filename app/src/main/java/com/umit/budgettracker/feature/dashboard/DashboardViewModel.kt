package com.umit.budgettracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.MonthlySavingGoal
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.repository.DebtRepository
import com.umit.budgettracker.core.domain.repository.IncomeRepository
import com.umit.budgettracker.core.domain.repository.SavingGoalRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
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
    private val debtRepository: DebtRepository,
    private val salaryRepository: SalaryRepository,
    private val incomeRepository: IncomeRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = _selectedMonth
        .flatMapLatest { month ->
            val monthlyIncomeContext = combine(
                calculator.getSummaryForMonth(month),
                calculator.getSummaryForMonth(month.minusMonths(1)),
                salaryRepository.observeSalaryForMonth(month),
                incomeRepository.observeIncomesForMonth(month)
            ) { summary, previousSummary, salaryRule, additionalIncomes ->
                DashboardIncomeContext(
                    summary = summary,
                    previousSummary = previousSummary,
                    salaryRule = salaryRule,
                    additionalIncomeCount = additionalIncomes.size
                )
            }
            combine(
                monthlyIncomeContext,
                debtRepository.observeOpenDebtRecords()
            ) { incomeContext, debts ->
                DashboardUiState.Success(
                    summary = incomeContext.summary,
                    previousSummary = incomeContext.previousSummary,
                    effectiveSalaryRule = incomeContext.salaryRule,
                    additionalIncomeCount = incomeContext.additionalIncomeCount,
                    openDebts = debts
                )
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
    data class Success(
        val summary: MonthlyBudgetSummary,
        val previousSummary: MonthlyBudgetSummary,
        val effectiveSalaryRule: SalaryRule?,
        val additionalIncomeCount: Int,
        val openDebts: List<DebtRecord>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

private data class DashboardIncomeContext(
    val summary: MonthlyBudgetSummary,
    val previousSummary: MonthlyBudgetSummary,
    val salaryRule: SalaryRule?,
    val additionalIncomeCount: Int
)
