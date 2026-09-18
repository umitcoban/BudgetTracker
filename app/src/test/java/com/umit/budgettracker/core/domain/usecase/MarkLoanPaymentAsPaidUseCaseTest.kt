package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MarkLoanPaymentAsPaidUseCaseTest {
    private val bank = PaymentAccount(2L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)
    private val loanCategory = Category(15L, "Kredi", "loan", 0, CategoryType.EXPENSE, true, true, 0)
    private val loan = Loan(
        id = 7L,
        title = "Konut kredisi",
        principalAmount = 1_200_000L,
        monthlyPaymentAmount = 100_000L,
        installmentCount = 12,
        startMonth = YearMonth.of(2026, 1),
        paymentDay = 15,
        categoryId = loanCategory.id,
        paymentAccountId = bank.id,
        note = null,
        isActive = true
    )

    @Test
    fun invoke_recordsPaymentAndCreatesLinkedExpenseOnce() = runBlocking {
        val expenses = FakeExpenseRepository()
        val payments = FakeLoanPaymentRepository()
        val useCase = useCase(loan, expenses, payments)
        val month = YearMonth.of(2026, 3)

        assertEquals(MarkLoanPaymentResult.MarkedPaid, useCase(loan.id, month))
        assertEquals(MarkLoanPaymentResult.AlreadyPaid, useCase(loan.id, month))

        val expense = expenses.saved.single()
        assertEquals("Konut kredisi (3/12)", expense.title)
        assertEquals(100_000L, expense.amount)
        assertEquals(LocalDate.of(2026, 3, 15), expense.expenseDate)
        assertEquals(loan.id, expense.loanId)
        assertEquals(bank.id, expense.paymentAccountId)
        assertEquals(AccountType.BANK_ACCOUNT, expense.paymentSourceType)
        assertEquals(loanCategory.id, expense.categoryId)
        assertEquals(1, payments.saved.size)
    }

    @Test
    fun invoke_fallsBackToDefaultLoanCategoryAndFirstAccountWhenLoanHasNone() = runBlocking {
        val expenses = FakeExpenseRepository()
        val legacyLoan = loan.copy(categoryId = null, paymentAccountId = null)

        val result = useCase(legacyLoan, expenses, FakeLoanPaymentRepository())(legacyLoan.id, YearMonth.of(2026, 2))

        assertEquals(MarkLoanPaymentResult.MarkedPaid, result)
        assertEquals(loanCategory.id, expenses.saved.single().categoryId)
        assertEquals(bank.id, expenses.saved.single().paymentAccountId)
    }

    @Test
    fun invoke_refusesMonthsOutsideTheLoanTerm() = runBlocking {
        val expenses = FakeExpenseRepository()

        val result = useCase(loan, expenses, FakeLoanPaymentRepository())(loan.id, YearMonth.of(2027, 1))

        assertEquals(MarkLoanPaymentResult.NotDue, result)
        assertTrue(expenses.saved.isEmpty())
    }

    private fun useCase(loan: Loan, expenses: FakeExpenseRepository, payments: FakeLoanPaymentRepository) =
        MarkLoanPaymentAsPaidUseCase(
            loanRepository = FakeLoanRepository(loan),
            loanPaymentRepository = payments,
            expenseRepository = expenses,
            categoryRepository = FakeCategoryRepository(listOf(loanCategory)),
            accountRepository = FakePaymentAccountRepository(listOf(bank))
        )

    private class FakeLoanRepository(private val loan: Loan) : LoanRepository {
        override fun observeActiveLoans(): Flow<List<Loan>> = flowOf(listOf(loan))
        override fun observeAllLoans(): Flow<List<Loan>> = flowOf(listOf(loan))
        override fun observeLoanById(id: Long): Flow<Loan?> = flowOf(loan.takeIf { it.id == id })
        override suspend fun upsertLoan(loan: Loan) = Unit
        override suspend fun closeLoanEarly(id: Long, closedAt: LocalDate) = Unit
        override suspend fun deleteLoan(id: Long): LoanDeletionResult = LoanDeletionResult.Deleted
    }

    private class FakeLoanPaymentRepository : LoanPaymentRepository {
        val saved = mutableListOf<LoanPayment>()
        override fun observeAllPayments(): Flow<List<LoanPayment>> = flowOf(saved)
        override fun observePaymentsForMonth(month: YearMonth): Flow<List<LoanPayment>> = flowOf(saved.filter { it.paymentMonth == month })
        override suspend fun getPayment(loanId: Long, month: YearMonth): LoanPayment? =
            saved.firstOrNull { it.loanId == loanId && it.paymentMonth == month }
        override suspend fun insertPayment(payment: LoanPayment) { saved += payment }
    }

    private class FakeExpenseRepository : ExpenseRepository {
        val saved = mutableListOf<Expense>()
        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> = flowOf(saved)
        override fun observeAllExpenses(): Flow<List<Expense>> = flowOf(saved)
        override suspend fun getExpenseById(id: Long): Expense? = null
        override suspend fun insertExpense(expense: Expense) { saved += expense }
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) = Unit
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean = false
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean = false
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean = false
    }

    private class FakeCategoryRepository(private val categories: List<Category>) : CategoryRepository {
        override fun observeActiveCategories(): Flow<List<Category>> = flowOf(categories)
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(categories)
        override suspend fun getCategoryById(id: Long): Category? = categories.firstOrNull { it.id == id }
        override suspend fun upsertCategory(category: Category) = Unit
        override suspend fun deleteCategory(category: Category) = Unit
    }

    private class FakePaymentAccountRepository(private val accounts: List<PaymentAccount>) : PaymentAccountRepository {
        override fun observeAllAccounts(): Flow<List<PaymentAccount>> = flowOf(accounts)
        override fun observeActiveAccounts(): Flow<List<PaymentAccount>> = flowOf(accounts)
        override suspend fun getAccountById(id: Long): PaymentAccount? = accounts.firstOrNull { it.id == id }
    }
}
