package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MarkSubscriptionPaymentAsPaidUseCaseTest {
    @Test
    fun invoke_createsOneExpenseAndSecondCallDoesNotDuplicate() = runBlocking {
        val repository = FakeExpenseRepository()
        val useCase = MarkSubscriptionPaymentAsPaidUseCase(repository)
        val payment = SubscriptionMonthlyPayment(
            subscriptionId = 10L,
            title = "Netflix",
            amount = 25_000L,
            billingDay = 31,
            categoryId = 2L,
            paymentAccountId = 3L
        )
        val month = YearMonth.of(2026, 2)

        assertTrue(useCase(payment, month) is MarkSubscriptionPaymentResult.Created)
        assertTrue(useCase(payment, month) is MarkSubscriptionPaymentResult.AlreadyPaid)

        assertEquals(1, repository.saved.size)
        assertEquals(month.atEndOfMonth(), repository.saved.single().expenseDate)
        assertEquals(10L, repository.saved.single().subscriptionId)
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val saved = mutableListOf<Expense>()

        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
            return flowOf(saved.filter { YearMonth.from(it.expenseDate) == yearMonth })
        }

        override fun observeAllExpenses(): Flow<List<Expense>> = flowOf(saved)
        override suspend fun getExpenseById(id: Long): Expense? = saved.firstOrNull { it.id == id }
        override suspend fun insertExpense(expense: Expense) {
            saved.add(expense.copy(id = saved.size + 1L, paymentSourceType = AccountType.CASH))
        }
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) {
            saved.removeIf { it.id == expense.id }
        }
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean {
            return saved.any { it.subscriptionId == subscriptionId && YearMonth.from(it.expenseDate) == yearMonth }
        }
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean {
            return saved.any { it.subscriptionId == subscriptionId }
        }
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean {
            return saved.any { it.fixedExpenseId == fixedExpenseId && YearMonth.from(it.expenseDate) == yearMonth }
        }
    }
}
