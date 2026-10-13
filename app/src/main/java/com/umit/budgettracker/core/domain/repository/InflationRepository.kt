package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.InflationRate
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface InflationRepository {
    fun observeAll(): Flow<List<InflationRate>>
    suspend fun getAll(): List<InflationRate>

    /** Inserts or replaces the rate for its month; a month never has two rows. */
    suspend fun upsert(rate: InflationRate)
    suspend fun delete(month: YearMonth)
}
