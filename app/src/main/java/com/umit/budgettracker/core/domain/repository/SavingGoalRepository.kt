package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.MonthlySavingGoal
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface SavingGoalRepository {
    fun observeAllSavingGoals(): Flow<List<MonthlySavingGoal>>
    fun observeSavingGoalForMonth(yearMonth: YearMonth): Flow<MonthlySavingGoal?>
    suspend fun upsertSavingGoal(goal: MonthlySavingGoal)
    suspend fun deleteSavingGoal(goal: MonthlySavingGoal)
}
