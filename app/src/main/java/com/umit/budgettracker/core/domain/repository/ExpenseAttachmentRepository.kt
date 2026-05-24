package com.umit.budgettracker.core.domain.repository

import android.net.Uri
import com.umit.budgettracker.core.domain.model.ExpenseAttachment
import kotlinx.coroutines.flow.Flow

interface ExpenseAttachmentRepository {
    fun observeForExpense(expenseId: Long): Flow<List<ExpenseAttachment>>
    suspend fun addReceiptPhoto(expenseId: Long, sourceUri: Uri): ExpenseAttachment?
    suspend fun deleteAttachment(attachment: ExpenseAttachment)
    suspend fun getAllAttachments(): List<ExpenseAttachment>
}
