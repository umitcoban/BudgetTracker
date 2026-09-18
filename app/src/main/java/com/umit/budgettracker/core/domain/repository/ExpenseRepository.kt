package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

interface ExpenseRepository {
    fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>>
    fun observeAllExpenses(): Flow<List<Expense>>

    /**
     * Expenses whose [Expense.expenseDate] falls in [startDate, endDate] (both inclusive).
     * The default filters [observeAllExpenses] so fakes stay correct; the Room implementation
     * overrides it with an indexed range query.
     */
    fun observeExpensesForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>> {
        return observeAllExpenses().map { expenses ->
            expenses.filter { !it.expenseDate.isBefore(startDate) && !it.expenseDate.isAfter(endDate) }
        }
    }

    suspend fun getExpenseById(id: Long): Expense?
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean
    suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean
    suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean
}
