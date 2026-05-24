package com.umit.budgettracker.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ExpenseWithDetails(
    @Embedded val expense: ExpenseEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "paymentAccountId",
        entityColumn = "id"
    )
    val account: PaymentAccountEntity?
)
