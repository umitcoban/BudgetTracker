package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.Subscription
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.model.SubscriptionPriceHistory
import com.umit.budgettracker.core.network.ExchangeRateResult
import com.umit.budgettracker.core.network.ExchangeRateService
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import javax.inject.Inject

/** Everything except expenses that a monthly subscription projection needs; month-independent. */
data class SubscriptionInputs(
    val subscriptions: List<Subscription>,
    val priceHistory: List<SubscriptionPriceHistory>,
    val categories: List<Category>,
    val accounts: List<PaymentAccount>
)

class SubscriptionMonthlyCalculator @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val exchangeRateService: ExchangeRateService
) {
    fun getPaymentsForMonth(month: YearMonth): Flow<List<SubscriptionMonthlyPayment>> {
        return combine(
            observeInputs(),
            expenseRepository.observeExpensesForMonth(month)
        ) { inputs, expenses ->
            val rates = resolveRates(inputs, listOf(month))
            calculatePayments(month, inputs, expenses, rates)
        }
    }

    fun observeInputs(): Flow<SubscriptionInputs> {
        return combine(
            subscriptionRepository.observeAllSubscriptions(),
            subscriptionRepository.observeAllPriceHistory(),
            categoryRepository.observeActiveCategories(),
            accountRepository.observeActiveAccounts()
        ) { subs, histories, cats, accounts ->
            SubscriptionInputs(subs, histories, cats, accounts)
        }
    }

    /**
     * Fetches the live TRY rate once per distinct foreign currency used by a subscription that
     * contributes to any of [months]. Failed lookups are simply absent from the result so callers
     * fall back to the stored rate on the price history / subscription.
     */
    suspend fun resolveRates(
        inputs: SubscriptionInputs,
        months: List<YearMonth>
    ): Map<String, ExchangeRateResult> {
        val currencies = months
            .flatMap { month ->
                inputs.subscriptions
                    .filter { SubscriptionRules.contributesToMonth(it, inputs.priceHistory, month) }
                    .map { sub -> currencyFor(sub, inputs.priceHistory, month) }
            }
            .filter { it != "TRY" }
            .toSet()
        return currencies.mapNotNull { currency ->
            exchangeRateService.fetchRateToTry(currency).getOrNull()?.let { currency to it }
        }.toMap()
    }

    /**
     * Pure per-month projection. [monthExpenses] must be the calendar-month expenses of [month];
     * a subscription counts as paid when one of them references it.
     */
    fun calculatePayments(
        month: YearMonth,
        inputs: SubscriptionInputs,
        monthExpenses: List<Expense>,
        rates: Map<String, ExchangeRateResult>
    ): List<SubscriptionMonthlyPayment> {
        val paidSubscriptionIds = monthExpenses.mapNotNull { it.subscriptionId }.toSet()
        return inputs.subscriptions.filter { sub ->
            SubscriptionRules.contributesToMonth(sub, inputs.priceHistory, month)
        }.map { sub ->
            val price = SubscriptionRules.priceEntryForMonth(sub.id, inputs.priceHistory, month)
            val originalAmount = price?.amount ?: 0L
            val currency = currencyFor(sub, inputs.priceHistory, month)
            val latestRate = if (currency == "TRY") null else rates[currency]
            val rateToTry = latestRate?.rateToTry ?: price?.exchangeRateToTry ?: sub.exchangeRateToTry
            val rateScale = latestRate?.rateScale ?: price?.exchangeRateScale ?: sub.exchangeRateScale
            val amount = if (currency == "TRY") {
                originalAmount
            } else {
                calculateTryMinorAmount(originalAmount, rateToTry, rateScale)
            }

            SubscriptionMonthlyPayment(
                subscriptionId = sub.id,
                title = sub.title,
                amount = amount,
                originalAmount = originalAmount,
                originalCurrency = currency.takeIf { it != "TRY" },
                exchangeRateToTry = rateToTry.takeIf { currency != "TRY" },
                exchangeRateScale = rateScale.takeIf { currency != "TRY" },
                exchangeRateSource = latestRate?.source ?: price?.exchangeRateSource ?: sub.exchangeRateSource,
                exchangeRateUpdatedAt = System.currentTimeMillis().takeIf { latestRate != null } ?: price?.exchangeRateUpdatedAt ?: sub.exchangeRateUpdatedAt,
                billingDay = sub.billingDay,
                categoryId = sub.categoryId,
                paymentAccountId = sub.paymentAccountId,
                isPaid = sub.id in paidSubscriptionIds,
                category = inputs.categories.find { it.id == sub.categoryId },
                account = inputs.accounts.find { it.id == sub.paymentAccountId }
            )
        }
    }

    private fun currencyFor(
        sub: Subscription,
        priceHistory: List<SubscriptionPriceHistory>,
        month: YearMonth
    ): String {
        val price = SubscriptionRules.priceEntryForMonth(sub.id, priceHistory, month)
        return price?.originalCurrency ?: sub.originalCurrency ?: "TRY"
    }
}

private fun calculateTryMinorAmount(originalAmount: Long, exchangeRateToTry: Long?, exchangeRateScale: Int?): Long {
    if (exchangeRateToTry == null || exchangeRateScale == null) return 0L
    return BigDecimal(originalAmount)
        .multiply(BigDecimal(exchangeRateToTry))
        .divide(BigDecimal(exchangeRateScale), 0, RoundingMode.HALF_UP)
        .longValueExact()
}
