package com.umit.budgettracker.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.domain.repository.NetWorthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val calculator: MonthlyBudgetCalculator,
    private val netWorthRepository: NetWorthRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val uiState: StateFlow<ReportsUiState> = _selectedMonth
        .flatMapLatest { month ->
            combine(
                calculator.getSummaryForMonth(month),
                calculator.getSummaryForMonth(month.minusMonths(1)),
                netWorthRepository.observeSnapshotForMonth(month),
                sixMonthTrend(month)
            ) { current, previous, netWorth, trend ->
                ReportsUiState.Success(
                    currentMonth = current,
                    previousMonth = previous,
                    netWorth = netWorth,
                    trend = trend
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState.Loading)

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    private fun sixMonthTrend(lastMonth: YearMonth): Flow<List<MonthlyTrendPoint>> {
        return combine(
            calculator.getSummaryForMonth(lastMonth.minusMonths(5)),
            calculator.getSummaryForMonth(lastMonth.minusMonths(4)),
            calculator.getSummaryForMonth(lastMonth.minusMonths(3)),
            calculator.getSummaryForMonth(lastMonth.minusMonths(2)),
            calculator.getSummaryForMonth(lastMonth.minusMonths(1)),
            calculator.getSummaryForMonth(lastMonth)
        ) { summaries ->
            summaries.map { summary ->
                MonthlyTrendPoint(
                    month = summary.yearMonth,
                    incomeAmount = summary.totalIncomeAmount,
                    expenseAmount = summary.totalExpenseAmount,
                    remainingAmount = summary.remainingAfterSavingAndFixedPayments
                )
            }
        }
    }
}

data class MonthlyTrendPoint(
    val month: YearMonth,
    val incomeAmount: Long,
    val expenseAmount: Long,
    val remainingAmount: Long
)

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Success(
        val currentMonth: MonthlyBudgetSummary,
        val previousMonth: MonthlyBudgetSummary,
        val netWorth: NetWorthSnapshot?,
        val trend: List<MonthlyTrendPoint>
    ) : ReportsUiState
}
