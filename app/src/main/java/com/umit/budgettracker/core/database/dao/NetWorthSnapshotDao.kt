package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.NetWorthSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetWorthSnapshotDao {
    @Query("SELECT * FROM net_worth_snapshots ORDER BY yearMonth DESC")
    fun getAll(): Flow<List<NetWorthSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: NetWorthSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<NetWorthSnapshotEntity>)

    @Delete
    suspend fun delete(snapshot: NetWorthSnapshotEntity)
}
