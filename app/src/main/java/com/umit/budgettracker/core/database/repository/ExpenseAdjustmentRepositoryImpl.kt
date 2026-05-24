package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.ExpenseAdjustmentDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.repository.ExpenseAdjustmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseAdjustmentRepositoryImpl @Inject constructor(
    private val dao: ExpenseAdjustmentDao
) : ExpenseAdjustmentRepository {
    override fun observeAllAdjustments(): Flow<List<ExpenseAdjustment>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeForExpense(expenseId: Long): Flow<List<ExpenseAdjustment>> {
        return dao.getByExpense(expenseId).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun addAdjustment(adjustment: ExpenseAdjustment) {
        dao.insert(adjustment.toEntity())
    }

    override suspend fun deleteAdjustment(adjustment: ExpenseAdjustment) {
        dao.delete(adjustment.toEntity())
    }
}
