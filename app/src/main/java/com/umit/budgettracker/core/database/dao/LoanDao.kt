package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans")
    fun getAll(): Flow<List<LoanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: LoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loans: List<LoanEntity>)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Query(
        "UPDATE loans SET isActive = 0, closedAt = :closedAt, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun closeEarly(id: Long, closedAt: Long, updatedAt: Long)

    @Delete
    suspend fun delete(loan: LoanEntity)
}
