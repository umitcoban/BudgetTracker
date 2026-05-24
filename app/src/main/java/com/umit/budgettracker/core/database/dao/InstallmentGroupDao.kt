package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.InstallmentGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentGroupDao {
    @Query("SELECT * FROM installment_groups")
    fun getAll(): Flow<List<InstallmentGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: InstallmentGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<InstallmentGroupEntity>)

    @Query("SELECT * FROM installment_groups WHERE id = :id")
    suspend fun getById(id: Long): InstallmentGroupEntity?

    @Delete
    suspend fun delete(group: InstallmentGroupEntity)
}
