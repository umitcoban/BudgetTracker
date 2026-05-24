package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class FixedExpenseMonthlyCalculatorTest {
    @Test
    fun getPaymentsForMonth_includesOnlyActiveExpensesInMonthRange() = runBlocking {
        val repository = FakeFixedExpenseRepository(
            listOf(
                fixedExpense(id = 1L, title = "Kira", startMonth = YearMonth.of(2026, 1), endMonth = null, isActive = true),
                fixedExpense(id = 2L, title = "Aidat", startMonth = YearMonth.of(2026, 3), endMonth = YearMonth.of(2026, 5), isActive = true),
                fixedExpense(id = 3L, title = "Eski Gider", startMonth = YearMonth.of(2026, 1), endMonth = YearMonth.of(2026, 4), isActive = true),
                fixedExpense(id = 4L, title = "Pasif", startMonth = YearMonth.of(2026, 1), endMonth = null, isActive = false)
            )
        )
        val calculator = FixedExpenseMonthlyCalculator(repository)

        val payments = calculator.getPaymentsForMonth(YearMonth.of(2026, 5)).first()

        assertEquals(listOf("Kira", "Aidat"), payments.map { it.title })
    }

    private fun fixedExpense(
        id: Long,
        title: String,
        startMonth: YearMonth,
        endMonth: YearMonth?,
        isActive: Boolean
    ) = FixedExpense(
        id = id,
        title = title,
        amount = 10_000L,
        dayOfMonth = 5,
        startMonth = startMonth,
        endMonth = endMonth,
        categoryId = null,
        paymentAccountId = null,
        note = null,
        isActive = isActive
    )

    private class FakeFixedExpenseRepository(
        fixedExpenses: List<FixedExpense>
    ) : FixedExpenseRepository {
        private val values = MutableStateFlow(fixedExpenses)

        override fun observeAllFixedExpenses(): Flow<List<FixedExpense>> = values

        override fun observeActiveFixedExpenses(): Flow<List<FixedExpense>> {
            return MutableStateFlow(values.value.filter { it.isActive })
        }

        override suspend fun upsertFixedExpense(expense: FixedExpense) = Unit

        override suspend fun deleteFixedExpense(expense: FixedExpense) = Unit
    }
}
