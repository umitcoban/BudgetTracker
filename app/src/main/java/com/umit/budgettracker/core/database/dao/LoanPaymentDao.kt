package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.umit.budgettracker.core.database.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanPaymentDao {
    @Query("SELECT * FROM loan_payments ORDER BY paymentMonth DESC, paidAt DESC")
    fun getAll(): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loan_payments WHERE paymentMonth = :paymentMonth")
    fun getForMonth(paymentMonth: String): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId AND paymentMonth = :paymentMonth LIMIT 1")
    suspend fun getByLoanAndMonth(loanId: Long, paymentMonth: String): LoanPaymentEntity?

    @Query("SELECT COUNT(*) FROM loan_payments WHERE loanId = :loanId")
    suspend fun countByLoanId(loanId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: LoanPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(payments: List<LoanPaymentEntity>)
}
