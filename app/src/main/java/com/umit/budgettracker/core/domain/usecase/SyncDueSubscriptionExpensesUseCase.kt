package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.SubscriptionRules
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class SyncDueSubscriptionExpensesUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: PaymentAccountRepository
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
                    val amount = SubscriptionRules.priceForMonth(subscription.id, histories, month) ?: 0L
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
                                subscriptionId = subscription.id
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
