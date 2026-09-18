package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.IncomeDao
import com.umit.budgettracker.core.database.entity.IncomeEntity
import com.umit.budgettracker.core.domain.model.IncomeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.YearMonth

class IncomeRepositoryImplTest {

    @Test
    fun observeIncomesForMonth_readsOnlyTheSelectedCalendarMonth() = runBlocking {
        val dao = mock<IncomeDao>()
        val month = YearMonth.of(2026, 8)
        val startDate = month.atDay(1).toEpochDay()
        val endDate = month.atEndOfMonth().toEpochDay()
        val entity = IncomeEntity(
            id = 7L,
            title = "Prim",
            amount = 15_000_00L,
            incomeDate = LocalDate.of(2026, 8, 12).toEpochDay(),
            type = IncomeType.BONUS.name
        )
        whenever(dao.getForPeriod(startDate, endDate)).thenReturn(flowOf(listOf(entity)))

        val result = IncomeRepositoryImpl(dao)
            .observeIncomesForMonth(month)
            .first()

        verify(dao).getForPeriod(startDate, endDate)
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 8, 12), result.single().incomeDate)
        assertEquals(15_000_00L, result.single().amount)
    }
}
