package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.FixedExpense
import kotlinx.coroutines.flow.Flow

interface FixedExpenseRepository {
    fun observeAllFixedExpenses(): Flow<List<FixedExpense>>
    fun observeActiveFixedExpenses(): Flow<List<FixedExpense>>
    suspend fun upsertFixedExpense(expense: FixedExpense)
    suspend fun deleteFixedExpense(expense: FixedExpense)
}
