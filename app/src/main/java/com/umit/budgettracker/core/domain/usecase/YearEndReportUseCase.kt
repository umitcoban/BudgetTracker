package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.YearEndReport
import com.umit.budgettracker.core.domain.calculator.YearEndRules
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.InflationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Year-to-date (or full past year) summary, compared with the same months a year earlier.
 * One batched calculator call covers both years.
 */
class YearEndReportUseCase @Inject constructor(
    private val calculator: MonthlyBudgetCalculator,
    private val expenseRepository: ExpenseRepository,
    private val inflationRepository: InflationRepository
) {
    fun observe(year: Int, today: LocalDate = LocalDate.now()): Flow<YearEndReport> {
        val lastMonth = if (year >= today.year) YearMonth.from(today) else YearMonth.of(year, 12)
        val months = (1..lastMonth.monthValue).map { YearMonth.of(year, it) }
        val previous = months.map { it.minusYears(1) }
        return combine(
            calculator.getSummariesForMonths(previous + months),
            expenseRepository.observeExpensesForDateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)),
            inflationRepository.observeAll()
        ) { summaries, expenses, rates ->
            YearEndRules.build(
                year = year,
                months = summaries.drop(previous.size),
                previous = summaries.take(previous.size),
                yearExpenses = expenses,
                rates = rates
            )
        }
    }

    suspend fun build(year: Int): YearEndReport = observe(year).first()
}
