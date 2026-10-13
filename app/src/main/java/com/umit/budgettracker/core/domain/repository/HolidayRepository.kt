package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.PublicHoliday
import kotlinx.coroutines.flow.Flow

interface HolidayRepository {
    fun observeAll(): Flow<List<PublicHoliday>>
    suspend fun getAll(): List<PublicHoliday>
    suspend fun replaceYear(year: Int, holidays: List<PublicHoliday>)
}
