package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.CreditCardStatementSummary
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.PaymentAccount
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class CreditCardStatementCalculator @Inject constructor() {

    fun calculateStatement(
        account: PaymentAccount,
        paymentMonth: YearMonth,
        allExpenses: List<Expense>
    ): CreditCardStatementSummary {
        val statementDay = account.statementDay ?: 1
        val dueDay = account.dueDay ?: 1

        // Determine due date in the selected paymentMonth
        var dueDate = paymentMonth.atDay(clampDay(dueDay, paymentMonth))
        
        // Find the statement end date that leads to this due date.
        // Rule: Statement end date is before the due date.
        // If dueDay <= statementDay, it means statement closed in the PREVIOUS month.
        
        val statementEndMonth = if (dueDay <= statementDay) {
            paymentMonth.minusMonths(1)
        } else {
            paymentMonth
        }
        
        val statementEndDate = statementEndMonth.atDay(clampDay(statementDay, statementEndMonth))
        val statementStartDate = statementEndDate.minusMonths(1).plusDays(1)

        val includedExpenses = allExpenses.filter {
            it.paymentAccountId == account.id &&
            (it.expenseDate.isAfter(statementStartDate.minusDays(1)) && 
             it.expenseDate.isBefore(statementEndDate.plusDays(1)))
        }

        return CreditCardStatementSummary(
            accountId = account.id,
            accountName = account.name,
            paymentMonth = paymentMonth,
            statementStartDate = statementStartDate,
            statementEndDate = statementEndDate,
            dueDate = dueDate,
            totalAmount = includedExpenses.sumOf { it.amount },
            expenses = includedExpenses
        )
    }

    private fun clampDay(day: Int, month: YearMonth): Int {
        return day.coerceAtMost(month.lengthOfMonth())
    }
}
