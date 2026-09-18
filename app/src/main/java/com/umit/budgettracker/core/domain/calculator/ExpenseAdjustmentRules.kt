package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment

/** Refunds never overwrite the expense row; every financial total works from the net amount. */
object ExpenseAdjustmentRules {
    fun groupByExpense(adjustments: List<ExpenseAdjustment>): Map<Long, List<ExpenseAdjustment>> {
        return adjustments.groupBy { it.expenseId }
    }

    /** `amount` minus linked refunds, floored at zero. */
    fun netAmount(expense: Expense, adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>): Long {
        val adjustmentTotal = adjustmentsByExpenseId[expense.id].orEmpty().sumOf { it.amount }
        return (expense.amount - adjustmentTotal).coerceAtLeast(0L)
    }
}

fun Expense.netAmount(adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>): Long {
    return ExpenseAdjustmentRules.netAmount(this, adjustmentsByExpenseId)
}
