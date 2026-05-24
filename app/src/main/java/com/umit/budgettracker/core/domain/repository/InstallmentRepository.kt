package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.InstallmentGroup
import kotlinx.coroutines.flow.Flow

interface InstallmentRepository {
    fun observeInstallmentGroups(): Flow<List<InstallmentGroup>>
    suspend fun getInstallmentGroupById(id: Long): InstallmentGroup?
    suspend fun insertInstallmentGroup(group: InstallmentGroup, expenses: List<Expense>): Long
    suspend fun deleteInstallmentGroupWithGeneratedExpenses(groupId: Long)
    fun observeExpensesForInstallmentGroup(groupId: Long): Flow<List<Expense>>
}
