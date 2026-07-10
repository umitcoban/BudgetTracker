package com.umit.budgettracker.core.database.repository

import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toDomain
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PaymentAccountRepositoryImpl @Inject constructor(
    private val dao: PaymentAccountDao
) : PaymentAccountRepository {
    override fun observeAllAccounts(): Flow<List<PaymentAccount>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeActiveAccounts(): Flow<List<PaymentAccount>> {
        return dao.getAllActive().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAccountById(id: Long): PaymentAccount? {
        return dao.getById(id)?.toDomain()
    }
}
