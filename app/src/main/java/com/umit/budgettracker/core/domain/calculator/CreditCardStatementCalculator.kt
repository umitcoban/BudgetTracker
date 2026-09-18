package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.CreditCardStatementSummary
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.model.PaymentAccount
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class CreditCardStatementCalculator @Inject constructor() {

    fun calculateStatement(
        account: PaymentAccount,
        paymentMonth: YearMonth,
        allExpenses: List<Expense>,
        rules: List<CreditCardStatementRule> = emptyList(),
        adjustments: List<ExpenseAdjustment> = emptyList()
    ): CreditCardStatementSummary {
        val adjustmentsByExpenseId = ExpenseAdjustmentRules.groupByExpense(adjustments)
        val paymentRule = ruleForMonth(account, paymentMonth, rules)
        val statementDay = paymentRule.statementDay
        val dueDay = paymentRule.dueDay

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
        
        val statementRule = ruleForMonth(account, statementEndMonth, rules)
        val statementEndDate = statementEndMonth.atDay(clampDay(statementRule.statementDay, statementEndMonth))
        // The period starts the day after the PREVIOUS statement closed, using that month's own
        // rule — so a statement day that moves (11 -> 12) leaves no gap and no overlap.
        val previousEndMonth = statementEndMonth.minusMonths(1)
        val previousRule = ruleForMonth(account, previousEndMonth, rules)
        val statementStartDate = previousEndMonth
            .atDay(clampDay(previousRule.statementDay, previousEndMonth))
            .plusDays(1)

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
            totalAmount = includedExpenses.sumOf { it.netAmount(adjustmentsByExpenseId) },
            expenses = includedExpenses
        )
    }

    private fun clampDay(day: Int, month: YearMonth): Int {
        return day.coerceAtMost(month.lengthOfMonth())
    }

    private fun ruleForMonth(
        account: PaymentAccount,
        month: YearMonth,
        rules: List<CreditCardStatementRule>
    ): CreditCardStatementRule {
        return rules.filter { it.accountId == account.id && !it.effectiveFromMonth.isAfter(month) }
            .maxByOrNull { it.effectiveFromMonth }
            ?: CreditCardStatementRule(0, account.id, YearMonth.of(1, 1), account.statementDay ?: 1, account.dueDay ?: 1)
    }
}
