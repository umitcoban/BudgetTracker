package com.umit.budgettracker.core.database.dao

import androidx.room.*
import com.umit.budgettracker.core.database.entity.SalaryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryRuleDao {
    @Query("SELECT * FROM salary_rules ORDER BY effectiveStartMonth DESC")
    fun getAll(): Flow<List<SalaryRuleEntity>>

    @Query("SELECT * FROM salary_rules WHERE effectiveStartMonth = :effectiveStartMonth LIMIT 1")
    suspend fun getByEffectiveStartMonth(effectiveStartMonth: String): SalaryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SalaryRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<SalaryRuleEntity>)

    @Delete
    suspend fun delete(rule: SalaryRuleEntity)
}
