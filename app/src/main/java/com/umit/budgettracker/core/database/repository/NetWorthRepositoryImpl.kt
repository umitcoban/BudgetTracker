package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.NetWorthSnapshotDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.domain.repository.NetWorthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class NetWorthRepositoryImpl @Inject constructor(
    private val snapshotDao: NetWorthSnapshotDao
) : NetWorthRepository {
    override fun observeSnapshots(): Flow<List<NetWorthSnapshot>> {
        return snapshotDao.getAll().map { it.map { e -> e.toDomain() } }
    }

    override fun observeSnapshotForMonth(yearMonth: YearMonth): Flow<NetWorthSnapshot?> {
        return snapshotDao.getAll().map { entities ->
            entities.find { it.yearMonth == yearMonth.toString() }?.toDomain()
        }
    }

    override suspend fun upsertSnapshot(snapshot: NetWorthSnapshot) {
        snapshotDao.insert(snapshot.toEntity())
    }

    override suspend fun deleteSnapshot(snapshot: NetWorthSnapshot) {
        snapshotDao.delete(snapshot.toEntity())
    }
}
