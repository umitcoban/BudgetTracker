package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SubscriptionRulesTest {
    @Test
    fun cancelledFromMonth_excludesCancellationMonthAndFuture() {
        val subscription = subscription(cancelledFromMonth = YearMonth.of(2026, 8))
        val histories = listOf(price(YearMonth.of(2026, 5), 20_000L))

        assertTrue(SubscriptionRules.contributesToMonth(subscription, histories, YearMonth.of(2026, 7)))
        assertFalse(SubscriptionRules.contributesToMonth(subscription, histories, YearMonth.of(2026, 8)))
        assertFalse(SubscriptionRules.contributesToMonth(subscription, histories, YearMonth.of(2026, 9)))
    }

    @Test
    fun priceForMonth_keepsOldPriceBeforeEffectiveMonth() {
        val histories = listOf(
            price(YearMonth.of(2026, 5), 20_000L),
            price(YearMonth.of(2026, 6), 25_000L)
        )

        assertEquals(20_000L, SubscriptionRules.priceForMonth(1L, histories, YearMonth.of(2026, 5)))
        assertEquals(25_000L, SubscriptionRules.priceForMonth(1L, histories, YearMonth.of(2026, 6)))
        assertEquals(25_000L, SubscriptionRules.priceForMonth(1L, histories, YearMonth.of(2026, 7)))
    }

    private fun subscription(cancelledFromMonth: YearMonth? = null) = Subscription(
        id = 1L,
        title = "Netflix",
        categoryId = 1L,
        paymentAccountId = 1L,
        billingDay = 10,
        isActive = true,
        note = null,
        cancelledFromMonth = cancelledFromMonth
    )

    private fun price(month: YearMonth, amount: Long) = SubscriptionPriceHistory(
        id = 0L,
        subscriptionId = 1L,
        amount = amount,
        effectiveFromMonth = month
    )
}
