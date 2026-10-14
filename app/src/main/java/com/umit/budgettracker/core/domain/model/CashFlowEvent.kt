package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

data class CashFlowEvent(
    val date: LocalDate,
    val title: String,
    val amount: Long,
    val type: CashFlowEventType,
    val sourceId: Long?,
    val description: String?,
    /** Set when [date] is a weekend / public holiday and the bank will settle on a later day. */
    val bankDayNote: String? = null
)

enum class CashFlowEventType {
    INCOME, EXPENSE, CREDIT_CARD_PAYMENT, INSTALLMENT, SUBSCRIPTION, LOAN, FIXED_EXPENSE, PLANNED_EXPENSE;

    /** Planned movements have not happened yet; actual ones are already reflected in balances. */
    val isPlanned: Boolean get() = this in setOf(CREDIT_CARD_PAYMENT, SUBSCRIPTION, LOAN, FIXED_EXPENSE, PLANNED_EXPENSE)
}

/** Where the projected balance is lowest in the coming days, for the "akıllı nakit uyarısı". */
data class CashForecast(
    val startBalance: Long,
    val lowestBalance: Long,
    val lowestDate: LocalDate,
    /** The outflow that pushes the balance to its lowest point. */
    val pressureEvent: CashFlowEvent?,
    /** The first inflow after the lowest point, if any. */
    val nextIncome: CashFlowEvent?,
    val horizonEnd: LocalDate
) {
    val shortfall: Long get() = (-lowestBalance).coerceAtLeast(0L)
}
