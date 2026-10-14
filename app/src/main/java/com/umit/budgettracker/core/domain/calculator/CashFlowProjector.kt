package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.model.CashForecast
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.model.FixedExpenseMonthlyPayment
import com.umit.budgettracker.core.domain.model.Income
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.PlannedExpense
import com.umit.budgettracker.core.domain.model.PublicHoliday
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.CreditCardStatementRuleRepository
import com.umit.budgettracker.core.domain.repository.ExpenseAdjustmentRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.HolidayRepository
import com.umit.budgettracker.core.domain.repository.IncomeRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.PlannedExpenseRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Builds a month's cash-movement calendar (see "Cash Flow Rule" in CODEBASE_CONTEXT.md).
 * Shared by the Cash Flow screen, the smart cash warning and the daily background checks.
 */
class CashFlowProjector @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val statementRuleRepository: CreditCardStatementRuleRepository,
    private val incomeRepository: IncomeRepository,
    private val salaryRepository: SalaryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val plannedExpenseRepository: PlannedExpenseRepository,
    private val holidayRepository: HolidayRepository,
    private val statementCalculator: CreditCardStatementCalculator,
    private val subscriptionCalculator: SubscriptionMonthlyCalculator,
    private val loanCalculator: LoanMonthlyCalculator,
    private val fixedExpenseCalculator: FixedExpenseMonthlyCalculator
) {
    fun observeMonth(month: YearMonth): Flow<List<CashFlowEvent>> {
        val actualFlow = combine(
            incomeRepository.observeIncomesForMonth(month),
            expenseRepository.observeExpensesForMonth(month),
            // A statement due this month closes at most two calendar months back.
            expenseRepository.observeExpensesForDateRange(
                startDate = month.minusMonths(STATEMENT_LOOKBACK_MONTHS).atDay(1),
                endDate = month.atEndOfMonth()
            ),
            adjustmentRepository.observeAllAdjustments(),
            statementRuleRepository.observeAllRules()
        ) { incomes, expenses, statementWindowExpenses, adjustments, rules ->
            ActualInputs(incomes, expenses, statementWindowExpenses, adjustments, rules)
        }
        // Card-paid planned items of the two previous months can be paid with this month's statement.
        val sources = (STATEMENT_LOOKBACK_MONTHS downTo 0L).map { month.minusMonths(it) }
        val plannedSourcesFlow = combine(sources.map { source ->
            combine(
                subscriptionCalculator.getPaymentsForMonth(source),
                loanCalculator.getPaymentsForMonth(source),
                fixedExpenseCalculator.getPaymentsForMonth(source)
            ) { subscriptions, loans, fixedExpenses -> PlannedSource(source, subscriptions, loans, fixedExpenses) }
        }) { it.toList() }
        val plannedFlow = combine(
            accountRepository.observeActiveAccounts(),
            plannedSourcesFlow,
            salaryRepository.observeSalaryForMonth(month)
        ) { accounts, plannedSources, salaryRule ->
            PlannedInputs(accounts, plannedSources, salaryRule)
        }
        val extraFlow = combine(
            plannedExpenseRepository.observeAll(),
            holidayRepository.observeAll()
        ) { plannedExpenses, holidays -> plannedExpenses to holidays }

        return combine(actualFlow, plannedFlow, extraFlow) { actual, planned, extra ->
            buildEvents(month, actual, planned, extra.first, extra.second)
        }
    }

    /** Current and next month together, for forecasts that cross a month boundary. */
    fun observeRange(months: List<YearMonth>): Flow<List<CashFlowEvent>> {
        val flows = months.map { observeMonth(it) }
        return combine(flows) { lists -> lists.flatMap { it }.sortedBy { it.date } }
    }

    private fun buildEvents(
        month: YearMonth,
        actual: ActualInputs,
        planned: PlannedInputs,
        plannedExpenses: List<PlannedExpense>,
        holidays: List<PublicHoliday>
    ): List<CashFlowEvent> {
        val list = mutableListOf<CashFlowEvent>()
        val adjustmentsByExpenseId = ExpenseAdjustmentRules.groupByExpense(actual.adjustments)

        planned.salaryRule?.takeIf { it.amount > 0L }?.let { salary ->
            val payDay = salary.payDay
            list += CashFlowEvent(
                date = month.atDay((payDay ?: 1).coerceIn(1, month.lengthOfMonth())),
                title = "Maaş",
                amount = salary.amount,
                type = CashFlowEventType.INCOME,
                sourceId = salary.id,
                description = if (payDay == null) "Aylık maaş · gün girilmedi, ay başı varsayıldı" else "Aylık maaş"
            )
        }

        actual.incomes.forEach { income ->
            list += CashFlowEvent(
                date = income.incomeDate,
                title = income.title,
                amount = income.amount,
                type = CashFlowEventType.INCOME,
                sourceId = income.id,
                description = "Gelir"
            )
        }

        // Card purchases are not cash movements; the statement payment below is.
        actual.expenses.filter { it.paymentSourceType != AccountType.CREDIT_CARD }.forEach { e ->
            list += CashFlowEvent(
                date = e.expenseDate,
                title = e.title,
                amount = e.netAmount(adjustmentsByExpenseId),
                type = if (e.installmentGroupId != null) CashFlowEventType.INSTALLMENT else CashFlowEventType.EXPENSE,
                sourceId = e.id,
                description = when {
                    e.subscriptionId != null -> "Abonelik ödemesi"
                    e.fixedExpenseId != null -> "Sabit gider ödemesi"
                    else -> null
                }
            )
        }

        planned.accounts.filter { it.type == AccountType.CREDIT_CARD }.forEach { acc ->
            val statement = statementCalculator.calculateStatement(
                account = acc,
                paymentMonth = month,
                allExpenses = actual.statementWindowExpenses,
                rules = actual.statementRules,
                adjustments = actual.adjustments
            )
            if (statement.totalAmount > 0) {
                list += CashFlowEvent(
                    date = statement.dueDate,
                    title = "${acc.name} Ekstresi",
                    amount = statement.totalAmount,
                    type = CashFlowEventType.CREDIT_CARD_PAYMENT,
                    sourceId = acc.id,
                    description = null
                )
            }
        }

        // Planned payments appear when their money leaves: on the billing day for cash/bank
        // accounts, on the statement due date for card-paid ones (same rule as the monthly summary).
        fun placement(source: YearMonth, day: Int, account: PaymentAccount?): Pair<LocalDate, String?>? {
            val billingDate = source.atDay(day.coerceIn(1, source.lengthOfMonth()))
            if (account == null || account.type != AccountType.CREDIT_CARD) {
                return if (source == month) billingDate to null else null
            }
            if (planningMonthFor(billingDate, account, actual.statementRules) != month) return null
            val dueDay = actual.statementRules.effectiveFor(account.id, month)?.dueDay ?: account.dueDay ?: day
            return month.atDay(dueDay.coerceIn(1, month.lengthOfMonth())) to "${account.name} ekstresiyle ödenir"
        }

        planned.sources.forEach { source ->
            source.subscriptions.filter { !it.isPaid }.forEach { sub ->
                val (date, cardNote) = placement(source.month, sub.billingDay, sub.account) ?: return@forEach
                list += CashFlowEvent(
                    date = date,
                    title = sub.title,
                    amount = sub.amount,
                    type = CashFlowEventType.SUBSCRIPTION,
                    sourceId = sub.subscriptionId,
                    description = listOfNotNull(
                        sub.originalCurrency?.let { currency -> "${sub.originalAmount?.let { formatMinor(it) } ?: ""} $currency · güncel kurla" },
                        cardNote
                    ).joinToString(" · ").ifEmpty { null }
                )
            }

            source.loans.forEach { loan ->
                val (date, cardNote) = placement(source.month, loan.paymentDay, loan.account) ?: return@forEach
                list += CashFlowEvent(
                    date = date,
                    title = loan.title,
                    amount = loan.amount,
                    type = CashFlowEventType.LOAN,
                    sourceId = loan.loanId,
                    description = listOfNotNull("${loan.currentInstallment}/${loan.totalInstallments}", cardNote).joinToString(" · ")
                )
            }

            val processedFixedIds = actual.statementWindowExpenses
                .filter { YearMonth.from(it.expenseDate) == source.month }
                .mapNotNull { it.fixedExpenseId }
                .toSet()
            source.fixedExpenses.filter { it.fixedExpenseId !in processedFixedIds }.forEach { fixedExpense ->
                val (date, cardNote) = placement(source.month, fixedExpense.dayOfMonth, fixedExpense.account) ?: return@forEach
                list += CashFlowEvent(
                    date = date,
                    title = fixedExpense.title,
                    amount = fixedExpense.amount,
                    type = CashFlowEventType.FIXED_EXPENSE,
                    sourceId = fixedExpense.fixedExpenseId,
                    description = cardNote ?: "Sabit gider"
                )
            }
        }

        plannedExpenses
            .filter { !it.isCompleted && YearMonth.from(it.dueDate) == month }
            .forEach { item ->
                list += CashFlowEvent(
                    date = item.dueDate,
                    title = item.title,
                    amount = PlannedExpenseRules.remaining(item).takeIf { it > 0L } ?: item.amount,
                    type = CashFlowEventType.PLANNED_EXPENSE,
                    sourceId = item.id,
                    description = if (item.savedAmount > 0L) "Büyük harcama · ayrılan ${formatMinor(item.savedAmount)} ₺ düşüldü" else "Büyük harcama"
                )
            }

        return list
            .map { event ->
                if (event.type.isPlanned || event.type == CashFlowEventType.INCOME) {
                    event.copy(bankDayNote = BusinessDayRules.shiftNote(event.date, holidays))
                } else {
                    event
                }
            }
            .sortedBy { it.date }
    }

    private data class ActualInputs(
        val incomes: List<Income>,
        val expenses: List<Expense>,
        val statementWindowExpenses: List<Expense>,
        val adjustments: List<ExpenseAdjustment>,
        val statementRules: List<CreditCardStatementRule>
    )

    private data class PlannedInputs(
        val accounts: List<PaymentAccount>,
        val sources: List<PlannedSource>,
        val salaryRule: SalaryRule?
    )

    /** Planned payments of one calendar month, before placing them on the day money leaves. */
    private data class PlannedSource(
        val month: YearMonth,
        val subscriptions: List<SubscriptionMonthlyPayment>,
        val loans: List<LoanMonthlyPayment>,
        val fixedExpenses: List<FixedExpenseMonthlyPayment>
    )

    private companion object {
        const val STATEMENT_LOOKBACK_MONTHS = 2L

        fun formatMinor(value: Long): String = "${value / 100},${(value % 100).toString().padStart(2, '0')}"
    }
}

/** Pure balance projection over cash-flow events. */
object CashForecastRules {
    /**
     * Walks [events] from [today] (inclusive) to [horizonEnd] starting at [startBalance].
     * Planned movements count from today on; actual ones only when dated after today, because
     * the starting balance already reflects what happened so far.
     */
    fun forecast(
        startBalance: Long,
        events: List<CashFlowEvent>,
        today: LocalDate,
        horizonEnd: LocalDate
    ): CashForecast {
        val relevant = events
            .filter { !it.date.isBefore(today) && !it.date.isAfter(horizonEnd) }
            .filter { it.type.isPlanned || it.date.isAfter(today) }
            .sortedWith(compareBy<CashFlowEvent> { it.date }.thenBy { if (it.type == CashFlowEventType.INCOME) 0 else 1 })
        var balance = startBalance
        var lowest = startBalance
        var lowestDate = today
        var pressure: CashFlowEvent? = null
        relevant.forEach { event ->
            balance += if (event.type == CashFlowEventType.INCOME) event.amount else -event.amount
            if (balance < lowest) {
                lowest = balance
                lowestDate = event.date
                pressure = event
            }
        }
        val nextIncome = relevant.firstOrNull { it.type == CashFlowEventType.INCOME && it.date.isAfter(lowestDate) }
        return CashForecast(
            startBalance = startBalance,
            lowestBalance = lowest,
            lowestDate = lowestDate,
            pressureEvent = pressure,
            nextIncome = nextIncome,
            horizonEnd = horizonEnd
        )
    }
}
