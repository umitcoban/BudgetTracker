package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class Subscription(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val paymentAccountId: Long,
    val billingDay: Int,
    val isActive: Boolean,
    val note: String?,
    val cancelledFromMonth: YearMonth? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null,
    val category: Category? = null,
    val account: PaymentAccount? = null
)

data class SubscriptionPriceHistory(
    val id: Long,
    val subscriptionId: Long,
    val amount: Long,
    val effectiveFromMonth: YearMonth,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null
)

data class SubscriptionMonthlyPayment(
    val subscriptionId: Long,
    val title: String,
    val amount: Long,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null,
    val billingDay: Int,
    val categoryId: Long,
    val paymentAccountId: Long,
    val isPaid: Boolean = false,
    val category: Category? = null,
    val account: PaymentAccount? = null
)

enum class SubscriptionPaymentStatus {
    PLANNED,
    PAID
}
