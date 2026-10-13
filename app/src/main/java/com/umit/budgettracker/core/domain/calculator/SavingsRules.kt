package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.EmergencyFundStatus
import com.umit.budgettracker.core.domain.model.GoalAllocation
import com.umit.budgettracker.core.domain.model.GoalContribution
import com.umit.budgettracker.core.domain.model.GoalProgress
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.PlannedExpense
import com.umit.budgettracker.core.domain.model.SavingsGoal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Purpose-bound savings goals. Read-only maths: nothing here moves money. */
object SavingsGoalRules {
    /** Months of contribution history averaged for the "current pace". */
    const val PACE_MONTHS = 3

    fun progress(
        goal: SavingsGoal,
        contributions: List<GoalContribution>,
        linkedAssetValue: Long?,
        today: LocalDate
    ): GoalProgress {
        val own = contributions.filter { it.goalId == goal.id }
        val saved = (linkedAssetValue ?: own.sumOf { it.amount }).coerceAtLeast(0L)
        val remaining = (goal.targetAmount - saved).coerceAtLeast(0L)
        val percent = if (goal.targetAmount > 0L) (saved * 100L / goal.targetAmount).coerceIn(0L, 100L).toInt() else 0
        val currentMonth = YearMonth.from(today)
        val monthsLeft = goal.targetMonth?.let { target ->
            (ChronoUnit.MONTHS.between(currentMonth, target) + 1).toInt().coerceAtLeast(0)
        }
        val requiredMonthly = monthsLeft?.let { months ->
            if (remaining == 0L) 0L else if (months <= 0) remaining else ceilDiv(remaining, months.toLong())
        }
        val paceStart = currentMonth.minusMonths(PACE_MONTHS.toLong()).atDay(1)
        val averageMonthly = own
            .filter { !it.date.isBefore(paceStart) && !it.date.isAfter(today) }
            .sumOf { it.amount }
            .coerceAtLeast(0L) / PACE_MONTHS
        val completed = remaining == 0L && goal.targetAmount > 0L
        val projectedFinish = when {
            completed -> null
            averageMonthly <= 0L -> null
            else -> currentMonth.plusMonths(ceilDiv(remaining, averageMonthly) - 1)
        }
        val delay = if (projectedFinish != null && goal.targetMonth != null) {
            ChronoUnit.MONTHS.between(goal.targetMonth, projectedFinish).toInt()
        } else {
            null
        }
        return GoalProgress(
            goal = goal,
            savedAmount = saved,
            remainingAmount = remaining,
            percent = percent,
            monthsLeft = monthsLeft,
            requiredMonthly = requiredMonthly,
            averageMonthly = averageMonthly,
            projectedFinish = projectedFinish,
            delayMonths = delay,
            isCompleted = completed
        )
    }

    /**
     * Splits [available] (e.g. this month's leftover) across unfinished active goals. Each goal
     * first asks for its required monthly amount (or 1/12 of what's left when it has no target
     * month); higher priority goals are served first, and a shortfall at one priority level is
     * shared proportionally. The result is only a suggestion; the user confirms each line.
     */
    fun suggestAllocation(available: Long, progresses: List<GoalProgress>): List<GoalAllocation> {
        if (available <= 0L) return emptyList()
        var left = available
        val result = mutableListOf<GoalAllocation>()
        progresses
            .filter { it.goal.isActive && !it.isCompleted && it.remainingAmount > 0L }
            .groupBy { it.goal.priority }
            .toSortedMap()
            .forEach { (_, group) ->
                if (left <= 0L) return@forEach
                val asks = group.associateWith { p ->
                    (p.requiredMonthly ?: ceilDiv(p.remainingAmount, 12L)).coerceAtMost(p.remainingAmount).coerceAtLeast(0L)
                }
                val totalAsk = asks.values.sum()
                if (totalAsk <= 0L) return@forEach
                if (totalAsk <= left) {
                    asks.forEach { (p, ask) -> if (ask > 0L) result += GoalAllocation(p.goal.id, p.goal.name, ask) }
                    left -= totalAsk
                } else {
                    var given = 0L
                    val entries = asks.entries.toList()
                    entries.forEachIndexed { index, (p, ask) ->
                        val share = if (index == entries.lastIndex) left - given else ask * left / totalAsk
                        if (share > 0L) result += GoalAllocation(p.goal.id, p.goal.name, share)
                        given += share
                    }
                    left = 0L
                }
            }
        return result
    }

    fun ceilDiv(value: Long, divisor: Long): Long = if (divisor <= 0L) value else (value + divisor - 1) / divisor
}

/** "Birikimin kaç ay yeter?" over the essential spending of recent months. */
object EmergencyFundRules {
    const val DEFAULT_TARGET_MONTHS = 6
    const val AVERAGE_MONTHS = 3

    /**
     * Average of [MonthlyBudgetSummary.essentialExpenseAmount] over the last [AVERAGE_MONTHS]
     * months that have any essential spending (so a brand-new install isn't averaged with zeros).
     */
    fun essentialMonthlyAverage(summaries: List<MonthlyBudgetSummary>): Long {
        val relevant = summaries.takeLast(AVERAGE_MONTHS).filter { it.essentialExpenseAmount > 0L }
        if (relevant.isEmpty()) return 0L
        return relevant.sumOf { it.essentialExpenseAmount } / relevant.size
    }

    fun status(essentialMonthly: Long, fundAmount: Long, targetMonths: Int = DEFAULT_TARGET_MONTHS): EmergencyFundStatus {
        val target = essentialMonthly * targetMonths
        return EmergencyFundStatus(
            essentialMonthlyExpense = essentialMonthly,
            fundAmount = fundAmount,
            coveredMonthsTenths = if (essentialMonthly > 0L) (fundAmount * 10L / essentialMonthly).toInt() else null,
            targetMonths = targetMonths,
            targetAmount = target,
            shortfall = (target - fundAmount).coerceAtLeast(0L)
        )
    }
}

/** Reserve planning for dated large costs. */
object PlannedExpenseRules {
    /** Months from [currentMonth] to the due month, both inclusive (at least 1). */
    fun monthsUntil(item: PlannedExpense, currentMonth: YearMonth): Int {
        val due = YearMonth.from(item.dueDate)
        return (ChronoUnit.MONTHS.between(currentMonth, due) + 1).toInt().coerceAtLeast(1)
    }

    fun remaining(item: PlannedExpense): Long = (item.amount - item.savedAmount).coerceAtLeast(0L)

    /** "ayda Y ayır": remaining spread over the months left, rounded up to whole kuruş. */
    fun monthlyReserve(item: PlannedExpense, currentMonth: YearMonth): Long {
        if (item.isCompleted) return 0L
        val due = YearMonth.from(item.dueDate)
        if (due.isBefore(currentMonth)) return 0L
        return SavingsGoalRules.ceilDiv(remaining(item), monthsUntil(item, currentMonth).toLong())
    }

    fun totalMonthlyReserve(items: List<PlannedExpense>, currentMonth: YearMonth): Long =
        items.sumOf { monthlyReserve(it, currentMonth) }

    /** Next due date for a yearly item once the current one is done. */
    fun nextOccurrence(item: PlannedExpense): PlannedExpense = item.copy(
        dueDate = item.dueDate.plusYears(1),
        savedAmount = 0L,
        isCompleted = false
    )
}
