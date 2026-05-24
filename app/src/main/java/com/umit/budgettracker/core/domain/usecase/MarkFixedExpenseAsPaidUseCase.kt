package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import java.time.YearMonth
import javax.inject.Inject

class MarkFixedExpenseAsPaidUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository
) {
    suspend operator fun invoke(
        fixedExpense: FixedExpense,
        month: YearMonth
    ): MarkFixedExpenseAsPaidResult {
        if (!fixedExpense.isActive || month.isBefore(fixedExpense.startMonth) || fixedExpense.endMonth?.let { month.isAfter(it) } == true) {
            return MarkFixedExpenseAsPaidResult.NotApplicable
        }

        val categoryId = fixedExpense.categoryId ?: return MarkFixedExpenseAsPaidResult.MissingRequiredSelection
        val paymentAccountId = fixedExpense.paymentAccountId ?: return MarkFixedExpenseAsPaidResult.MissingRequiredSelection
        val accountType = fixedExpense.account?.type ?: return MarkFixedExpenseAsPaidResult.MissingRequiredSelection

        if (expenseRepository.hasFixedExpenseForMonth(fixedExpense.id, month)) {
            return MarkFixedExpenseAsPaidResult.AlreadyPaid
        }

        expenseRepository.insertExpense(
            Expense(
                id = 0,
                title = fixedExpense.title,
                amount = fixedExpense.amount,
                expenseDate = month.atDay(fixedExpense.dayOfMonth.coerceAtMost(month.lengthOfMonth())),
                categoryId = categoryId,
                paymentAccountId = paymentAccountId,
                paymentSourceType = accountType,
                note = "Sabit gider ödemesi",
                fixedExpenseId = fixedExpense.id
            )
        )

        return MarkFixedExpenseAsPaidResult.Created
    }
}

sealed interface MarkFixedExpenseAsPaidResult {
    data object Created : MarkFixedExpenseAsPaidResult
    data object AlreadyPaid : MarkFixedExpenseAsPaidResult
    data object MissingRequiredSelection : MarkFixedExpenseAsPaidResult
    data object NotApplicable : MarkFixedExpenseAsPaidResult
}
