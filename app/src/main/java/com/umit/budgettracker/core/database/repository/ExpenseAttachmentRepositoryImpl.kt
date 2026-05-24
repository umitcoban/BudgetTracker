package com.umit.budgettracker.core.database.repository

import android.content.Context
import android.net.Uri
import com.umit.budgettracker.core.database.dao.ExpenseAttachmentDao
import com.umit.budgettracker.core.database.entity.ExpenseAttachmentEntity
import com.umit.budgettracker.core.domain.model.ExpenseAttachment
import com.umit.budgettracker.core.domain.model.ExpenseAttachmentType
import com.umit.budgettracker.core.domain.repository.ExpenseAttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

class ExpenseAttachmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ExpenseAttachmentDao
) : ExpenseAttachmentRepository {

    override fun observeForExpense(expenseId: Long): Flow<List<ExpenseAttachment>> {
        return dao.observeForExpense(expenseId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addReceiptPhoto(expenseId: Long, sourceUri: Uri): ExpenseAttachment? = withContext(Dispatchers.IO) {
        try {
            val attachmentsDir = File(context.filesDir, "attachments/expenses/$expenseId")
            if (!attachmentsDir.exists()) attachmentsDir.mkdirs()

            val fileName = "receipt_${System.currentTimeMillis()}.jpg"
            val destFile = File(attachmentsDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val entity = ExpenseAttachmentEntity(
                expenseId = expenseId,
                type = ExpenseAttachmentType.RECEIPT_PHOTO.name,
                localPath = "attachments/expenses/$expenseId/$fileName",
                mimeType = "image/jpeg",
                originalFileName = sourceUri.lastPathSegment
            )
            dao.insert(entity)
            entity.copy(id = 0).toDomain() // Simple conversion, ID will be refreshed on flow
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteAttachment(attachment: ExpenseAttachment) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, attachment.localPath)
        if (file.exists()) file.delete()
        dao.delete(attachment.toEntity())
    }

    override suspend fun getAllAttachments(): List<ExpenseAttachment> {
        return dao.getAll().map { it.toDomain() }
    }
}

// Mapper extension
private fun ExpenseAttachmentEntity.toDomain() = ExpenseAttachment(
    id = id,
    expenseId = expenseId,
    type = ExpenseAttachmentType.valueOf(type),
    localPath = localPath,
    mimeType = mimeType,
    originalFileName = originalFileName,
    createdAt = createdAt
)

private fun ExpenseAttachment.toEntity() = ExpenseAttachmentEntity(
    id = id,
    expenseId = expenseId,
    type = type.name,
    localPath = localPath,
    mimeType = mimeType,
    originalFileName = originalFileName,
    createdAt = createdAt
)
