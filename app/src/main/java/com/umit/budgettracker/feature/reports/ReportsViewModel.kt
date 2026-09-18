package com.umit.budgettracker.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CategoryTrendRules
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.CategoryTrend
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
            val months = (TREND_MONTH_COUNT - 1 downTo 0).map { month.minusMonths(it.toLong()) }
            combine(
                calculator.getSummariesForMonths(months),
                netWorthRepository.observeSnapshotForMonth(month)
            ) { summaries, netWorth ->
                ReportsUiState.Success(
                    currentMonth = summaries.last(),
                    previousMonth = summaries[summaries.size - 2],
                    netWorth = netWorth,
                    categoryTrends = CategoryTrendRules.buildTrends(summaries),
                    trend = summaries.map { summary ->
                        MonthlyTrendPoint(
                            month = summary.yearMonth,
                            incomeAmount = summary.totalIncomeAmount,
                            expenseAmount = summary.totalExpenseAmount,
                            remainingAmount = summary.remainingAfterSavingAndFixedPayments,
                            directExpenseAmount = summary.directExpenseAmount,
                            creditCardPaymentAmount = summary.creditCardPaymentAmount
                        )
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState.Loading)

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    private companion object {
        /** Selected month plus the five before it; the previous-month comparison reuses index size-2. */
        const val TREND_MONTH_COUNT = 6
    }
}

data class MonthlyTrendPoint(
    val month: YearMonth,
    val incomeAmount: Long,
    val expenseAmount: Long,
    val remainingAmount: Long,
    val directExpenseAmount: Long,
    val creditCardPaymentAmount: Long
)

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Success(
        val currentMonth: MonthlyBudgetSummary,
        val previousMonth: MonthlyBudgetSummary,
        val netWorth: NetWorthSnapshot?,
        val categoryTrends: List<CategoryTrend>,
        val trend: List<MonthlyTrendPoint>
    ) : ReportsUiState
}
