package com.umit.budgettracker.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.EmergencyFundRules
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.SavingsGoalRules
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.repository.SavingsGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class GoalsUiState(
    val progresses: List<GoalProgress> = emptyList(),
    val contributions: List<GoalContribution> = emptyList(),
    val emergency: EmergencyFundStatus? = null,
    val leftoverThisMonth: Long = 0L,
    val suggestion: List<GoalAllocation> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val isLoaded: Boolean = false
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: SavingsGoalRepository,
    assetRepository: AssetRepository,
    calculator: MonthlyBudgetCalculator,
    settings: SettingsDataStore
) : ViewModel() {
    private val currentMonth = YearMonth.now()
    // Three completed months for the essential-spending average, plus the current month for leftover.
    private val months = (3 downTo 0).map { currentMonth.minusMonths(it.toLong()) }

    val uiState: StateFlow<GoalsUiState> = combine(
        combine(repository.observeGoals(), repository.observeContributions()) { goals, contributions -> goals to contributions },
        combine(assetRepository.observeAssets(), assetRepository.observePrices()) { assets, prices -> assets to prices },
        calculator.getSummariesForMonths(months),
        settings.preferences
    ) { (goals, contributions), (assets, prices), summaries, prefs ->
        val today = LocalDate.now()
        val priceMap = prices.associateBy { it.code }
        val portfolio = AssetValuationRules.summarize(assets, prices, today)
        val progresses = goals.map { goal ->
            val linkedValue = goal.linkedAssetId?.let { id ->
                assets.firstOrNull { it.id == id }?.let { AssetValuationRules.valuate(it, priceMap, today).value }
            }
            SavingsGoalRules.progress(goal, contributions, linkedValue, today)
        }
        val essential = EmergencyFundRules.essentialMonthlyAverage(summaries.dropLast(1))
        val leftover = summaries.last().remainingAfterFixedPayments.coerceAtLeast(0L)
        GoalsUiState(
            progresses = progresses,
            contributions = contributions,
            emergency = EmergencyFundRules.status(essential, portfolio.emergencyFundValue, prefs.emergencyTargetMonths),
            leftoverThisMonth = leftover,
            suggestion = SavingsGoalRules.suggestAllocation(leftover, progresses.filter { it.goal.linkedAssetId == null }),
            assets = assets.filter { it.isActive },
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun saveGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            repository.upsertGoal(goal)
            _message.value = "${goal.name} kaydedildi."
        }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch {
            _message.value = if (repository.deleteGoal(goal.id)) {
                "${goal.name} silindi."
            } else {
                "Bu hedefte katkı kayıtları var. Silmek yerine pasif yapabilir ya da önce katkıları silebilirsin."
            }
        }
    }

    fun addContribution(goalId: Long, amount: Long, date: LocalDate, note: String?) {
        viewModelScope.launch {
            repository.addContribution(GoalContribution(0, goalId, amount, date, note))
            _message.value = if (amount >= 0) "Katkı eklendi." else "Çekim kaydedildi."
        }
    }

    /** User-confirmed suggestion: one contribution per goal, dated today. No money moves anywhere. */
    fun applySuggestion(allocations: List<GoalAllocation>) {
        viewModelScope.launch {
            allocations.forEach { repository.addContribution(GoalContribution(0, it.goalId, it.amount, LocalDate.now(), "Aylık artan öneri")) }
            _message.value = "${allocations.size} hedefe katkı eklendi."
        }
    }

    fun deleteContribution(contribution: GoalContribution) {
        viewModelScope.launch { repository.deleteContribution(contribution.id) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
