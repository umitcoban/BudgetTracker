package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.network.ExchangeRateResult
import com.umit.budgettracker.core.network.ExchangeRateService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyBudgetCalculatorDataIntegrityTest {
    @Test
    fun getSummaryForMonth_excludesProcessedFixedExpenseFromPlannedFixedPayments() = runBlocking {
        val account = PaymentAccount(1L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)
        val fixedExpense = FixedExpense(
            id = 10L,
            title = "Kira",
            amount = 20_000L,
            dayOfMonth = 5,
            startMonth = YearMonth.of(2026, 5),
            endMonth = null,
            categoryId = 1L,
            paymentAccountId = account.id,
            note = null,
            isActive = true,
            account = account
        )
        val processedExpense = expense(
            amount = 20_000L,
            date = LocalDate.of(2026, 5, 5),
            account = account,
            fixedExpenseId = fixedExpense.id
        )

        val summary = calculator(
            expenses = listOf(processedExpense),
            fixedExpenses = listOf(fixedExpense)
        ).getSummaryForMonth(YearMonth.of(2026, 5)).first()

        assertEquals(20_000L, summary.totalExpenseAmount)
        assertEquals(0L, summary.fixedExpenseAmount)
        assertEquals(0L, summary.projectedFixedPaymentsAmount)
    }

    @Test
    fun getSummaryForMonth_movesCreditCardExpenseAfterStatementDayToNextStatementMonth() = runBlocking {
        val card = PaymentAccount(
            id = 2L,
            name = "Kart",
            type = AccountType.CREDIT_CARD,
            statementDay = 11,
            dueDay = 20,
            isActive = true
        )
        val expense = expense(
            amount = 10_000L,
            date = LocalDate.of(2026, 5, 12),
            account = card
        )
        val calc = calculator(expenses = listOf(expense))

        val may = calc.getSummaryForMonth(YearMonth.of(2026, 5)).first()
        val june = calc.getSummaryForMonth(YearMonth.of(2026, 6)).first()

        assertEquals(0L, may.creditCardPaymentAmount)
        assertEquals(10_000L, june.creditCardPaymentAmount)
    }

    private fun calculator(
        expenses: List<Expense> = emptyList(),
        fixedExpenses: List<FixedExpense> = emptyList()
    ): MonthlyBudgetCalculator {
        val expenseRepository = FakeExpenseRepository(expenses)
        val categoryRepository = FakeCategoryRepository()
        val accountRepository = FakePaymentAccountRepository()
        return MonthlyBudgetCalculator(
            salaryRepository = FakeSalaryRepository(),
            savingGoalRepository = FakeSavingGoalRepository(),
            incomeRepository = FakeIncomeRepository(),
            expenseRepository = expenseRepository,
            budgetRepository = FakeCategoryBudgetRepository(),
            adjustmentRepository = FakeExpenseAdjustmentRepository(),
            subscriptionCalculator = SubscriptionMonthlyCalculator(
                subscriptionRepository = FakeSubscriptionRepository(),
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                expenseRepository = expenseRepository,
                exchangeRateService = FakeExchangeRateService()
            ),
            loanCalculator = LoanMonthlyCalculator(FakeLoanRepository()),
            fixedExpenseCalculator = FixedExpenseMonthlyCalculator(FakeFixedExpenseRepository(fixedExpenses))
        )
    }

    private fun expense(
        amount: Long,
        date: LocalDate,
        account: PaymentAccount,
        fixedExpenseId: Long? = null
    ) = Expense(
        id = amount + date.toEpochDay(),
        title = "Harcama",
        amount = amount,
        expenseDate = date,
        categoryId = 1L,
        paymentAccountId = account.id,
        paymentSourceType = account.type,
        note = null,
        fixedExpenseId = fixedExpenseId,
        account = account
    )

    private class FakeSalaryRepository : SalaryRepository {
        override fun observeAllSalaryRules(): Flow<List<SalaryRule>> {
            return flowOf(listOf(SalaryRule(1L, 100_000L, YearMonth.of(2026, 1), null)))
        }
        override fun observeSalaryForMonth(yearMonth: YearMonth): Flow<SalaryRule?> = flowOf(null)
        override suspend fun upsertSalaryRule(rule: SalaryRule) = Unit
        override suspend fun deleteSalaryRule(rule: SalaryRule) = Unit
    }

    private class FakeSavingGoalRepository : SavingGoalRepository {
        override fun observeAllSavingGoals(): Flow<List<MonthlySavingGoal>> = flowOf(emptyList())
        override fun observeSavingGoalForMonth(yearMonth: YearMonth): Flow<MonthlySavingGoal?> = flowOf(null)
        override suspend fun upsertSavingGoal(goal: MonthlySavingGoal) = Unit
        override suspend fun deleteSavingGoal(goal: MonthlySavingGoal) = Unit
    }

    private class FakeIncomeRepository : IncomeRepository {
        override fun observeAllIncomes(): Flow<List<Income>> = flowOf(emptyList())
        override fun observeIncomesForMonth(yearMonth: YearMonth): Flow<List<Income>> = flowOf(emptyList())
        override suspend fun insertIncome(income: Income) = Unit
        override suspend fun updateIncome(income: Income) = Unit
        override suspend fun deleteIncome(income: Income) = Unit
    }

    private class FakeExpenseRepository(private val expenses: List<Expense>) : ExpenseRepository {
        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
            return flowOf(expenses.filter { YearMonth.from(it.expenseDate) == yearMonth })
        }
        override fun observeAllExpenses(): Flow<List<Expense>> = flowOf(expenses)
        override suspend fun getExpenseById(id: Long): Expense? = expenses.firstOrNull { it.id == id }
        override suspend fun insertExpense(expense: Expense) = Unit
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) = Unit
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean = false
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean = false
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean = false
    }

    private class FakeCategoryBudgetRepository : CategoryBudgetRepository {
        override fun observeAllBudgets(): Flow<List<CategoryBudget>> = flowOf(emptyList())
        override fun observeBudgetsForMonth(yearMonth: YearMonth): Flow<List<CategoryBudget>> = flowOf(emptyList())
        override fun observeBudgetForCategoryAndMonth(categoryId: Long, yearMonth: YearMonth): Flow<CategoryBudget?> = flowOf(null)
        override suspend fun upsertCategoryBudget(budget: CategoryBudget) = Unit
        override suspend fun deleteCategoryBudget(budget: CategoryBudget) = Unit
    }

    private class FakeExpenseAdjustmentRepository : ExpenseAdjustmentRepository {
        override fun observeAllAdjustments(): Flow<List<ExpenseAdjustment>> = flowOf(emptyList())
        override fun observeForExpense(expenseId: Long): Flow<List<ExpenseAdjustment>> = flowOf(emptyList())
        override suspend fun addAdjustment(adjustment: ExpenseAdjustment) = Unit
        override suspend fun deleteAdjustment(adjustment: ExpenseAdjustment) = Unit
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        override fun observeActiveSubscriptions(): Flow<List<Subscription>> = flowOf(emptyList())
        override fun observeAllSubscriptions(): Flow<List<Subscription>> = flowOf(emptyList())
        override fun observeSubscriptionById(id: Long): Flow<Subscription?> = flowOf(null)
        override fun observePriceHistory(subscriptionId: Long): Flow<List<SubscriptionPriceHistory>> = flowOf(emptyList())
        override fun observeAllPriceHistory(): Flow<List<SubscriptionPriceHistory>> = flowOf(emptyList())
        override suspend fun upsertSubscription(subscription: Subscription) = Unit
        override suspend fun backfillMissingPriceHistoryCurrency(subscription: Subscription) = Unit
        override suspend fun addPriceHistory(history: SubscriptionPriceHistory) = Unit
        override suspend fun createSubscriptionWithPrice(subscription: Subscription, initialAmount: Long, startMonth: YearMonth) = Unit
        override suspend fun deactivateSubscription(id: Long) = Unit
        override suspend fun deleteSubscription(id: Long) = Unit
    }

    private class FakeCategoryRepository : CategoryRepository {
        override fun observeActiveCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeAllCategories(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getCategoryById(id: Long): Category? = null
        override suspend fun upsertCategory(category: Category) = Unit
        override suspend fun deleteCategory(category: Category) = Unit
    }

    private class FakePaymentAccountRepository : PaymentAccountRepository {
        override fun observeActiveAccounts(): Flow<List<PaymentAccount>> = flowOf(emptyList())
        override suspend fun getAccountById(id: Long): PaymentAccount? = null
    }

    private class FakeLoanRepository : LoanRepository {
        override fun observeActiveLoans(): Flow<List<Loan>> = flowOf(emptyList())
        override fun observeAllLoans(): Flow<List<Loan>> = flowOf(emptyList())
        override fun observeLoanById(id: Long): Flow<Loan?> = flowOf(null)
        override suspend fun upsertLoan(loan: Loan) = Unit
        override suspend fun closeLoanEarly(id: Long, closedAt: java.time.LocalDate) = Unit
        override suspend fun deleteLoan(id: Long) = com.umit.budgettracker.core.domain.repository.LoanDeletionResult.Deleted
    }

    private class FakeFixedExpenseRepository(private val fixedExpenses: List<FixedExpense>) : FixedExpenseRepository {
        override fun observeAllFixedExpenses(): Flow<List<FixedExpense>> = flowOf(fixedExpenses)
        override fun observeActiveFixedExpenses(): Flow<List<FixedExpense>> = flowOf(fixedExpenses.filter { it.isActive })
        override suspend fun upsertFixedExpense(expense: FixedExpense) = Unit
        override suspend fun deleteFixedExpense(expense: FixedExpense) = Unit
    }

    private class FakeExchangeRateService : ExchangeRateService() {
        override suspend fun fetchRateToTry(currency: String): Result<ExchangeRateResult> {
            return Result.failure(IllegalStateException("No network in unit test"))
        }
    }
}
