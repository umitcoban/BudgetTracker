package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import java.time.YearMonth

object SubscriptionRules {
    fun contributesToMonth(
        subscription: Subscription,
        histories: List<SubscriptionPriceHistory>,
        month: YearMonth
    ): Boolean {
        return subscription.isActive &&
            (subscription.cancelledFromMonth == null || month.isBefore(subscription.cancelledFromMonth)) &&
            histories.any { it.subscriptionId == subscription.id && !it.effectiveFromMonth.isAfter(month) }
    }

    fun priceForMonth(
        subscriptionId: Long,
        histories: List<SubscriptionPriceHistory>,
        month: YearMonth
    ): Long? = priceEntryForMonth(subscriptionId, histories, month)?.amount

    fun priceEntryForMonth(
        subscriptionId: Long,
        histories: List<SubscriptionPriceHistory>,
        month: YearMonth
    ): SubscriptionPriceHistory? {
        return histories
            .filter { it.subscriptionId == subscriptionId && !it.effectiveFromMonth.isAfter(month) }
            .maxByOrNull { it.effectiveFromMonth }
    }
}
