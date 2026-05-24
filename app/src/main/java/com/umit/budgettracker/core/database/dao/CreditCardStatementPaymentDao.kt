package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.umit.budgettracker.core.database.entity.CreditCardStatementPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardStatementPaymentDao {
    @Query("SELECT * FROM credit_card_statement_payments")
    fun getAll(): Flow<List<CreditCardStatementPaymentEntity>>

    @Query("SELECT * FROM credit_card_statement_payments WHERE paymentMonth = :paymentMonth")
    fun getByMonth(paymentMonth: String): Flow<List<CreditCardStatementPaymentEntity>>

    @Query("SELECT * FROM credit_card_statement_payments WHERE accountId = :accountId AND paymentMonth = :paymentMonth LIMIT 1")
    suspend fun getByAccountAndMonth(accountId: Long, paymentMonth: String): CreditCardStatementPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: CreditCardStatementPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<CreditCardStatementPaymentEntity>)
}
