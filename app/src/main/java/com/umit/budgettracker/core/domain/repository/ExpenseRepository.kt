package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface ExpenseRepository {
    fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>>
    fun observeAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: Long): Expense?
    suspend fun insertExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean
    suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean
    suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean
}
