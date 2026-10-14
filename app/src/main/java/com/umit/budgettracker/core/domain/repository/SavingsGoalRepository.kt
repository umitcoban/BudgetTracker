package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.GoalContribution
import com.umit.budgettracker.core.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface SavingsGoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>
    fun observeContributions(): Flow<List<GoalContribution>>
    suspend fun upsertGoal(goal: SavingsGoal): Long

    /** Blocked while the goal still has contributions; deactivate it instead. */
    suspend fun deleteGoal(id: Long): Boolean
    suspend fun addContribution(contribution: GoalContribution)
    suspend fun deleteContribution(id: Long)
}
