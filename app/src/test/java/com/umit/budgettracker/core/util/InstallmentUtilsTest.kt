package com.umit.budgettracker.core.util

import com.umit.budgettracker.core.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InstallmentUtilsTest {

    @Test
    fun testSplitting() {
        val expenses = InstallmentUtils.generateInstallmentExpenses(
            title = "Phone",
            totalAmount = 10000L,
            count = 3,
            startDate = LocalDate.of(2026, 5, 1),
            categoryId = 1,
            paymentAccountId = 1,
            paymentSourceType = AccountType.CASH,
            note = null
        )

        assertEquals(3, expenses.size)
        assertEquals(3334L, expenses[0].amount)
        assertEquals(3333L, expenses[1].amount)
        assertEquals(3333L, expenses[2].amount)
        assertEquals(10000L, expenses.sumOf { it.amount })
        
        assertEquals(LocalDate.of(2026, 5, 1), expenses[0].expenseDate)
        assertEquals(LocalDate.of(2026, 6, 1), expenses[1].expenseDate)
        assertEquals(LocalDate.of(2026, 7, 1), expenses[2].expenseDate)
    }
}
