package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.umit.budgettracker.core.database.entity.ExpenseAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseAdjustmentDao {
    @Query("SELECT * FROM expense_adjustments")
    fun getAll(): Flow<List<ExpenseAdjustmentEntity>>

    @Query("SELECT * FROM expense_adjustments WHERE expenseId = :expenseId ORDER BY adjustmentDate DESC")
    fun getByExpense(expenseId: Long): Flow<List<ExpenseAdjustmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(adjustment: ExpenseAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(adjustments: List<ExpenseAdjustmentEntity>)

    @Delete
    suspend fun delete(adjustment: ExpenseAdjustmentEntity)
}
