package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.InstallmentGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InstallmentRulesTest {
    private val group = InstallmentGroup(
        id = 1L,
        title = "Telefon",
        totalAmount = 100_001L,
        installmentCount = 3,
        startDate = LocalDate.of(2026, 7, 10),
        categoryId = 8L,
        paymentAccountId = 3L,
        note = null
    )

    @Test
    fun installmentAmounts_putTheRemainderOnTheFirstInstalmentLikeTheGenerator() {
        assertEquals(listOf(33_335L, 33_333L, 33_333L), InstallmentRules.installmentAmounts(100_001L, 3))
    }

    @Test
    fun progress_countsInstalmentsOnOrBeforeTodayAndSumsTheRest() {
        val progress = InstallmentRules.progress(group, today = LocalDate.of(2026, 8, 10))

        assertEquals(2, progress.elapsedCount)
        assertEquals(1, progress.remainingCount)
        assertEquals(33_333L, progress.remainingAmount)
        assertEquals(LocalDate.of(2026, 9, 10), progress.nextInstallmentDate)
    }

    @Test
    fun progress_isCompletedAfterTheLastInstalment() {
        val progress = InstallmentRules.progress(group, today = LocalDate.of(2026, 9, 10))

        assertTrue(progress.isCompleted)
        assertEquals(0L, progress.remainingAmount)
        assertNull(progress.nextInstallmentDate)
    }
}
