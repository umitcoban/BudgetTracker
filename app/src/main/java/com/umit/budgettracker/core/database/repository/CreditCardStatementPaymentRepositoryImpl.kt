package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.CreditCardStatementPaymentDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.database.mapper.toEntity
import com.umit.budgettracker.core.domain.model.CreditCardStatementPayment
import com.umit.budgettracker.core.domain.repository.CreditCardStatementPaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject

class CreditCardStatementPaymentRepositoryImpl @Inject constructor(
    private val dao: CreditCardStatementPaymentDao
) : CreditCardStatementPaymentRepository {

    override fun observeAllPayments(): Flow<List<CreditCardStatementPayment>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observePaymentsForMonth(paymentMonth: YearMonth): Flow<List<CreditCardStatementPayment>> {
        return dao.getByMonth(paymentMonth.toString()).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun setStatementPaid(accountId: Long, paymentMonth: YearMonth, amount: Long) {
        val existing = dao.getByAccountAndMonth(accountId, paymentMonth.toString())?.toDomain()
        dao.insert(
            CreditCardStatementPayment(
                id = existing?.id ?: 0,
                accountId = accountId,
                paymentMonth = paymentMonth,
                amountAtPayment = amount,
                isPaid = true,
                paidAt = Instant.now().toEpochMilli()
            ).toEntity()
        )
    }

    override suspend fun setStatementUnpaid(accountId: Long, paymentMonth: YearMonth) {
        val existing = dao.getByAccountAndMonth(accountId, paymentMonth.toString())?.toDomain()
        dao.insert(
            CreditCardStatementPayment(
                id = existing?.id ?: 0,
                accountId = accountId,
                paymentMonth = paymentMonth,
                amountAtPayment = existing?.amountAtPayment ?: 0L,
                isPaid = false,
                paidAt = null
            ).toEntity()
        )
    }
}
