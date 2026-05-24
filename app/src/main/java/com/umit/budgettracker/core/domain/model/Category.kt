package com.umit.budgettracker.core.domain.model

data class Category(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorValue: Int,
    val type: CategoryType,
    val isDefault: Boolean,
    val isActive: Boolean,
    val sortOrder: Int
)

enum class CategoryType {
    EXPENSE, INCOME, SYSTEM
}
