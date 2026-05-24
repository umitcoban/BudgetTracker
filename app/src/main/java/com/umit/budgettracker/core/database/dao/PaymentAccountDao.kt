package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.PaymentAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAccountDao {
    @Query("SELECT * FROM payment_accounts WHERE isActive = 1")
    fun getAllActive(): Flow<List<PaymentAccountEntity>>

    @Query("SELECT * FROM payment_accounts WHERE id = :id")
    suspend fun getById(id: Long): PaymentAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: PaymentAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<PaymentAccountEntity>)

    @Update
    suspend fun update(account: PaymentAccountEntity)

    @Delete
    suspend fun delete(account: PaymentAccountEntity)
}
