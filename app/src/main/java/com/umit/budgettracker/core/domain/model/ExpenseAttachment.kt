package com.umit.budgettracker.core.domain.model

import java.time.Instant

data class ExpenseAttachment(
    val id: Long,
    val expenseId: Long,
    val type: ExpenseAttachmentType,
    val localPath: String,
    val mimeType: String?,
    val originalFileName: String?,
    val createdAt: Long
)

enum class ExpenseAttachmentType {
    RECEIPT_PHOTO, OTHER
}
