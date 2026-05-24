package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.MonthlySavingGoalDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.MonthlySavingGoal
import com.umit.budgettracker.core.domain.repository.SavingGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class SavingGoalRepositoryImpl @Inject constructor(
    private val dao: MonthlySavingGoalDao
) : SavingGoalRepository {
    override fun observeSavingGoalForMonth(yearMonth: YearMonth): Flow<MonthlySavingGoal?> {
        return dao.observeByMonth(yearMonth.toString()).map { it?.toDomain() }
    }

    override fun observeAllSavingGoals(): Flow<List<MonthlySavingGoal>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun upsertSavingGoal(goal: MonthlySavingGoal) {
        dao.insert(goal.toEntity())
    }

    override suspend fun deleteSavingGoal(goal: MonthlySavingGoal) {
        dao.delete(goal.toEntity())
    }
}
