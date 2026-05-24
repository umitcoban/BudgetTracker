package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface NetWorthRepository {
    fun observeSnapshots(): Flow<List<NetWorthSnapshot>>
    fun observeSnapshotForMonth(yearMonth: YearMonth): Flow<NetWorthSnapshot?>
    suspend fun upsertSnapshot(snapshot: NetWorthSnapshot)
    suspend fun deleteSnapshot(snapshot: NetWorthSnapshot)
}
