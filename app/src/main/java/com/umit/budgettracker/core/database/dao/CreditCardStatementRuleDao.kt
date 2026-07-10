package com.umit.budgettracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.umit.budgettracker.core.database.entity.CreditCardStatementRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardStatementRuleDao {
    @Query("SELECT * FROM credit_card_statement_rules")
    fun getAll(): Flow<List<CreditCardStatementRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CreditCardStatementRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<CreditCardStatementRuleEntity>)
}
