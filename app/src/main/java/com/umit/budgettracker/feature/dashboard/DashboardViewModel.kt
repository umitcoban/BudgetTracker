package com.umit.budgettracker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.EmergencyFundRules
import com.umit.budgettracker.core.domain.calculator.SavingsGoalRules
import com.umit.budgettracker.core.domain.model.CashForecast
import com.umit.budgettracker.core.domain.model.EmergencyFundStatus
import com.umit.budgettracker.core.domain.model.GoalProgress
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.repository.AssistantRepository
import com.umit.budgettracker.core.domain.repository.SavingsGoalRepository
import com.umit.budgettracker.core.domain.usecase.CashForecastUseCase
import com.umit.budgettracker.core.datastore.SettingsDataStore
import java.time.LocalDate
import com.umit.budgettracker.core.domain.model.DebtRecord
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.domain.model.MonthlySavingGoal
import com.umit.budgettracker.core.domain.model.SalaryRule
import com.umit.budgettracker.core.domain.repository.DebtRepository
import com.umit.budgettracker.core.domain.repository.IncomeRepository
import com.umit.budgettracker.core.domain.repository.SavingGoalRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val calculator: MonthlyBudgetCalculator,
    private val savingGoalRepository: SavingGoalRepository,
    private val debtRepository: DebtRepository,
    private val salaryRepository: SalaryRepository,
    private val incomeRepository: IncomeRepository,
    assetRepository: AssetRepository,
    savingsGoalRepository: SavingsGoalRepository,
    assistantRepository: AssistantRepository,
    settings: SettingsDataStore,
    cashForecast: CashForecastUseCase
) : ViewModel() {

    /** Month-independent extras: portfolio, goals, smart cash forecast, pending drafts. */
    private val extras: Flow<DashboardExtras> = combine(
        combine(assetRepository.observeAssets(), assetRepository.observePrices()) { assets, prices ->
            AssetValuationRules.summarize(assets, prices, LocalDate.now())
        },
        combine(savingsGoalRepository.observeGoals(), savingsGoalRepository.observeContributions()) { goals, contributions -> goals to contributions },
        cashForecast.observe(),
        assistantRepository.observeDrafts(),
        settings.preferences
    ) { portfolio, (goals, contributions), forecast, drafts, prefs ->
        val today = LocalDate.now()
        val progresses = goals.filter { it.isActive }.map { goal ->
            val linked = goal.linkedAssetId?.let { id -> portfolio.valuations.firstOrNull { it.asset.id == id }?.value }
            SavingsGoalRules.progress(goal, contributions, linked, today)
        }
        DashboardExtras(
            portfolioValue = portfolio.totalValue,
            hasAssets = portfolio.valuations.isNotEmpty(),
            emergencyFundValue = portfolio.emergencyFundValue,
            emergencyTargetMonths = prefs.emergencyTargetMonths,
            topGoal = progresses.filter { !it.isCompleted }.minByOrNull { it.goal.priority },
            activeGoalCount = progresses.size,
            cashForecast = forecast,
            draftCount = drafts.size
        )
    }

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = _selectedMonth
        .flatMapLatest { month ->
            val historyMonths = (HISTORY_MONTH_COUNT - 1 downTo 0).map { month.minusMonths(it.toLong()) }
            val monthlyIncomeContext = combine(
                calculator.getSummariesForMonths(historyMonths),
                salaryRepository.observeSalaryForMonth(month),
                incomeRepository.observeIncomesForMonth(month)
            ) { history, salaryRule, additionalIncomes ->
                DashboardIncomeContext(
                    summary = history.last(),
                    previousSummary = history[history.size - 2],
                    history = history,
                    salaryRule = salaryRule,
                    additionalIncomeCount = additionalIncomes.size
                )
            }
            combine(
                monthlyIncomeContext,
                debtRepository.observeOpenDebtRecords(),
                extras
            ) { incomeContext, debts, extras ->
                val essential = EmergencyFundRules.essentialMonthlyAverage(incomeContext.history.dropLast(1))
                DashboardUiState.Success(
                    extras = extras,
                    emergency = EmergencyFundRules.status(essential, extras.emergencyFundValue, extras.emergencyTargetMonths),
                    summary = incomeContext.summary,
                    previousSummary = incomeContext.previousSummary,
                    history = incomeContext.history,
                    effectiveSalaryRule = incomeContext.salaryRule,
                    additionalIncomeCount = incomeContext.additionalIncomeCount,
                    openDebts = debts
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun currentMonth() {
        _selectedMonth.value = YearMonth.now()
    }

    fun updateSavingGoal(amount: Long) {
        viewModelScope.launch {
            savingGoalRepository.upsertSavingGoal(
                MonthlySavingGoal(
                    yearMonth = _selectedMonth.value,
                    amount = amount,
                    note = null
                )
            )
        }
    }

    fun applySuggestedSaving(amount: Long) {
        if (amount <= 0L) return
        updateSavingGoal(amount)
    }

    private companion object {
        /** Selected month plus the five before it, for the KPI sparklines. */
        const val HISTORY_MONTH_COUNT = 6
    }
}

data class DashboardExtras(
    val portfolioValue: Long = 0L,
    val hasAssets: Boolean = false,
    val emergencyFundValue: Long = 0L,
    val emergencyTargetMonths: Int = 6,
    val topGoal: GoalProgress? = null,
    val activeGoalCount: Int = 0,
    val cashForecast: CashForecast? = null,
    val draftCount: Int = 0
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val extras: DashboardExtras,
        val emergency: EmergencyFundStatus,
        val summary: MonthlyBudgetSummary,
        val previousSummary: MonthlyBudgetSummary,
        /** Oldest first, ending with [summary]. */
        val history: List<MonthlyBudgetSummary>,
        val effectiveSalaryRule: SalaryRule?,
        val additionalIncomeCount: Int,
        val openDebts: List<DebtRecord>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

private data class DashboardIncomeContext(
    val summary: MonthlyBudgetSummary,
    val previousSummary: MonthlyBudgetSummary,
    val history: List<MonthlyBudgetSummary>,
    val salaryRule: SalaryRule?,
    val additionalIncomeCount: Int
)
