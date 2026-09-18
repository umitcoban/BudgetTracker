package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.BudgetWarning
import com.umit.budgettracker.core.domain.model.BudgetWarningType
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/** One credit card's statement that falls due in a planning month. */
data class CreditCardPaymentDue(
    val accountId: Long,
    val accountName: String,
    val dueDate: LocalDate,
    val amount: Long,
    val isPaid: Boolean
)

/**
 * Builds the ordered warning list shown on the Dashboard. Ordering is by severity so callers
 * can safely show only the first entry:
 * negative remaining > exceeded category budget > card due > loan due > category at 80%.
 */
object BudgetWarningRules {
    const val UPCOMING_PAYMENT_WINDOW_DAYS = 7L
    const val CATEGORY_WARNING_THRESHOLD_PERCENT = 80L

    fun build(
        month: YearMonth,
        today: LocalDate,
        remainingAfterFixedPayments: Long,
        categorySummaries: List<CategorySummary>,
        cardPayments: List<CreditCardPaymentDue>,
        loanPayments: List<LoanMonthlyPayment>
    ): List<BudgetWarning> {
        val warnings = mutableListOf<BudgetWarning>()

        if (remainingAfterFixedPayments < 0L) {
            warnings += BudgetWarning(
                type = BudgetWarningType.NEGATIVE_REMAINING,
                message = "Planlanan ödemelerden sonra bu ay " +
                    "${MoneyFormatter.format(remainingAfterFixedPayments.absoluteValue)} açık oluşuyor."
            )
        }

        val budgeted = categorySummaries
            .filter { (it.budgetLimit ?: 0L) > 0L }
            .map { summary -> summary to usagePercent(summary.amount, summary.budgetLimit!!) }

        budgeted
            .filter { (summary, _) -> summary.amount > summary.budgetLimit!! }
            .sortedByDescending { (summary, _) -> summary.amount - summary.budgetLimit!! }
            .forEach { (summary, _) ->
                val limit = summary.budgetLimit!!
                warnings += BudgetWarning(
                    type = BudgetWarningType.CATEGORY_LIMIT_EXCEEDED,
                    message = "${summary.categoryName} bütçesi " +
                        "${MoneyFormatter.format(summary.amount - limit)} aşıldı " +
                        "(${MoneyFormatter.format(summary.amount)} / ${MoneyFormatter.format(limit)})."
                )
            }

        cardPayments
            .filter { !it.isPaid && it.amount > 0L && isDueSoonOrOverdue(it.dueDate, today, month) }
            .sortedBy { it.dueDate }
            .forEach { payment ->
                warnings += BudgetWarning(
                    type = BudgetWarningType.UPCOMING_CARD_PAYMENT,
                    message = "${payment.accountName} ekstresi ${dueLabel(payment.dueDate, today)}: " +
                        "${MoneyFormatter.format(payment.amount)}."
                )
            }

        loanPayments
            .map { it to month.atDay(it.paymentDay.coerceIn(1, month.lengthOfMonth())) }
            .filter { (payment, dueDate) -> payment.amount > 0L && isDueSoonOrOverdue(dueDate, today, month) }
            .sortedBy { (_, dueDate) -> dueDate }
            .forEach { (payment, dueDate) ->
                warnings += BudgetWarning(
                    type = BudgetWarningType.UPCOMING_LOAN_PAYMENT,
                    message = "${payment.title} kredi taksidi ${dueLabel(dueDate, today)}: " +
                        "${MoneyFormatter.format(payment.amount)}."
                )
            }

        budgeted
            .filter { (summary, percent) ->
                summary.amount <= summary.budgetLimit!! && percent >= CATEGORY_WARNING_THRESHOLD_PERCENT
            }
            .sortedByDescending { (_, percent) -> percent }
            .forEach { (summary, percent) ->
                warnings += BudgetWarning(
                    type = BudgetWarningType.CATEGORY_LIMIT_80_PERCENT,
                    message = "${summary.categoryName} bütçesinin %$percent'i kullanıldı " +
                        "(${MoneyFormatter.format(summary.amount)} / ${MoneyFormatter.format(summary.budgetLimit!!)})."
                )
            }

        return warnings
    }

    /** Integer percentage so no floating point touches money. */
    private fun usagePercent(spent: Long, limit: Long): Long = spent * 100L / limit

    /**
     * A payment is worth warning about only while the selected month is the one the user is
     * living in: due within the next [UPCOMING_PAYMENT_WINDOW_DAYS] days, or already past due
     * earlier in the same month.
     */
    private fun isDueSoonOrOverdue(dueDate: LocalDate, today: LocalDate, month: YearMonth): Boolean {
        if (YearMonth.from(today) != month) return false
        val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
        return daysUntilDue <= UPCOMING_PAYMENT_WINDOW_DAYS
    }

    private fun dueLabel(dueDate: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, dueDate)
        return when {
            days < 0L -> "için ödeme günü geçti"
            days == 0L -> "bugün ödenecek"
            days == 1L -> "yarın ödenecek"
            else -> "$days gün içinde ödenecek"
        }
    }
}
