package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.DebtRecordDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.repository.DebtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class DebtRepositoryImpl @Inject constructor(
    private val debtDao: DebtRecordDao
) : DebtRepository {
    override fun observeAllDebtRecords(): Flow<List<DebtRecord>> {
        return debtDao.getAll().map { it.map { e -> e.toDomain() } }
    }

    override fun observeOpenDebtRecords(): Flow<List<DebtRecord>> {
        return debtDao.getAllUnpaid().map { it.map { e -> e.toDomain() } }
    }

    override fun observeDebtRecordsForMonth(yearMonth: YearMonth): Flow<List<DebtRecord>> {
        return debtDao.getAll().map { entities ->
            entities.filter { 
                it.dueDate != null && java.time.LocalDate.ofEpochDay(it.dueDate).let { date ->
                    YearMonth.from(date) == yearMonth
                }
            }.map { it.toDomain() }
        }
    }

    override suspend fun upsertDebtRecord(record: DebtRecord) {
        debtDao.insert(record.toEntity())
    }

    override suspend fun markAsPaid(id: Long) {
        val records = debtDao.getAll().firstOrNull() ?: emptyList()
        records.find { it.id == id }?.let { 
            debtDao.insert(it.copy(isPaid = true))
        }
    }

    override suspend fun deleteDebtRecord(id: Long) {
        val records = debtDao.getAll().firstOrNull() ?: emptyList()
        records.find { it.id == id }?.let { 
            debtDao.delete(it)
        }
    }
}
