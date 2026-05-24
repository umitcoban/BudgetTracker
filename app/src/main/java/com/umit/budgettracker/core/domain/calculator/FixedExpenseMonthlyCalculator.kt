package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.FixedExpenseMonthlyPayment
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject

class FixedExpenseMonthlyCalculator @Inject constructor(
    private val repository: FixedExpenseRepository
) {
    fun getPaymentsForMonth(month: YearMonth): Flow<List<FixedExpenseMonthlyPayment>> {
        return repository.observeActiveFixedExpenses().map { fixedExpenses ->
            fixedExpenses
                .filter { expense ->
                    !month.isBefore(expense.startMonth) &&
                        (expense.endMonth == null || !month.isAfter(expense.endMonth))
                }
                .map { expense ->
                    FixedExpenseMonthlyPayment(
                        fixedExpenseId = expense.id,
                        title = expense.title,
                        amount = expense.amount,
                        dayOfMonth = expense.dayOfMonth,
                        categoryId = expense.categoryId,
                        paymentAccountId = expense.paymentAccountId,
                        category = expense.category,
                        account = expense.account
                    )
                }
        }
    }
}
