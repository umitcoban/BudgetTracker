package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.SalaryRules
import com.umit.budgettracker.core.domain.model.FixedExpense
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.repository.FixedExpenseRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject

/**
 * Turns a confirmed "zam planı" into real planning rules without touching past months:
 * - salary: a new effective-month rule (or an update of the rule starting that month);
 * - fixed expense (e.g. rent): the current record ends the month before, a new one starts at
 *   [fromMonth] with the raised amount. Already processed expenses keep pointing at the old record.
 */
class ApplyRaiseUseCase @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val fixedExpenseRepository: FixedExpenseRepository
) {
    suspend fun salary(percent: Int, fromMonth: YearMonth): SalaryRule? {
        val rules = salaryRepository.observeAllSalaryRules().first()
        val base = SalaryRules.effectiveForMonth(rules, fromMonth) ?: return null
        val raised = base.amount + base.amount * percent / 100L
        val rule = SalaryRule(
            id = SalaryRules.idForSave(base, fromMonth),
            amount = raised,
            effectiveStartMonth = fromMonth,
            note = "Zam planı %$percent",
            payDay = base.payDay
        )
        salaryRepository.upsertSalaryRule(rule)
        return rule
    }

    suspend fun fixedExpense(expense: FixedExpense, percent: Int, fromMonth: YearMonth): Long {
        val raised = expense.amount + expense.amount * percent / 100L
        if (!fromMonth.isAfter(expense.startMonth)) {
            fixedExpenseRepository.upsertFixedExpense(expense.copy(amount = raised))
            return raised
        }
        fixedExpenseRepository.upsertFixedExpense(expense.copy(endMonth = fromMonth.minusMonths(1)))
        fixedExpenseRepository.upsertFixedExpense(
            expense.copy(
                id = 0L,
                amount = raised,
                startMonth = fromMonth,
                endMonth = expense.endMonth?.takeIf { !it.isBefore(fromMonth) },
                note = listOfNotNull(expense.note, "Zam %$percent").joinToString(" · ")
            )
        )
        return raised
    }
}
