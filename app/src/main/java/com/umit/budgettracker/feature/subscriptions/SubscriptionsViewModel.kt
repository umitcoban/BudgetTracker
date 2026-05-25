package com.umit.budgettracker.feature.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.SubscriptionMonthlyCalculator
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.*
import com.umit.budgettracker.core.domain.usecase.MarkSubscriptionPaymentAsPaidUseCase
import com.umit.budgettracker.core.domain.usecase.MarkSubscriptionPaymentResult
import com.umit.budgettracker.core.domain.usecase.SyncDueSubscriptionExpensesUseCase
import com.umit.budgettracker.core.network.ExchangeRateResult
import com.umit.budgettracker.core.network.ExchangeRateService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val calculator: SubscriptionMonthlyCalculator,
    private val markSubscriptionPaymentAsPaid: MarkSubscriptionPaymentAsPaidUseCase,
    private val syncDueSubscriptionExpenses: SyncDueSubscriptionExpensesUseCase,
    private val exchangeRateService: ExchangeRateService
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<PaymentAccount>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyPayments: StateFlow<List<SubscriptionMonthlyPayment>> = _selectedMonth
        .flatMapLatest { month -> calculator.getPaymentsForMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubscriptions: StateFlow<List<Subscription>> = repository.observeAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _exchangeRateState = MutableStateFlow<SubscriptionExchangeRateUiState>(SubscriptionExchangeRateUiState.Idle)
    val exchangeRateState: StateFlow<SubscriptionExchangeRateUiState> = _exchangeRateState.asStateFlow()

    fun nextMonth() { _selectedMonth.value = _selectedMonth.value.plusMonths(1) }
    fun previousMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }

    fun addSubscription(
        title: String,
        amount: Long,
        billingDay: Int,
        categoryId: Long,
        accountId: Long,
        month: YearMonth,
        currency: String,
        exchangeRateToTry: Long?,
        exchangeRateScale: Int?,
        exchangeRateSource: String?,
        exchangeRateUpdatedAt: Long?
    ) {
        viewModelScope.launch {
            val sub = Subscription(
                id = 0,
                title = title,
                categoryId = categoryId,
                paymentAccountId = accountId,
                billingDay = billingDay,
                isActive = true,
                note = null,
                originalCurrency = currency.takeIf { it != "TRY" },
                exchangeRateToTry = exchangeRateToTry.takeIf { currency != "TRY" },
                exchangeRateScale = exchangeRateScale.takeIf { currency != "TRY" },
                exchangeRateSource = exchangeRateSource.takeIf { currency != "TRY" },
                exchangeRateUpdatedAt = exchangeRateUpdatedAt.takeIf { currency != "TRY" }
            )
            repository.createSubscriptionWithPrice(sub, amount, month)
            val syncResult = runCatching { syncDueSubscriptionExpenses() }.getOrNull()
            _selectedMonth.value = month
            _message.emit(
                if ((syncResult?.createdCount ?: 0) > 0) {
                    "Abonelik eklendi. Geçmiş vadesi gelen ${syncResult?.createdCount} ödeme harcamalara işlendi."
                } else {
                    "Abonelik eklendi. Gelecek ödemeler projeksiyonda görünecek."
                }
            )
        }
    }

    fun updateSubscription(previousSubscription: Subscription, subscription: Subscription, newAmount: Long?, effectiveMonth: YearMonth?) {
        viewModelScope.launch {
            repository.backfillMissingPriceHistoryCurrency(previousSubscription)
            repository.upsertSubscription(subscription)
            if (newAmount != null && effectiveMonth != null) {
                repository.addPriceHistory(
                    SubscriptionPriceHistory(
                        id = 0,
                        subscriptionId = subscription.id,
                        amount = newAmount,
                        effectiveFromMonth = effectiveMonth,
                        originalCurrency = subscription.originalCurrency ?: "TRY",
                        exchangeRateToTry = subscription.exchangeRateToTry,
                        exchangeRateScale = subscription.exchangeRateScale,
                        exchangeRateSource = subscription.exchangeRateSource,
                        exchangeRateUpdatedAt = subscription.exchangeRateUpdatedAt
                    )
                )
                _selectedMonth.value = effectiveMonth
            }
            runCatching { syncDueSubscriptionExpenses() }
            _message.emit("Abonelik güncellendi.")
        }
    }

    fun markAsPaid(payment: SubscriptionMonthlyPayment) {
        viewModelScope.launch {
            when (markSubscriptionPaymentAsPaid(payment, _selectedMonth.value)) {
                MarkSubscriptionPaymentResult.Created -> _message.emit("Abonelik ödemesi harcamalara işlendi.")
                MarkSubscriptionPaymentResult.AlreadyPaid -> _message.emit("Bu abonelik ödemesi zaten harcamalara işlenmiş.")
            }
        }
    }

    fun cancelSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.upsertSubscription(subscription.copy(cancelledFromMonth = _selectedMonth.value))
            _message.emit("Abonelik seçili aydan itibaren iptal edildi.")
        }
    }

    fun deactivateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.upsertSubscription(subscription.copy(isActive = false))
            _message.emit("Abonelik pasifleştirildi.")
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            if (expenseRepository.hasAnySubscriptionExpense(subscription.id)) {
                _message.emit("Bu aboneliğe bağlı geçmiş harcamalar var. Silmek yerine pasifleştirebilirsiniz.")
            } else {
                repository.deleteSubscription(subscription.id)
                _message.emit("Abonelik silindi.")
            }
        }
    }

    fun fetchExchangeRate(currency: String) {
        if (currency == "TRY") {
            _exchangeRateState.value = SubscriptionExchangeRateUiState.Idle
            return
        }

        viewModelScope.launch {
            _exchangeRateState.value = SubscriptionExchangeRateUiState.Loading
            exchangeRateService.fetchRateToTry(currency)
                .onSuccess { _exchangeRateState.value = SubscriptionExchangeRateUiState.Success(it) }
                .onFailure {
                    _exchangeRateState.value = SubscriptionExchangeRateUiState.Error(
                        "Kur bilgisi alınamadı. Manuel kur girebilirsiniz."
                    )
                }
        }
    }

    fun clearExchangeRateState() {
        _exchangeRateState.value = SubscriptionExchangeRateUiState.Idle
    }
}

sealed interface SubscriptionExchangeRateUiState {
    data object Idle : SubscriptionExchangeRateUiState
    data object Loading : SubscriptionExchangeRateUiState
    data class Success(val rate: ExchangeRateResult) : SubscriptionExchangeRateUiState
    data class Error(val message: String) : SubscriptionExchangeRateUiState
}
