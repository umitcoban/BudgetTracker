package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
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

class SubscriptionMonthlyCalculator @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val exchangeRateService: ExchangeRateService
) {
    fun getPaymentsForMonth(month: YearMonth): Flow<List<SubscriptionMonthlyPayment>> {
        return combine(
            subscriptionRepository.observeAllSubscriptions(),
            subscriptionRepository.observeAllPriceHistory(),
            categoryRepository.observeActiveCategories(),
            accountRepository.observeActiveAccounts(),
            expenseRepository.observeExpensesForMonth(month)
        ) { subs, histories, cats, accounts, expenses ->
            subs.filter { sub ->
                SubscriptionRules.contributesToMonth(sub, histories, month)
            }.map { sub ->
                val isPaid = expenses.any { it.subscriptionId == sub.id }
                val price = SubscriptionRules.priceEntryForMonth(sub.id, histories, month)
                val originalAmount = price?.amount ?: 0L
                val currency = price?.originalCurrency ?: sub.originalCurrency ?: "TRY"
                val latestRate = if (currency == "TRY") {
                    null
                } else {
                    exchangeRateService.fetchRateToTry(currency).getOrNull()
                }
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
                    isPaid = isPaid,
                    category = cats.find { it.id == sub.categoryId },
                    account = accounts.find { it.id == sub.paymentAccountId }
                )
            }
        }
    }
}

private fun calculateTryMinorAmount(originalAmount: Long, exchangeRateToTry: Long?, exchangeRateScale: Int?): Long {
    if (exchangeRateToTry == null || exchangeRateScale == null) return 0L
    return BigDecimal(originalAmount)
        .multiply(BigDecimal(exchangeRateToTry))
        .divide(BigDecimal(exchangeRateScale), 0, RoundingMode.HALF_UP)
        .longValueExact()
}
