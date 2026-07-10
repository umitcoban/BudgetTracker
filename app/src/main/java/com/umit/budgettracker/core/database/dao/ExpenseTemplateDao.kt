package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.ExpenseTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseTemplateDao {
    @Query("SELECT * FROM expense_templates")
    fun getAll(): Flow<List<ExpenseTemplateEntity>>

    @Query("SELECT * FROM expense_templates WHERE isActive = 1")
    fun getAllActive(): Flow<List<ExpenseTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: ExpenseTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<ExpenseTemplateEntity>)

    @Query("SELECT * FROM expense_templates WHERE id = :id")
    suspend fun getById(id: Long): ExpenseTemplateEntity?

    @Delete
    suspend fun delete(template: ExpenseTemplateEntity)
}
