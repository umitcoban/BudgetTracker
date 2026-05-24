package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.ExpenseAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseAttachmentDao {
    @Query("SELECT * FROM expense_attachments WHERE expenseId = :expenseId")
    fun observeForExpense(expenseId: Long): Flow<List<ExpenseAttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: ExpenseAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<ExpenseAttachmentEntity>)

    @Delete
    suspend fun delete(attachment: ExpenseAttachmentEntity)

    @Query("DELETE FROM expense_attachments WHERE expenseId = :expenseId")
    suspend fun deleteForExpense(expenseId: Long)

    @Query("SELECT * FROM expense_attachments")
    suspend fun getAll(): List<ExpenseAttachmentEntity>
}
