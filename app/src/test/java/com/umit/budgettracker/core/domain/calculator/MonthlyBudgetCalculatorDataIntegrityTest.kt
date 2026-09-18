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
import org.junit.Assert.assertTrue
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

    @Test
    fun getSummaryForMonth_usesEffectiveStatementRuleForHistoricalCreditCardPlanning() = runBlocking {
        val card = PaymentAccount(
            id = 3L,
            name = "Kart",
            type = AccountType.CREDIT_CARD,
            statementDay = 13,
            dueDay = 5,
            isActive = true
        )
        val expenseBeforeChange = expense(
            amount = 10_000L,
            date = LocalDate.of(2026, 6, 12),
            account = card
        )
        val expenseInPreviousStatementCycle = expense(
            amount = 20_000L,
            date = LocalDate.of(2026, 7, 14),
            account = card
        )
        val rule = CreditCardStatementRule(
            id = 1L,
            accountId = card.id,
            effectiveFromMonth = YearMonth.of(2026, 8),
            statementDay = 11,
            dueDay = 20
        )
        val calc = calculator(
            expenses = listOf(expenseBeforeChange, expenseInPreviousStatementCycle),
            statementRules = listOf(rule)
        )

        val july = calc.getSummaryForMonth(YearMonth.of(2026, 7)).first()
        val august = calc.getSummaryForMonth(YearMonth.of(2026, 8)).first()
        val september = calc.getSummaryForMonth(YearMonth.of(2026, 9)).first()

        assertEquals(10_000L, july.creditCardPaymentAmount)
        assertEquals(20_000L, august.creditCardPaymentAmount)
        assertEquals(0L, september.creditCardPaymentAmount)
    }

    @Test
    fun getSummaryForMonth_includesSpentCategoriesWithoutBudget() = runBlocking {
        val account = PaymentAccount(1L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)
        val category = Category(
            id = 5L,
            name = "Evcil Hayvan",
            iconName = "pets",
            colorValue = 0xFF4CAF50.toInt(),
            type = CategoryType.EXPENSE,
            isDefault = false,
            isActive = true,
            sortOrder = 0
        )
        val expense = expense(
            amount = 12_500L,
            date = LocalDate.of(2026, 5, 10),
            account = account,
            category = category
        )

        val summary = calculator(expenses = listOf(expense))
            .getSummaryForMonth(YearMonth.of(2026, 5))
            .first()

        val categorySummary = summary.categorySummaries.single()
        assertEquals("Evcil Hayvan", categorySummary.categoryName)
        assertEquals(12_500L, categorySummary.amount)
        assertEquals(null, categorySummary.budgetLimit)
    }

    @Test
    fun getSummariesForMonths_matchesIndividualMonthSummaries() = runBlocking {
        val card = PaymentAccount(2L, "Kart", AccountType.CREDIT_CARD, 11, 20, true)
        val bank = PaymentAccount(1L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)
        val calc = calculator(
            expenses = listOf(
                expense(amount = 10_000L, date = LocalDate.of(2026, 5, 12), account = card),
                expense(amount = 7_500L, date = LocalDate.of(2026, 6, 3), account = bank),
                expense(amount = 4_000L, date = LocalDate.of(2026, 7, 20), account = card)
            )
        )
        val months = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7))

        val batched = calc.getSummariesForMonths(months).first()
        val individual = months.map { calc.getSummaryForMonth(it).first() }

        assertEquals(months, batched.map { it.yearMonth })
        assertEquals(individual, batched)
    }

    @Test
    fun getSummariesForMonths_loadsOnlyThePlanningWindowAndStillCatchesShiftedCardExpenses() = runBlocking {
        // dueDay <= statementDay pushes a post-statement expense two months forward: May -> July.
        val card = PaymentAccount(3L, "Kart", AccountType.CREDIT_CARD, 13, 5, true)
        val repository = RangeTrackingExpenseRepository(
            listOf(
                expense(amount = 10_000L, date = LocalDate.of(2026, 5, 14), account = card),
                expense(amount = 99_000L, date = LocalDate.of(2026, 1, 10), account = card)
            )
        )
        val july = YearMonth.of(2026, 7)

        val summary = calculator(expenseRepository = repository).getSummariesForMonths(listOf(july)).first().single()

        assertEquals(LocalDate.of(2026, 5, 1)..LocalDate.of(2026, 7, 31), repository.requestedRange)
        assertEquals(10_000L, summary.creditCardPaymentAmount)
    }

    @Test
    fun getSummaryForMonth_populatesWarningsWhenCategoryBudgetIsExceeded() = runBlocking {
        val account = PaymentAccount(1L, "Banka", AccountType.BANK_ACCOUNT, null, null, true)
        val category = Category(7L, "Market", "cart", 0xFF2196F3.toInt(), CategoryType.EXPENSE, true, true, 0)
        val month = YearMonth.of(2026, 5)
        val budget = CategoryBudget(1L, category.id, month, 50_000L, null, category)
        val overspend = expense(amount = 62_500L, date = month.atDay(9), account = account, category = category)

        val summary = calculator(expenses = listOf(overspend), budgets = listOf(budget))
            .getSummaryForMonth(month)
            .first()

        val warning = summary.warnings.single()
        assertEquals(BudgetWarningType.CATEGORY_LIMIT_EXCEEDED, warning.type)
        assertTrue(warning.message, warning.message.startsWith("Market bütçesi"))
    }

    private fun calculator(
        expenses: List<Expense> = emptyList(),
        fixedExpenses: List<FixedExpense> = emptyList(),
        statementRules: List<CreditCardStatementRule> = emptyList(),
        budgets: List<CategoryBudget> = emptyList(),
        expenseRepository: ExpenseRepository = FakeExpenseRepository(expenses)
    ): MonthlyBudgetCalculator {
        val categoryRepository = FakeCategoryRepository()
        val accountRepository = FakePaymentAccountRepository()
        return MonthlyBudgetCalculator(
            salaryRepository = FakeSalaryRepository(),
            savingGoalRepository = FakeSavingGoalRepository(),
            incomeRepository = FakeIncomeRepository(),
            expenseRepository = expenseRepository,
            budgetRepository = FakeCategoryBudgetRepository(budgets),
            adjustmentRepository = FakeExpenseAdjustmentRepository(),
            statementRuleRepository = FakeCreditCardStatementRuleRepository(statementRules),
            statementPaymentRepository = FakeCreditCardStatementPaymentRepository(),
            subscriptionCalculator = SubscriptionMonthlyCalculator(
                subscriptionRepository = FakeSubscriptionRepository(),
                categoryRepository = categoryRepository,
                accountRepository = accountRepository,
                expenseRepository = expenseRepository,
                exchangeRateService = FakeExchangeRateService()
            ),
            loanCalculator = LoanMonthlyCalculator(FakeLoanRepository(), FakeLoanPaymentRepository()),
            fixedExpenseCalculator = FixedExpenseMonthlyCalculator(FakeFixedExpenseRepository(fixedExpenses))
        )
    }

    private fun expense(
        amount: Long,
        date: LocalDate,
        account: PaymentAccount,
        fixedExpenseId: Long? = null,
        category: Category? = null
    ) = Expense(
        id = amount + date.toEpochDay(),
        title = "Harcama",
        amount = amount,
        expenseDate = date,
        categoryId = category?.id ?: 1L,
        paymentAccountId = account.id,
        paymentSourceType = account.type,
        note = null,
        fixedExpenseId = fixedExpenseId,
        category = category,
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

    /** Records the date window the calculator asks for and honours it like the Room query would. */
    private class RangeTrackingExpenseRepository(private val expenses: List<Expense>) : ExpenseRepository {
        var requestedRange: ClosedRange<LocalDate>? = null

        override fun observeExpensesForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>> {
            requestedRange = startDate..endDate
            return flowOf(expenses.filter { it.expenseDate in startDate..endDate })
        }
        override fun observeExpensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
            return flowOf(expenses.filter { YearMonth.from(it.expenseDate) == yearMonth })
        }
        override fun observeAllExpenses(): Flow<List<Expense>> = error("range query expected")
        override suspend fun getExpenseById(id: Long): Expense? = expenses.firstOrNull { it.id == id }
        override suspend fun insertExpense(expense: Expense) = Unit
        override suspend fun updateExpense(expense: Expense) = Unit
        override suspend fun deleteExpense(expense: Expense) = Unit
        override suspend fun hasSubscriptionExpenseForMonth(subscriptionId: Long, yearMonth: YearMonth): Boolean = false
        override suspend fun hasAnySubscriptionExpense(subscriptionId: Long): Boolean = false
        override suspend fun hasFixedExpenseForMonth(fixedExpenseId: Long, yearMonth: YearMonth): Boolean = false
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

    private class FakeCategoryBudgetRepository(private val budgets: List<CategoryBudget>) : CategoryBudgetRepository {
        override fun observeAllBudgets(): Flow<List<CategoryBudget>> = flowOf(budgets)
        override fun observeBudgetsForMonth(yearMonth: YearMonth): Flow<List<CategoryBudget>> =
            flowOf(budgets.filter { it.yearMonth == yearMonth })
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

    private class FakeCreditCardStatementPaymentRepository : CreditCardStatementPaymentRepository {
        override fun observeAllPayments(): Flow<List<CreditCardStatementPayment>> = flowOf(emptyList())
        override fun observePaymentsForMonth(paymentMonth: YearMonth): Flow<List<CreditCardStatementPayment>> = flowOf(emptyList())
        override suspend fun setStatementPaid(accountId: Long, paymentMonth: YearMonth, amount: Long) = Unit
        override suspend fun setStatementUnpaid(accountId: Long, paymentMonth: YearMonth) = Unit
    }

    private class FakeCreditCardStatementRuleRepository(
        private val rules: List<CreditCardStatementRule>
    ) : CreditCardStatementRuleRepository {
        override fun observeAllRules(): Flow<List<CreditCardStatementRule>> = flowOf(rules)
        override suspend fun saveRule(rule: CreditCardStatementRule) = Unit
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
        override fun observeAllAccounts(): Flow<List<PaymentAccount>> = flowOf(emptyList())
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

    private class FakeLoanPaymentRepository : LoanPaymentRepository {
        override fun observeAllPayments(): Flow<List<LoanPayment>> = flowOf(emptyList())
        override fun observePaymentsForMonth(month: YearMonth): Flow<List<LoanPayment>> = flowOf(emptyList())
        override suspend fun getPayment(loanId: Long, month: YearMonth): LoanPayment? = null
        override suspend fun insertPayment(payment: LoanPayment) = Unit
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
