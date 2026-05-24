package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MarkFixedExpenseAsPaidUseCaseTest {
    @Test
    fun invoke_createsOneExpenseAndPreventsDuplicateForSameMonth() = runBlocking {
        val repository = FakeExpenseRepository()
        val useCase = MarkFixedExpenseAsPaidUseCase(repository)
        val fixedExpense = fixedExpense()
        val month = YearMonth.of(2026, 5)

        assertTrue(useCase(fixedExpense, month) is MarkFixedExpenseAsPaidResult.Created)
        assertTrue(useCase(fixedExpense, month) is MarkFixedExpenseAsPaidResult.AlreadyPaid)

        assertEquals(1, repository.saved.size)
        assertEquals(40L, repository.saved.single().fixedExpenseId)
        assertEquals("Sabit gider ödemesi", repository.saved.single().note)
        assertEquals(month.atDay(10), repository.saved.single().expenseDate)
    }

    private fun fixedExpense() = FixedExpense(
        id = 40L,
        title = "Kira",
        amount = 20_000L,
        dayOfMonth = 10,
        startMonth = YearMonth.of(2026, 1),
        endMonth = null,
        categoryId = 2L,
        paymentAccountId = 3L,
        note = null,
        isActive = true,
        account = PaymentAccount(
            id = 3L,
            name = "Banka",
            type = AccountType.BANK_ACCOUNT,
            statementDay = null,
            dueDay = null,
            isActive = true
        )
    )

    private class FakeExpenseRepository : ExpenseRepository {
        val saved = mutableListOf<Expense>()

        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
            return flowOf(saved.filter { YearMonth.from(it.expenseDate) == yearMonth })
        }

        override fun observeAllExpenses(): Flow<List<Expense>> = flowOf(saved)
        override suspend fun getExpenseById(id: Long): Expense? = saved.firstOrNull { it.id == id }
        override suspend fun insertExpense(expense: Expense) {
            saved.add(expense.copy(id = saved.size + 1L))
        }
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) {
            saved.removeIf { it.id == expense.id }
        }
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean = false
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean = false
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean {
            return saved.any { it.fixedExpenseId == fixedExpenseId && YearMonth.from(it.expenseDate) == yearMonth }
        }
    }
}
