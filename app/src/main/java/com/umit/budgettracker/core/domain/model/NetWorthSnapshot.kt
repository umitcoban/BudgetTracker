package com.umit.budgettracker.core.domain.model

import java.time.YearMonth

data class NetWorthSnapshot(
    val id: Long,
    val yearMonth: YearMonth,
    val cashAmount: Long,
    val bankAmount: Long,
    val investmentAmount: Long,
    val creditCardDebt: Long,
    val loanDebt: Long,
    val note: String?
) {
    val netWorth: Long get() = cashAmount + bankAmount + investmentAmount - creditCardDebt - loanDebt
}
