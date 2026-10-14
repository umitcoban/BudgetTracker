package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.network.ExchangeRateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class MonthlyBudgetCalculator @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val savingGoalRepository: SavingGoalRepository,
    private val incomeRepository: IncomeRepository,
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: CategoryBudgetRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val statementRuleRepository: CreditCardStatementRuleRepository,
    private val statementPaymentRepository: CreditCardStatementPaymentRepository,
    private val subscriptionCalculator: SubscriptionMonthlyCalculator,
    private val loanCalculator: LoanMonthlyCalculator,
    private val fixedExpenseCalculator: FixedExpenseMonthlyCalculator
) {
    fun getSummaryForMonth(month: YearMonth): Flow<MonthlyBudgetSummary> {
        return getSummariesForMonths(listOf(month)).map { it.single() }
    }

    /**
     * Summaries for every month in [months], in the same order, computed from a single set of
     * database subscriptions. Prefer this over several [getSummaryForMonth] calls: each of those
     * opens its own Room queries and re-fetches exchange rates independently.
     *
     * Expenses are loaded for `[min(months) - BASELINE_MONTHS - MAX_PLANNING_MONTH_SHIFT, max(months)]`.
     */
    fun getSummariesForMonths(months: List<YearMonth>): Flow<List<MonthlyBudgetSummary>> {
        if (months.isEmpty()) return flowOf(emptyList())
        // Spike detection needs each month's baseline, so compute those months too (not returned).
        val computedMonths = months
            .flatMap { month -> (0..CategoryTrendRules.BASELINE_MONTHS).map { month.minusMonths(it.toLong()) } }
            .distinct()
            .sorted()
        val firstMonth = computedMonths.first()
        val lastMonth = computedMonths.last()

        val incomeFlow = combine(
            salaryRepository.observeAllSalaryRules(),
            savingGoalRepository.observeAllSavingGoals(),
            incomeRepository.observeAllIncomes()
        ) { salaryRules, savingGoals, incomes ->
            MonthlyIncomeInputs(
                salaryRules = salaryRules,
                savingGoals = savingGoals,
                incomes = incomes
            )
        }

        val expenseFlow = combine(
            expenseRepository.observeExpensesForDateRange(
                startDate = firstMonth.minusMonths(MAX_PLANNING_MONTH_SHIFT).atDay(1),
                endDate = lastMonth.atEndOfMonth()
            ),
            budgetRepository.observeAllBudgets(),
            adjustmentRepository.observeAllAdjustments(),
            statementRuleRepository.observeAllRules(),
            statementPaymentRepository.observeAllPayments()
        ) { expenses, budgets, adjustments, statementRules, statementPayments ->
            MonthlyExpenseInputs(
                expenses = expenses,
                budgets = budgets,
                adjustments = adjustments,
                statementRules = statementRules,
                statementPayments = statementPayments
            )
        }

        val plannedFlow = combine(
            subscriptionCalculator.observeInputs(),
            loanCalculator.observeInputs(),
            fixedExpenseCalculator.observeInputs()
        ) { subscriptions, loans, fixedExpenses ->
            PlannedPaymentInputs(
                subscriptions = subscriptions,
                loans = loans,
                fixedExpenses = fixedExpenses
            )
        }

        return combine(incomeFlow, expenseFlow, plannedFlow) { income, expense, planned ->
            // Card-paid planned items of the two previous calendar months can fall due in a month.
            val rateMonths = computedMonths.flatMap { m -> (0..MAX_PLANNING_MONTH_SHIFT).map { m.minusMonths(it) } }.distinct()
            val rates = subscriptionCalculator.resolveRates(planned.subscriptions, rateMonths)
            val today = LocalDate.now()
            val adjustmentsByExpenseId = ExpenseAdjustmentRules.groupByExpense(expense.adjustments)
            val preparedExpenses = expense.expenses.map {
                PreparedExpense(
                    expense = it,
                    netAmount = it.netAmount(adjustmentsByExpenseId),
                    calendarMonth = YearMonth.from(it.expenseDate),
                    planningMonth = it.planningMonth(expense.statementRules)
                )
            }
            val computations = computedMonths.associateWith { month ->
                buildSummary(month, income, expense, planned, preparedExpenses, rates)
            }
            months.map { month ->
                val computation = computations.getValue(month)
                val baseline = (CategoryTrendRules.BASELINE_MONTHS downTo 1)
                    .map { computations.getValue(month.minusMonths(it.toLong())).summary }
                val calendarMonthExpenses = preparedExpenses.filter { it.calendarMonth == month }.map { it.expense }
                computation.summary.copy(
                    warnings = BudgetWarningRules.build(
                        month = month,
                        today = today,
                        remainingAfterFixedPayments = computation.summary.remainingAfterFixedPayments,
                        categorySummaries = computation.summary.categorySummaries,
                        cardPayments = computation.cardPayments,
                        loanPayments = computation.loanPayments,
                        categorySpikes = CategoryTrendRules.detectSpikes(computation.summary, baseline),
                        duplicates = AnomalyRules.findDuplicates(calendarMonthExpenses),
                        subscriptionIncreases = AnomalyRules.findSubscriptionIncreases(
                            month = month,
                            subscriptions = planned.subscriptions.subscriptions,
                            priceHistory = planned.subscriptions.priceHistory
                        ),
                        positiveTrends = AnomalyRules.findPositiveTrends(baseline + computation.summary)
                    )
                )
            }
        }
    }

    private fun buildSummary(
        month: YearMonth,
        income: MonthlyIncomeInputs,
        expense: MonthlyExpenseInputs,
        planned: PlannedPaymentInputs,
        preparedExpenses: List<PreparedExpense>,
        rates: Map<String, ExchangeRateResult>
    ): MonthComputation {
        val applicableSalary = SalaryRules.effectiveForMonth(income.salaryRules, month)?.amount ?: 0L
        val savingGoalAmount = income.savingGoals.firstOrNull { it.yearMonth == month }?.amount ?: 0L
        val additionalIncomeAmount = income.incomes
            .filter { YearMonth.from(it.incomeDate) == month }
            .sumOf { it.amount }

        val calendarMonthExpenses = preparedExpenses.filter { it.calendarMonth == month }
        val plannedMonthExpenses = preparedExpenses.filter { it.planningMonth == month }

        // Planned payments belong to the month their money leaves: a card-paid subscription, loan
        // or fixed expense is due with the statement, like a card purchase. So the planned items
        // of [month] come from the calendar months whose payment lands in it. "Already processed"
        // is decided in the item's own calendar month, where its linked expense is dated.
        val sourceMonths = (MAX_PLANNING_MONTH_SHIFT downTo 0L).map { month.minusMonths(it) }
        fun calendarExpenses(source: YearMonth) = preparedExpenses.filter { it.calendarMonth == source }
        fun lands(source: YearMonth, day: Int, account: PaymentAccount?) =
            planningMonthFor(source.atDay(day.coerceIn(1, source.lengthOfMonth())), account, expense.statementRules) == month

        val subscriptions = sourceMonths.flatMap { source ->
            subscriptionCalculator.calculatePayments(
                month = source,
                inputs = planned.subscriptions,
                monthExpenses = calendarExpenses(source).map { it.expense },
                rates = rates
            ).filter { lands(source, it.billingDay, it.account) }
        }
        val loans = sourceMonths.flatMap { source ->
            loanCalculator.calculatePayments(source, planned.loans.loans, planned.loans.payments)
                .filter { lands(source, it.paymentDay, it.account) }
        }
        val fixedExpenses = sourceMonths.flatMap { source ->
            val processedIds = calendarExpenses(source).mapNotNull { it.expense.fixedExpenseId }.toSet()
            fixedExpenseCalculator.calculatePayments(source, planned.fixedExpenses)
                .filter { it.fixedExpenseId !in processedIds && lands(source, it.dayOfMonth, it.account) }
        }

        val totalExpenses = plannedMonthExpenses.sumOf { it.netAmount }
        val totalCardExpense = calendarMonthExpenses
            .filter { it.expense.paymentSourceType == AccountType.CREDIT_CARD }
            .sumOf { it.netAmount }
        val creditCardPaymentAmount = plannedMonthExpenses
            .filter { it.expense.paymentSourceType == AccountType.CREDIT_CARD }
            .sumOf { it.netAmount }
        val directExpenses = plannedMonthExpenses
            .filter { it.expense.paymentSourceType != AccountType.CREDIT_CARD }
            .sumOf { it.netAmount }

        val totalSubscriptionsUnpaid = subscriptions.filter { !it.isPaid }.sumOf { it.amount }
        val totalSubscriptionsPaid = plannedMonthExpenses
            .filter { it.expense.subscriptionId != null }
            .sumOf { it.netAmount }
        val totalSubscriptionsPlanned = subscriptions.sumOf { it.amount }
        val totalLoans = loans.sumOf { it.amount }
        val totalFixedExpenses = fixedExpenses.sumOf { it.amount }
        val essentialPaid = plannedMonthExpenses
            .filter { it.expense.isEssential() }
            .sumOf { it.netAmount }
        val variableExpenses = plannedMonthExpenses
            .filter {
                it.expense.subscriptionId == null && it.expense.loanId == null &&
                    it.expense.fixedExpenseId == null && it.expense.installmentGroupId == null
            }
            .sumOf { it.netAmount }
        val suggestedSaving = ((applicableSalary + additionalIncomeAmount - totalExpenses - totalSubscriptionsUnpaid - totalLoans - totalFixedExpenses) / 2)
            .coerceAtLeast(0L)

        val summary = MonthlyBudgetSummary(
            yearMonth = month,
            salaryAmount = applicableSalary,
            additionalIncomeAmount = additionalIncomeAmount,
            savingGoalAmount = savingGoalAmount,
            totalExpenseAmount = totalExpenses,
            calendarCreditCardSpendingAmount = totalCardExpense,
            creditCardPaymentAmount = creditCardPaymentAmount,
            directExpenseAmount = directExpenses,
            subscriptionAmount = totalSubscriptionsUnpaid,
            subscriptionPlannedAmount = totalSubscriptionsPlanned,
            subscriptionPaidAmount = totalSubscriptionsPaid,
            subscriptionUnpaidPlannedAmount = totalSubscriptionsUnpaid,
            loanPaymentAmount = totalLoans,
            fixedExpenseAmount = totalFixedExpenses,
            suggestedSavingAmount = suggestedSaving,
            essentialExpenseAmount = essentialPaid + totalSubscriptionsUnpaid + totalLoans + totalFixedExpenses,
            variableExpenseAmount = variableExpenses,
            subscriptionPayments = subscriptions,
            loanPayments = loans,
            fixedExpensePayments = fixedExpenses,
            // Budgets answer "what did I spend this month", so categories use the day of the
            // purchase, not the month its card statement is paid.
            categorySummaries = buildCategorySummaries(
                monthExpenses = calendarMonthExpenses,
                budgets = expense.budgets.filter { it.yearMonth == month }
            )
        )

        return MonthComputation(
            summary = summary,
            cardPayments = buildCardPaymentsDue(
                month = month,
                plannedMonthExpenses = plannedMonthExpenses,
                statementRules = expense.statementRules,
                statementPayments = expense.statementPayments
            ),
            loanPayments = loans
        )
    }

    private companion object {
        /**
         * A credit-card expense can land at most this many months after its calendar month
         * (statement close pushes +1, a due day on or before the statement day pushes +1 more).
         * The expense window loaded for a month range starts this far before the first month;
         * keep it in sync with [planningMonth].
         */
        const val MAX_PLANNING_MONTH_SHIFT = 2L
    }
}

private fun buildCategorySummaries(
    monthExpenses: List<PreparedExpense>,
    budgets: List<CategoryBudget>
): List<CategorySummary> {
    val expensesByCategory = monthExpenses.groupBy { it.expense.categoryId }
    val budgetsByCategory = budgets.associateBy { it.categoryId }
    val categoryIds = (expensesByCategory.keys + budgetsByCategory.keys).toSortedSet()

    return categoryIds.map { categoryId ->
        val categoryExpenses = expensesByCategory[categoryId].orEmpty()
        val budget = budgetsByCategory[categoryId]
        val category = budget?.category ?: categoryExpenses.firstOrNull()?.expense?.category
        val spent = categoryExpenses.sumOf { it.netAmount }
        val budgetLimit = budget?.limitAmount

        CategorySummary(
            categoryId = categoryId,
            categoryName = category?.name ?: "Bilinmeyen",
            iconName = category?.iconName ?: "category",
            colorValue = category?.colorValue ?: 0xFF9E9E9E.toInt(),
            amount = spent,
            budgetLimit = budgetLimit,
            percentage = budgetLimit
                ?.takeIf { it > 0L }
                ?.let { spent.toFloat() / it }
        )
    }.sortedByDescending { it.amount }
}

/**
 * One entry per credit card that has a statement falling due in [month]. The due day is resolved
 * the same way [CreditCardStatementCalculator] does for the payment month.
 */
private fun buildCardPaymentsDue(
    month: YearMonth,
    plannedMonthExpenses: List<PreparedExpense>,
    statementRules: List<CreditCardStatementRule>,
    statementPayments: List<CreditCardStatementPayment>
): List<CreditCardPaymentDue> {
    return plannedMonthExpenses
        .filter { it.expense.paymentSourceType == AccountType.CREDIT_CARD && it.expense.account != null }
        .groupBy { it.expense.account!!.id }
        .mapNotNull { (accountId, items) ->
            val account = items.first().expense.account!!
            val dueDay = statementRules.effectiveFor(accountId, month)?.dueDay
                ?: account.dueDay
                ?: return@mapNotNull null
            CreditCardPaymentDue(
                accountId = accountId,
                accountName = account.name,
                dueDate = month.atDay(dueDay.coerceIn(1, month.lengthOfMonth())),
                amount = items.sumOf { it.netAmount },
                isPaid = statementPayments.any {
                    it.accountId == accountId && it.paymentMonth == month && it.isPaid
                }
            )
        }
}

/** Category names whose spending counts as an obligation for the emergency-fund estimate. */
private val ESSENTIAL_CATEGORY_NAMES = setOf("Kira", "Fatura", "Aidat")

private fun Expense.isEssential(): Boolean =
    subscriptionId != null || loanId != null || fixedExpenseId != null || installmentGroupId != null ||
        category?.name in ESSENTIAL_CATEGORY_NAMES

private fun Expense.planningMonth(statementRules: List<CreditCardStatementRule>): YearMonth =
    if (paymentSourceType != AccountType.CREDIT_CARD) YearMonth.from(expenseDate) else planningMonthFor(expenseDate, account, statementRules)

/** Month whose card statement pays a charge on [expenseDate]; the calendar month for other accounts. */
internal fun planningMonthFor(
    expenseDate: LocalDate,
    account: PaymentAccount?,
    statementRules: List<CreditCardStatementRule>
): YearMonth {
    if (account == null || account.type != AccountType.CREDIT_CARD) return YearMonth.from(expenseDate)
    val expenseMonth = YearMonth.from(expenseDate)
    val statementRule = statementRules.effectiveFor(account.id, expenseMonth)
    val statementDay = statementRule?.statementDay ?: account.statementDay ?: return expenseMonth

    val statementEndMonth = if (expenseDate.dayOfMonth <= statementDay.coerceAtMost(expenseMonth.lengthOfMonth())) {
        expenseMonth
    } else {
        expenseMonth.plusMonths(1)
    }
    val dueRule = statementRules.effectiveFor(account.id, statementEndMonth)
    val dueDay = dueRule?.dueDay ?: statementRule?.dueDay ?: account.dueDay ?: return expenseMonth
    val dueRuleStatementDay = dueRule?.statementDay ?: statementDay

    return if (dueDay <= dueRuleStatementDay) {
        statementEndMonth.plusMonths(1)
    } else {
        statementEndMonth
    }
}

internal fun List<CreditCardStatementRule>.effectiveFor(
    accountId: Long,
    month: YearMonth
): CreditCardStatementRule? {
    return asSequence()
        .filter { it.accountId == accountId && !it.effectiveFromMonth.isAfter(month) }
        .maxByOrNull { it.effectiveFromMonth }
}

/** A month's summary before warnings, plus the payment details the warning rules need. */
private data class MonthComputation(
    val summary: MonthlyBudgetSummary,
    val cardPayments: List<CreditCardPaymentDue>,
    val loanPayments: List<LoanMonthlyPayment>
)

/** An expense with the derived values every month summary needs, computed once per emission. */
private data class PreparedExpense(
    val expense: Expense,
    val netAmount: Long,
    val calendarMonth: YearMonth,
    val planningMonth: YearMonth
)

private data class MonthlyIncomeInputs(
    val salaryRules: List<SalaryRule>,
    val savingGoals: List<MonthlySavingGoal>,
    val incomes: List<Income>
)

private data class MonthlyExpenseInputs(
    val expenses: List<Expense>,
    val budgets: List<CategoryBudget>,
    val adjustments: List<ExpenseAdjustment>,
    val statementRules: List<CreditCardStatementRule>,
    val statementPayments: List<CreditCardStatementPayment>
)

private data class PlannedPaymentInputs(
    val subscriptions: SubscriptionInputs,
    val loans: LoanInputs,
    val fixedExpenses: List<FixedExpense>
)
