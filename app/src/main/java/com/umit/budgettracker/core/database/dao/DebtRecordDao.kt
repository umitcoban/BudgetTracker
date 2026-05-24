package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.DebtRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtRecordDao {
    @Query("SELECT * FROM debt_records")
    fun getAll(): Flow<List<DebtRecordEntity>>

    @Query("SELECT * FROM debt_records WHERE isPaid = 0")
    fun getAllUnpaid(): Flow<List<DebtRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DebtRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<DebtRecordEntity>)

    @Delete
    suspend fun delete(record: DebtRecordEntity)
}
