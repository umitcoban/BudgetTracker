package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.InstallmentGroup
import java.time.LocalDate

/** Progress of an installment purchase as of a given day. Amounts are Long minor units. */
data class InstallmentProgress(
    val group: InstallmentGroup,
    val elapsedCount: Int,
    val remainingCount: Int,
    val remainingAmount: Long,
    val nextInstallmentDate: LocalDate?
) {
    val isCompleted: Boolean get() = remainingCount == 0
}

object InstallmentRules {
    /** Mirrors the generator: the first installment absorbs the division remainder. */
    fun installmentAmounts(totalAmount: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val base = totalAmount / count
        val remainder = totalAmount % count
        return List(count) { index -> if (index == 0) base + remainder else base }
    }

    fun installmentDate(group: InstallmentGroup, index: Int): LocalDate {
        return group.startDate.plusMonths(index.toLong())
    }

    /** An installment counts as elapsed once its date is on or before [today]. */
    fun progress(group: InstallmentGroup, today: LocalDate): InstallmentProgress {
        val amounts = installmentAmounts(group.totalAmount, group.installmentCount)
        val elapsed = amounts.indices.count { !installmentDate(group, it).isAfter(today) }
        val remaining = amounts.drop(elapsed)
        return InstallmentProgress(
            group = group,
            elapsedCount = elapsed,
            remainingCount = remaining.size,
            remainingAmount = remaining.sum(),
            nextInstallmentDate = if (remaining.isEmpty()) null else installmentDate(group, elapsed)
        )
    }
}
