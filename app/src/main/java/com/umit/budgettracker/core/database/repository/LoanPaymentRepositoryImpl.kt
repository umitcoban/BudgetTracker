package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.LoanPaymentDao
import com.umit.budgettracker.core.database.entity.LoanPaymentEntity
import com.umit.budgettracker.core.domain.model.LoanPayment
import com.umit.budgettracker.core.domain.repository.LoanPaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class LoanPaymentRepositoryImpl @Inject constructor(
    private val loanPaymentDao: LoanPaymentDao
) : LoanPaymentRepository {
    override fun observeAllPayments(): Flow<List<LoanPayment>> = loanPaymentDao.getAll().map { payments ->
        payments.map(LoanPaymentEntity::toDomain)
    }

    override fun observePaymentsForMonth(month: YearMonth): Flow<List<LoanPayment>> =
        loanPaymentDao.getForMonth(month.toString()).map { payments ->
            payments.map(LoanPaymentEntity::toDomain)
        }

    override suspend fun getPayment(loanId: Long, month: YearMonth): LoanPayment? {
        return loanPaymentDao.getByLoanAndMonth(loanId, month.toString())?.toDomain()
    }

    override suspend fun insertPayment(payment: LoanPayment) {
        loanPaymentDao.insert(payment.toEntity())
    }
}

private fun LoanPaymentEntity.toDomain() = LoanPayment(
    id = id,
    loanId = loanId,
    paymentMonth = YearMonth.parse(paymentMonth),
    amount = amount,
    paidAt = LocalDate.ofEpochDay(paidAt)
)

private fun LoanPayment.toEntity() = LoanPaymentEntity(
    id = id,
    loanId = loanId,
    paymentMonth = paymentMonth.toString(),
    amount = amount,
    paidAt = paidAt.toEpochDay()
)
