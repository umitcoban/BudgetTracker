package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.SubscriptionRules
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import com.umit.budgettracker.core.network.ExchangeRateService
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class SyncDueSubscriptionExpensesUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: PaymentAccountRepository,
    private val exchangeRateService: ExchangeRateService
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): SyncDueSubscriptionExpensesResult {
        val currentMonth = YearMonth.from(today)
        val subscriptions = subscriptionRepository.observeAllSubscriptions().first()
        val histories = subscriptionRepository.observeAllPriceHistory().first()
        var createdCount = 0

        subscriptions.forEach { subscription ->
            val firstMonth = histories
                .filter { it.subscriptionId == subscription.id }
                .minByOrNull { it.effectiveFromMonth }
                ?.effectiveFromMonth ?: return@forEach

            var month = firstMonth
            while (!month.isAfter(currentMonth)) {
                val dueDate = month.atDay(subscription.billingDay.coerceAtMost(month.lengthOfMonth()))
                if (dueDate.isAfter(today)) break

                if (SubscriptionRules.contributesToMonth(subscription, histories, month) &&
                    !expenseRepository.hasSubscriptionExpenseForMonth(subscription.id, month)
                ) {
                    val originalAmount = SubscriptionRules.priceForMonth(subscription.id, histories, month) ?: 0L
                    val currency = subscription.originalCurrency ?: "TRY"
                    val latestRate = if (currency == "TRY") {
                        null
                    } else {
                        exchangeRateService.fetchRateToTry(currency).getOrNull()
                    }
                    val rateToTry = latestRate?.rateToTry ?: subscription.exchangeRateToTry
                    val rateScale = latestRate?.rateScale ?: subscription.exchangeRateScale
                    val amount = if (currency == "TRY") {
                        originalAmount
                    } else {
                        calculateTryMinorAmount(originalAmount, rateToTry, rateScale)
                    }

                    if (amount > 0L) {
                        val account = accountRepository.getAccountById(subscription.paymentAccountId)
                        expenseRepository.insertExpense(
                            Expense(
                                id = 0,
                                title = subscription.title,
                                amount = amount,
                                expenseDate = dueDate,
                                categoryId = subscription.categoryId,
                                paymentAccountId = subscription.paymentAccountId,
                                paymentSourceType = account?.type ?: AccountType.CASH,
                                note = "Abonelik ödemesi",
                                subscriptionId = subscription.id,
                                originalAmount = originalAmount.takeIf { currency != "TRY" },
                                originalCurrency = currency.takeIf { currency != "TRY" },
                                exchangeRateToTry = rateToTry.takeIf { currency != "TRY" },
                                exchangeRateScale = rateScale.takeIf { currency != "TRY" },
                                exchangeRateSource = latestRate?.source ?: subscription.exchangeRateSource,
                                exchangeRateUpdatedAt = System.currentTimeMillis().takeIf { latestRate != null } ?: subscription.exchangeRateUpdatedAt
                            )
                        )
                        createdCount++
                    }
                }

                month = month.plusMonths(1)
            }
        }

        return SyncDueSubscriptionExpensesResult(createdCount)
    }
}

data class SyncDueSubscriptionExpensesResult(
    val createdCount: Int
)

private fun calculateTryMinorAmount(originalAmount: Long, exchangeRateToTry: Long?, exchangeRateScale: Int?): Long {
    if (exchangeRateToTry == null || exchangeRateScale == null) return 0L
    return BigDecimal(originalAmount)
        .multiply(BigDecimal(exchangeRateToTry))
        .divide(BigDecimal(exchangeRateScale), 0, RoundingMode.HALF_UP)
        .longValueExact()
}
