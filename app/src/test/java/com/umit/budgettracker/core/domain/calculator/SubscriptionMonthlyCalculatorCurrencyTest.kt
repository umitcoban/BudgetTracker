package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.CategoryType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import com.umit.budgettracker.core.network.ExchangeRateResult
import com.umit.budgettracker.core.network.ExchangeRateService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class SubscriptionMonthlyCalculatorCurrencyTest {
    @Test
    fun getPaymentsForMonth_keepsHistoricalTryPriceWhenSubscriptionCurrencyChangesToEur() = runBlocking {
        val subscription = Subscription(
            id = 1L,
            title = "Streaming",
            categoryId = 1L,
            paymentAccountId = 1L,
            billingDay = 5,
            isActive = true,
            note = null,
            originalCurrency = "EUR",
            exchangeRateToTry = 40_0000L,
            exchangeRateScale = 10_000,
            exchangeRateSource = "MANUAL",
            exchangeRateUpdatedAt = 1L
        )
        val histories = listOf(
            SubscriptionPriceHistory(
                id = 1L,
                subscriptionId = 1L,
                amount = 50_000L,
                effectiveFromMonth = YearMonth.of(2026, 5),
                originalCurrency = "TRY"
            ),
            SubscriptionPriceHistory(
                id = 2L,
                subscriptionId = 1L,
                amount = 800L,
                effectiveFromMonth = YearMonth.of(2026, 7),
                originalCurrency = "EUR",
                exchangeRateToTry = 40_0000L,
                exchangeRateScale = 10_000,
                exchangeRateSource = "MANUAL",
                exchangeRateUpdatedAt = 1L
            )
        )
        val calculator = SubscriptionMonthlyCalculator(
            subscriptionRepository = FakeSubscriptionRepository(subscription, histories),
            categoryRepository = FakeCategoryRepository(),
            accountRepository = FakePaymentAccountRepository(),
            expenseRepository = FakeExpenseRepository(),
            exchangeRateService = FakeExchangeRateService()
        )

        val mayPayment = calculator.getPaymentsForMonth(YearMonth.of(2026, 5)).first().single()
        val julyPayment = calculator.getPaymentsForMonth(YearMonth.of(2026, 7)).first().single()

        assertEquals(50_000L, mayPayment.amount)
        assertEquals(null, mayPayment.originalCurrency)
        assertEquals(32_000L, julyPayment.amount)
        assertEquals(800L, julyPayment.originalAmount)
        assertEquals("EUR", julyPayment.originalCurrency)
    }

    private class FakeSubscriptionRepository(
        private val subscription: Subscription,
        private val histories: List<SubscriptionPriceHistory>
    ) : SubscriptionRepository {
        override fun observeActiveSubscriptions(): Flow<List<Subscription>> = flowOf(listOf(subscription))
        override fun observeAllSubscriptions(): Flow<List<Subscription>> = flowOf(listOf(subscription))
        override fun observeSubscriptionById(id: Long): Flow<Subscription?> = flowOf(subscription.takeIf { it.id == id })
        override fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistory>> = flowOf(histories.filter { it.subscriptionId == subscriptionId })
        override fun observeAllPriceHistory(): Flow<List<SubscriptionPriceHistory>> = flowOf(histories)
        override suspend fun upsertSubscription(subscription: Subscription) = Unit
        override suspend fun backfillMissingPriceHistoryCurrency(subscription: Subscription) = Unit
        override suspend fun addPriceHistory(history: SubscriptionPriceHistory) = Unit
        override suspend fun createSubscriptionWithPrice(subscription: Subscription, initialAmount: Long, startMonth: YearMonth) = Unit
        override suspend fun deactivateSubscription(id: Long) = Unit
        override suspend fun deleteSubscription(id: Long) = Unit
    }

    private class FakeCategoryRepository : CategoryRepository {
        override fun observeActiveCategories(): Flow<List<Category>> {
            return flowOf(listOf(Category(1L, "Abonelik", "subscriptions", 0, CategoryType.EXPENSE, true, true, 0)))
        }
        override fun observeAllCategories(): Flow<List<Category>> = observeActiveCategories()
        override suspend fun getCategoryById(id: Long): Category? = null
        override suspend fun upsertCategory(category: Category) = Unit
        override suspend fun deleteCategory(category: Category) = Unit
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        override fun observeActiveAccounts(): Flow<List<PaymentAccount>> {
            return flowOf(listOf(PaymentAccount(1L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)))
        }
        override suspend fun getAccountById(id: Long): PaymentAccount? = null
    }

    private class FakeExpenseRepository : ExpenseRepository {
        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> = flowOf(emptyList())
        override fun observeAllExpenses(): Flow<List<Expense>> = flowOf(emptyList())
        override suspend fun getExpenseById(id: Long): Expense? = null
        override suspend fun insertExpense(expense: Expense) = Unit
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) = Unit
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean = false
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean = false
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean = false
    }

    private class FakeExchangeRateService : ExchangeRateService() {
        override suspend fun fetchRateToTry(currency: String): Result<ExchangeRateResult> {
            return Result.failure(IllegalStateException("No network in unit test"))
        }
    }
}
