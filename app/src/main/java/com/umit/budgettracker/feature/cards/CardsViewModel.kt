package com.umit.budgettracker.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CreditCardStatementCalculator
import com.umit.budgettracker.core.domain.calculator.ExpenseAdjustmentRules
import com.umit.budgettracker.core.domain.calculator.netAmount
import com.umit.budgettracker.core.domain.model.AccountType
import com.umit.budgettracker.core.domain.model.CreditCardStatementPayment
import com.umit.budgettracker.core.domain.model.CreditCardStatementSummary
import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.ExpenseAdjustment
import com.umit.budgettracker.core.domain.model.PaymentAccount
import com.umit.budgettracker.core.domain.model.CreditCardStatementRule
import com.umit.budgettracker.core.domain.repository.CreditCardStatementPaymentRepository
import com.umit.budgettracker.core.domain.repository.ExpenseAdjustmentRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.PaymentAccountRepository
import com.umit.budgettracker.core.domain.repository.CreditCardStatementRuleRepository
import com.umit.budgettracker.core.database.dao.PaymentAccountDao
import com.umit.budgettracker.core.database.mapper.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val statementPaymentRepository: CreditCardStatementPaymentRepository,
    private val statementCalculator: CreditCardStatementCalculator,
    private val statementRuleRepository: CreditCardStatementRuleRepository,
    private val dao: PaymentAccountDao // Using DAO for simple CRUD to save time on repository expansion
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val accounts: StateFlow<List<PaymentAccount>> = repository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statementUiState: StateFlow<List<CardStatementUiModel>> = _selectedMonth
        .flatMapLatest { month ->
            combine(
                repository.observeActiveAccounts(),
                expenseRepository.observeAllExpenses(),
                adjustmentRepository.observeAllAdjustments(),
                statementPaymentRepository.observePaymentsForMonth(month),
                statementRuleRepository.observeAllRules()
            ) { accounts, expenses, adjustments, payments, rules ->
                val adjustmentsByExpenseId = ExpenseAdjustmentRules.groupByExpense(adjustments)
                accounts
                    .filter { it.type == AccountType.CREDIT_CARD }
                    .map { account ->
                        val summary = statementCalculator.calculateStatement(account, month, expenses, rules, adjustments)
                        CardStatementUiModel(
                            accountId = account.id,
                            summary = summary,
                            payment = payments.find { it.accountId == account.id },
                            adjustmentsByExpenseId = adjustmentsByExpenseId
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun addCreditCard(name: String, statementDay: Int, dueDay: Int) {
        viewModelScope.launch {
            dao.insert(
                PaymentAccount(
                    id = 0,
                    name = name,
                    type = AccountType.CREDIT_CARD,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    isActive = true
                ).toEntity()
            )
        }
    }

    fun markStatementPaid(accountId: Long, amount: Long) {
        viewModelScope.launch {
            statementPaymentRepository.setStatementPaid(accountId, _selectedMonth.value, amount)
        }
    }

    fun markStatementUnpaid(accountId: Long) {
        viewModelScope.launch {
            statementPaymentRepository.setStatementUnpaid(accountId, _selectedMonth.value)
        }
    }

    fun toggleAccountActive(account: PaymentAccount) {
        viewModelScope.launch {
            dao.update(account.copy(isActive = !account.isActive).toEntity())
        }
    }

    val statementRules: StateFlow<List<CreditCardStatementRule>> = statementRuleRepository.observeAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Saves the rule from [effectiveFromMonth] onward. With [onlyThisMonth] the month after gets a
     * rule restoring whatever was in force before, so a one-off shift (11 -> 12) does not leak
     * into later statements. An existing rule starting that next month is left untouched.
     */
    fun saveStatementRule(
        accountId: Long,
        effectiveFromMonth: YearMonth,
        statementDay: Int,
        dueDay: Int,
        onlyThisMonth: Boolean = false
    ) {
        viewModelScope.launch {
            val nextMonth = effectiveFromMonth.plusMonths(1)
            val restore = if (onlyThisMonth) {
                val rules = statementRuleRepository.observeAllRules().first()
                val account = repository.getAccountById(accountId)
                val alreadyDefined = rules.any { it.accountId == accountId && it.effectiveFromMonth == nextMonth }
                val current = rules
                    .filter { it.accountId == accountId && !it.effectiveFromMonth.isAfter(nextMonth) }
                    .maxByOrNull { it.effectiveFromMonth }
                val restoreStatementDay = current?.statementDay ?: account?.statementDay
                val restoreDueDay = current?.dueDay ?: account?.dueDay
                if (!alreadyDefined && restoreStatementDay != null && restoreDueDay != null) {
                    CreditCardStatementRule(0, accountId, nextMonth, restoreStatementDay, restoreDueDay)
                } else {
                    null
                }
            } else {
                null
            }
            statementRuleRepository.saveRule(CreditCardStatementRule(0, accountId, effectiveFromMonth, statementDay, dueDay))
            restore?.let { statementRuleRepository.saveRule(it) }
        }
    }
}

data class CardStatementUiModel(
    val accountId: Long,
    val summary: CreditCardStatementSummary,
    val payment: CreditCardStatementPayment?,
    val adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>
) {
    val isPaid: Boolean get() = payment?.isPaid == true

    /** A statement with nothing on it is neither paid nor waiting. */
    val isPending: Boolean get() = !isPaid && totalAmount > 0L
    val totalAmount: Long get() = summary.expenses.sumOf { it.netAmount(adjustmentsByExpenseId) }
    fun netAmount(expense: Expense): Long = expense.netAmount(adjustmentsByExpenseId)
}
