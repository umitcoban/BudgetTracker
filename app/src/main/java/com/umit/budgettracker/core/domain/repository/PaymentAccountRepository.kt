package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.PaymentAccount
import kotlinx.coroutines.flow.Flow

interface PaymentAccountRepository {
    fun observeActiveAccounts(): Flow<List<PaymentAccount>>
    suspend fun getAccountById(id: Long): PaymentAccount?
}
