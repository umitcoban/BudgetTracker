package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.ExpenseEntity
import com.umit.budgettracker.core.database.entity.ExpenseWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Transaction
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAll(): Flow<List<ExpenseWithDetails>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE expenseDate BETWEEN :startDate AND :endDate ORDER BY expenseDate DESC")
    fun getExpensesForPeriod(startDate: Long, endDate: Long): Flow<List<ExpenseWithDetails>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseWithDetails?

    @Transaction
    @Query("SELECT * FROM expenses WHERE installmentGroupId = :groupId ORDER BY expenseDate ASC")
    fun getByInstallmentGroupId(groupId: Long): Flow<List<ExpenseWithDetails>>

    @Query("SELECT COUNT(*) FROM expenses WHERE subscriptionId = :subscriptionId AND expenseDate BETWEEN :startDate AND :endDate")
    suspend fun countSubscriptionExpensesForPeriod(subscriptionId: Long, startDate: Long, endDate: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE subscriptionId = :subscriptionId")
    suspend fun countBySubscriptionId(subscriptionId: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE fixedExpenseId = :fixedExpenseId AND expenseDate BETWEEN :startDate AND :endDate")
    suspend fun countFixedExpenseExpensesForPeriod(fixedExpenseId: Long, startDate: Long, endDate: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE installmentGroupId = :groupId")
    suspend fun deleteByInstallmentGroupId(groupId: Long)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}
