package com.umit.budgettracker.core.util

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import java.time.LocalDate

object InstallmentUtils {
    fun generateInstallmentExpenses(
        title: String,
        totalAmount: Long,
        count: Int,
        startDate: LocalDate,
        categoryId: Long,
        paymentAccountId: Long,
        paymentSourceType: AccountType,
        note: String?
    ): List<Expense> {
        val baseAmount = totalAmount / count
        val remainder = totalAmount % count
        
        return (1..count).map { i ->
            val installmentAmount = if (i == 1) baseAmount + remainder else baseAmount
            Expense(
                id = 0,
                title = "$title ($i/$count)",
                amount = installmentAmount,
                expenseDate = startDate.plusMonths((i - 1).toLong()),
                categoryId = categoryId,
                paymentAccountId = paymentAccountId,
                paymentSourceType = paymentSourceType,
                note = note
            )
        }
    }
}
