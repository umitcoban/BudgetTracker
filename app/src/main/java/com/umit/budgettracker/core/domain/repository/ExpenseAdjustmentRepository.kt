package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import kotlinx.coroutines.flow.Flow

interface ExpenseAdjustmentRepository {
    fun observeAllAdjustments(): Flow<List<ExpenseAdjustment>>
    fun observeForExpense(expenseId: Long): Flow<List<ExpenseAdjustment>>
    suspend fun addAdjustment(adjustment: ExpenseAdjustment)
    suspend fun deleteAdjustment(adjustment: ExpenseAdjustment)
}
