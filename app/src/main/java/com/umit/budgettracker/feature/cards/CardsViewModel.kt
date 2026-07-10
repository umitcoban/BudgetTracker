package com.umit.budgettracker.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.CreditCardStatementCalculator
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
                val adjustmentsByExpenseId = adjustments.groupBy { it.expenseId }
                accounts
                    .filter { it.type == AccountType.CREDIT_CARD }
                    .map { account ->
                        val summary = statementCalculator.calculateStatement(account, month, expenses, rules)
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

    fun saveStatementRule(accountId: Long, effectiveFromMonth: YearMonth, statementDay: Int, dueDay: Int) {
        viewModelScope.launch {
            statementRuleRepository.saveRule(CreditCardStatementRule(0, accountId, effectiveFromMonth, statementDay, dueDay))
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
    val totalAmount: Long get() = summary.expenses.sumOf { it.netAmount(adjustmentsByExpenseId) }
    fun netAmount(expense: Expense): Long = expense.netAmount(adjustmentsByExpenseId)
}

private fun Expense.netAmount(adjustmentsByExpenseId: Map<Long, List<ExpenseAdjustment>>): Long {
    return (amount - adjustmentsByExpenseId[id].orEmpty().sumOf { it.amount }).coerceAtLeast(0L)
}
