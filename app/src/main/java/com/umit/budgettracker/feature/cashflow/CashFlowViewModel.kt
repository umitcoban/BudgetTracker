package com.umit.budgettracker.feature.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CreditCardStatementCalculator
import com.umit.budgettracker.core.domain.calculator.FixedExpenseMonthlyCalculator
import com.umit.budgettracker.core.domain.calculator.LoanMonthlyCalculator
import com.umit.budgettracker.core.domain.calculator.SubscriptionMonthlyCalculator
import com.umit.budgettracker.core.domain.model.CashFlowEvent
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.FixedExpenseMonthlyPayment
import com.umit.budgettracker.core.domain.model.Income
import com.umit.budgettracker.core.domain.model.LoanMonthlyPayment
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.SubscriptionMonthlyPayment
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.IncomeRepository
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
    private val incomeRepository: IncomeRepository,
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
                expenseRepository.observeAllExpenses()
            ) { incomes, expenses, allExpenses ->
                CashFlowActualInputs(incomes, expenses, allExpenses)
            }
            val plannedFlow = combine(
                accountRepository.observeActiveAccounts(),
                subscriptionCalculator.getPaymentsForMonth(month),
                loanCalculator.getPaymentsForMonth(month),
                fixedExpenseCalculator.getPaymentsForMonth(month)
            ) { accounts, subscriptions, loans, fixedExpenses ->
                CashFlowPlannedInputs(accounts, subscriptions, loans, fixedExpenses)
            }

            combine(
                actualFlow,
                plannedFlow
            ) { actual, planned ->
                val list = mutableListOf<CashFlowEvent>()

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
                
                actual.expenses.forEach { e ->
                    list.add(
                        CashFlowEvent(
                            date = e.expenseDate,
                            title = e.title,
                            amount = e.amount,
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
                    val statement = statementCalculator.calculateStatement(acc, month, actual.allExpenses)
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

private data class CashFlowActualInputs(
    val incomes: List<Income>,
    val expenses: List<Expense>,
    val allExpenses: List<Expense>
)

private data class CashFlowPlannedInputs(
    val accounts: List<PaymentAccount>,
    val subscriptions: List<SubscriptionMonthlyPayment>,
    val loans: List<LoanMonthlyPayment>,
    val fixedExpenses: List<FixedExpenseMonthlyPayment>
)
