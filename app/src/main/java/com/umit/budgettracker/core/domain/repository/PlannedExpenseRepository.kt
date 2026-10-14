package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.PlannedExpense
import kotlinx.coroutines.flow.Flow

interface PlannedExpenseRepository {
    fun observeAll(): Flow<List<PlannedExpense>>
    suspend fun upsert(item: PlannedExpense)
    suspend fun delete(id: Long)
}
