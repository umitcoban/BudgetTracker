package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.umit.budgettracker.core.database.entity.FixedExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FixedExpenseDao {
    @Query("SELECT * FROM fixed_expenses ORDER BY title ASC")
    fun getAll(): Flow<List<FixedExpenseEntity>>

    @Query("SELECT * FROM fixed_expenses WHERE isActive = 1 ORDER BY title ASC")
    fun getActive(): Flow<List<FixedExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: FixedExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<FixedExpenseEntity>)

    @Update
    suspend fun update(expense: FixedExpenseEntity)

    @Delete
    suspend fun delete(expense: FixedExpenseEntity)
}
