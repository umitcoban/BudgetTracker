package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun getAll(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets WHERE yearMonth = :yearMonth")
    fun getByMonth(yearMonth: String): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId AND yearMonth = :yearMonth ORDER BY id DESC LIMIT 1")
    suspend fun getByCategoryAndMonth(categoryId: Long, yearMonth: String): CategoryBudgetEntity?

    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId AND yearMonth = :yearMonth ORDER BY id DESC")
    suspend fun getAllByCategoryAndMonth(categoryId: Long, yearMonth: String): List<CategoryBudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: CategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<CategoryBudgetEntity>)

    @Delete
    suspend fun delete(budget: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
