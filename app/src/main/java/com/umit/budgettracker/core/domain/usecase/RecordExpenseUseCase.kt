package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.repository.AssistantRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.parsing.StatementMatcher
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Shared "create one expense" path for quick add, receipt scan, statement import and approved
 * notification drafts: resolves the account's source type, learns merchant → category, and
 * offers a duplicate check (same day, same amount, similar title) before saving.
 */
class RecordExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val accountRepository: PaymentAccountRepository,
    private val assistantRepository: AssistantRepository
) {
    suspend fun findDuplicate(date: LocalDate, amount: Long, title: String): Expense? {
        val monthExpenses = expenseRepository.observeExpensesForMonth(YearMonth.from(date)).first()
        return StatementMatcher.findDuplicate(date, amount, title, monthExpenses)
    }

    /** Returns the new expense id, or null when the account no longer exists. */
    suspend operator fun invoke(
        title: String,
        amount: Long,
        date: LocalDate,
        categoryId: Long,
        accountId: Long,
        note: String? = null,
        learnMerchant: Boolean = true
    ): Long? {
        val account = accountRepository.getAccountById(accountId) ?: return null
        val id = expenseRepository.insertExpenseReturningId(
            Expense(
                id = 0,
                title = title.trim(),
                amount = amount,
                expenseDate = date,
                categoryId = categoryId,
                paymentAccountId = account.id,
                paymentSourceType = account.type,
                note = note
            )
        )
        if (learnMerchant) assistantRepository.learnMerchant(title, categoryId)
        return id
    }
}
