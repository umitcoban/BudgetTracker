package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.DebtRecord
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface DebtRepository {
    fun observeAllDebtRecords(): Flow<List<DebtRecord>>
    fun observeOpenDebtRecords(): Flow<List<DebtRecord>>
    fun observeDebtRecordsForMonth(yearMonth: YearMonth): Flow<List<DebtRecord>>
    suspend fun upsertDebtRecord(record: DebtRecord)
    suspend fun markAsPaid(id: Long)
    suspend fun deleteDebtRecord(id: Long)
}
