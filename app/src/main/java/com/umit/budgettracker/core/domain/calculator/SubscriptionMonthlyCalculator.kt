package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import javax.inject.Inject

class SubscriptionMonthlyCalculator @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository
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

                SubscriptionMonthlyPayment(
                    subscriptionId = sub.id,
                    title = sub.title,
                    amount = SubscriptionRules.priceForMonth(sub.id, histories, month) ?: 0L,
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
