package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SyncDueSubscriptionExpensesUseCaseTest {
    @Test
    fun invoke_createsDueRecurringExpensesAndDoesNotDuplicate() = runBlocking {
        val subscriptionRepository = FakeSubscriptionRepository()
        val expenseRepository = FakeExpenseRepository()
        val accountRepository = FakePaymentAccountRepository()
        val useCase = SyncDueSubscriptionExpensesUseCase(
            subscriptionRepository = subscriptionRepository,
            expenseRepository = expenseRepository,
            accountRepository = accountRepository
        )

        val first = useCase(LocalDate.of(2026, 5, 24))
        val second = useCase(LocalDate.of(2026, 5, 24))

        assertEquals(2, first.createdCount)
        assertEquals(0, second.createdCount)
        assertEquals(
            listOf(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 5, 4)),
            expenseRepository.saved.map { it.expenseDate }
        )
        assertEquals(listOf(12_000L, 12_000L), expenseRepository.saved.map { it.amount })
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val subscriptions = MutableStateFlow(
            listOf(
                Subscription(
                    id = 1L,
                    title = "Spotify",
                    categoryId = 2L,
                    paymentAccountId = 3L,
                    billingDay = 4,
                    isActive = true,
                    note = null
                )
            )
        )
        private val histories = MutableStateFlow(
            listOf(
                SubscriptionPriceHistory(
                    id = 1L,
                    subscriptionId = 1L,
                    amount = 12_000L,
                    effectiveFromMonth = YearMonth.of(2026, 4)
                )
            )
        )

        override fun observeActiveSubscriptions(): Flow<List<Subscription>> = subscriptions
        override fun observeAllSubscriptions(): Flow<List<Subscription>> = subscriptions
        override fun observeSubscriptionById(id: Long): Flow<Subscription?> = flowOf(subscriptions.value.firstOrNull { it.id == id })
        override fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistory>> {
            return flowOf(histories.value.filter { it.subscriptionId == subscriptionId })
        }
        override fun observeAllPriceHistory(): Flow<List<SubscriptionPriceHistory>> = histories
        override suspend fun upsertSubscription(subscription: Subscription) = Unit
        override suspend fun addPriceHistory(history: SubscriptionPriceHistory) = Unit
        override suspend fun createSubscriptionWithPrice(subscription: Subscription, initialAmount: Long, startMonth: YearMonth) = Unit
        override suspend fun deactivateSubscription(id: Long) = Unit
        override suspend fun deleteSubscription(id: Long) = Unit
    }

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
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean {
            return saved.any { it.subscriptionId == subscriptionId && YearMonth.from(it.expenseDate) == yearMonth }
        }
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean {
            return saved.any { it.subscriptionId == subscriptionId }
        }
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        override fun observeActiveAccounts(): Flow<List<PaymentAccount>> = flowOf(emptyList())
        override suspend fun getAccountById(id: Long): PaymentAccount {
            return PaymentAccount(id = id, name = "Banka", type = AccountType.BANK_ACCOUNT, statementDay = null, dueDay = null, isActive = true)
        }
    }
}
