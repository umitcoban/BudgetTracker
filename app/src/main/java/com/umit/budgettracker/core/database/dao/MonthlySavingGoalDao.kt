package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.MonthlySavingGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlySavingGoalDao {
    @Query("SELECT * FROM monthly_saving_goals")
    fun getAll(): Flow<List<MonthlySavingGoalEntity>>

    @Query("SELECT * FROM monthly_saving_goals WHERE yearMonth = :yearMonth")
    fun observeByMonth(yearMonth: String): Flow<MonthlySavingGoalEntity?>

    @Query("SELECT * FROM monthly_saving_goals WHERE yearMonth = :yearMonth")
    suspend fun getByMonth(yearMonth: String): MonthlySavingGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: MonthlySavingGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<MonthlySavingGoalEntity>)

    @Delete
    suspend fun delete(goal: MonthlySavingGoalEntity)
}
