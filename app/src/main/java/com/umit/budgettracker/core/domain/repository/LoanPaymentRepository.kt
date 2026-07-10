package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.LoanPayment
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface LoanPaymentRepository {
    fun observeAllPayments(): Flow<List<LoanPayment>>
    fun observePaymentsForMonth(month: YearMonth): Flow<List<LoanPayment>>
    suspend fun getPayment(loanId: Long, month: YearMonth): LoanPayment?
    suspend fun insertPayment(payment: LoanPayment)
}
