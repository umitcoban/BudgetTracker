package com.umit.budgettracker.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId")]
)
data class ExpenseAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val type: String, // RECEIPT_PHOTO, OTHER
    val localPath: String,
    val mimeType: String?,
    val originalFileName: String?,
    val createdAt: Long = System.currentTimeMillis()
)
