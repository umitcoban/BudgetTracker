package com.umit.budgettracker.feature.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CreditCardStatementCalculator
import com.umit.budgettracker.core.domain.calculator.ExpenseAdjustmentRules
import com.umit.budgettracker.core.domain.calculator.netAmount
import com.umit.budgettracker.core.domain.calculator.FixedExpenseMonthlyCalculator
import com.umit.budgettracker.core.domain.calculator.LoanMonthlyCalculator
import com.umit.budgettracker.core.domain.calculator.SubscriptionMonthlyCalculator
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.model.FixedExpenseMonthlyPayment
import com.umit.budgettracker.core.domain.model.Income
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.CreditCardStatementRuleRepository
import com.umit.budgettracker.core.domain.repository.ExpenseAdjustmentRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.IncomeRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashFlowViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val statementRuleRepository: CreditCardStatementRuleRepository,
    private val incomeRepository: IncomeRepository,
    private val salaryRepository: SalaryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val statementCalculator: CreditCardStatementCalculator,
    private val subscriptionCalculator: SubscriptionMonthlyCalculator,
    private val loanCalculator: LoanMonthlyCalculator,
    private val fixedExpenseCalculator: FixedExpenseMonthlyCalculator
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val events: StateFlow<List<CashFlowEvent>> = _selectedMonth
        .flatMapLatest { month ->
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
                CashFlowActualInputs(
                    incomes = incomes,
                    expenses = expenses,
                    statementWindowExpenses = statementWindowExpenses,
                    adjustmentsByExpenseId = ExpenseAdjustmentRules.groupByExpense(adjustments),
                    adjustments = adjustments,
                    statementRules = rules
                )
            }
            val plannedFlow = combine(
                accountRepository.observeActiveAccounts(),
                subscriptionCalculator.getPaymentsForMonth(month),
                loanCalculator.getPaymentsForMonth(month),
                fixedExpenseCalculator.getPaymentsForMonth(month),
                salaryRepository.observeSalaryForMonth(month)
            ) { accounts, subscriptions, loans, fixedExpenses, salaryRule ->
                CashFlowPlannedInputs(accounts, subscriptions, loans, fixedExpenses, salaryRule)
            }

            combine(
                actualFlow,
                plannedFlow
            ) { actual, planned ->
                val list = mutableListOf<CashFlowEvent>()

                // Salary rules carry no pay day, so the month's salary is placed on day 1.
                planned.salaryRule?.takeIf { it.amount > 0L }?.let { salary ->
                    list.add(
                        CashFlowEvent(
                            date = month.atDay(1),
                            title = "Maaş",
                            amount = salary.amount,
                            type = CashFlowEventType.INCOME,
                            sourceId = salary.id,
                            description = "Aylık maaş · ay başı varsayıldı"
                        )
                    )
                }

                actual.incomes.forEach { income ->
                    list.add(
                        CashFlowEvent(
                            date = income.incomeDate,
                            title = income.title,
                            amount = income.amount,
                            type = CashFlowEventType.INCOME,
                            sourceId = income.id,
                            description = "Gelir"
                        )
                    )
                }
                
                // Card purchases are not cash movements; the statement payment below is.
                actual.expenses.filter { it.paymentSourceType != AccountType.CREDIT_CARD }.forEach { e ->
                    list.add(
                        CashFlowEvent(
                            date = e.expenseDate,
                            title = e.title,
                            amount = e.netAmount(actual.adjustmentsByExpenseId),
                            type = if (e.installmentGroupId != null) CashFlowEventType.INSTALLMENT else CashFlowEventType.EXPENSE,
                            sourceId = e.id,
                            description = when {
                                e.subscriptionId != null -> "Abonelik ödemesi"
                                e.fixedExpenseId != null -> "Sabit gider ödemesi"
                                else -> null
                            }
                        )
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
                        list.add(
                            CashFlowEvent(
                                date = statement.dueDate,
                                title = "${acc.name} Ekstresi",
                                amount = statement.totalAmount,
                                type = CashFlowEventType.CREDIT_CARD_PAYMENT,
                                sourceId = acc.id,
                                description = null
                            )
                        )
                    }
                }

                planned.subscriptions.filter { !it.isPaid }.forEach { sub ->
                    list.add(
                        CashFlowEvent(
                            date = month.atDay(sub.billingDay.coerceAtMost(month.lengthOfMonth())),
                            title = sub.title,
                            amount = sub.amount,
                            type = CashFlowEventType.SUBSCRIPTION,
                            sourceId = sub.subscriptionId,
                            description = null
                        )
                    )
                }

                planned.loans.forEach { loan ->
                    list.add(
                        CashFlowEvent(
                            date = month.atDay(loan.paymentDay.coerceAtMost(month.lengthOfMonth())),
                            title = loan.title,
                            amount = loan.amount,
                            type = CashFlowEventType.LOAN,
                            sourceId = loan.loanId,
                            description = "${loan.currentInstallment}/${loan.totalInstallments}"
                        )
                    )
                }

                val paidFixedExpenseIds = actual.expenses
                    .filter { YearMonth.from(it.expenseDate) == month }
                    .mapNotNull { it.fixedExpenseId }
                    .toSet()
                planned.fixedExpenses.filter { it.fixedExpenseId !in paidFixedExpenseIds }.forEach { fixedExpense ->
                    list.add(
                        CashFlowEvent(
                            date = month.atDay(fixedExpense.dayOfMonth.coerceAtMost(month.lengthOfMonth())),
                            title = fixedExpense.title,
                            amount = fixedExpense.amount,
                            type = CashFlowEventType.FIXED_EXPENSE,
                            sourceId = fixedExpense.fixedExpenseId,
                            description = "Sabit gider"
                        )
                    )
                }

                list.sortedBy { it.date }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
}

private const val STATEMENT_LOOKBACK_MONTHS = 2L

private data class CashFlowActualInputs(
    val incomes: List<Income>,
    val expenses: List<Expense>,
    val statementWindowExpenses: List<Expense>,
    val adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>,
    val adjustments: List<ExpenseAdjustment>,
    val statementRules: List<CreditCardStatementRule>
)

private data class CashFlowPlannedInputs(
    val accounts: List<PaymentAccount>,
    val subscriptions: List<SubscriptionMonthlyPayment>,
    val loans: List<LoanMonthlyPayment>,
    val fixedExpenses: List<FixedExpenseMonthlyPayment>,
    val salaryRule: SalaryRule?
)
