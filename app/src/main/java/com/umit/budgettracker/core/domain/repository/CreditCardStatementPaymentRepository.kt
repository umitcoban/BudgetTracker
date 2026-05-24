package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.CreditCardStatementPayment
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface CreditCardStatementPaymentRepository {
    fun observeAllPayments(): Flow<List<CreditCardStatementPayment>>
    fun observePaymentsForMonth(paymentMonth: YearMonth): Flow<List<CreditCardStatementPayment>>
    suspend fun setStatementPaid(accountId: Long, paymentMonth: YearMonth, amount: Long)
    suspend fun setStatementUnpaid(accountId: Long, paymentMonth: YearMonth)
}
