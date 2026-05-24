package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import java.time.YearMonth
import javax.inject.Inject

class MarkSubscriptionPaymentAsPaidUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    suspend operator fun invoke(
        payment: SubscriptionMonthlyPayment,
        month: YearMonth
    ): MarkSubscriptionPaymentResult {
        if (expenseRepository.hasSubscriptionExpenseForMonth(payment.subscriptionId, month)) {
            return MarkSubscriptionPaymentResult.AlreadyPaid
        }

        expenseRepository.insertExpense(
            Expense(
                id = 0,
                title = payment.title,
                amount = payment.amount,
                expenseDate = month.atDay(payment.billingDay.coerceAtMost(month.lengthOfMonth())),
                categoryId = payment.categoryId,
                paymentAccountId = payment.paymentAccountId,
                paymentSourceType = payment.account?.type ?: AccountType.CASH,
                note = "Abonelik ödemesi",
                subscriptionId = payment.subscriptionId
            )
        )

        return MarkSubscriptionPaymentResult.Created
    }
}

sealed interface MarkSubscriptionPaymentResult {
    data object Created : MarkSubscriptionPaymentResult
    data object AlreadyPaid : MarkSubscriptionPaymentResult
}
