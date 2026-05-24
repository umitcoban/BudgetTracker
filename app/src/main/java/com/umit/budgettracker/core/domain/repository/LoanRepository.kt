package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Loan
import kotlinx.coroutines.flow.Flow

interface LoanRepository {
    fun observeActiveLoans(): Flow<List<Loan>>
    fun observeAllLoans(): Flow<List<Loan>>
    fun observeLoanById(id: Long): Flow<Loan?>
    suspend fun upsertLoan(loan: Loan)
    suspend fun deactivateLoan(id: Long)
    suspend fun deleteLoan(id: Long)
}
