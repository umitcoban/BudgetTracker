package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.ExpenseDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao
) : ExpenseRepository {
    override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
        val startDate = yearMonth.atDay(1).toEpochDay()
        val endDate = yearMonth.atEndOfMonth().toEpochDay()
        return expenseDao.getExpensesForPeriod(startDate, endDate).map { entities ->
            entities.map { it.expense.toDomain(it.category?.toDomain(), it.account?.toDomain()) }
        }
    }

    override fun observeAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAll().map { entities ->
            entities.map { it.expense.toDomain(it.category?.toDomain(), it.account?.toDomain()) }
        }
    }

    override suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getById(id)?.let { 
            it.expense.toDomain(it.category?.toDomain(), it.account?.toDomain())
        }
    }

    override suspend fun insertExpense(expense: Expense) {
        expenseDao.insert(expense.toEntity())
    }

    override suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense.toEntity())
    }

    override suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(expense.toEntity())
    }

    override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean {
        val startDate = yearMonth.atDay(1).toEpochDay()
        val endDate = yearMonth.atEndOfMonth().toEpochDay()
        return expenseDao.countSubscriptionExpensesForPeriod(subscriptionId, startDate, endDate) > 0
    }

    override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean {
        return expenseDao.countBySubscriptionId(subscriptionId) > 0
    }
}
