package com.umit.budgettracker.feature.inflation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.InflationRules
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.SalaryRules
import com.umit.budgettracker.core.domain.model.InflationRate
import com.umit.budgettracker.core.domain.repository.InflationRepository
import com.umit.budgettracker.core.domain.repository.SalaryRepository
import com.umit.budgettracker.core.network.sources.MarketDataService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class RealValueLine(val title: String, val detail: String, val changeBp: Int?, val estimated: Boolean)

data class InflationUiState(
    val rates: List<InflationRate> = emptyList(),
    val last12Bp: Int? = null,
    val last12Estimated: Boolean = false,
    val latest: InflationRate? = null,
    val inconsistencies: List<InflationRules.Inconsistency> = emptyList(),
    val missingMonths: List<YearMonth> = emptyList(),
    val realLines: List<RealValueLine> = emptyList()
)

@HiltViewModel
class InflationViewModel @Inject constructor(
    private val repository: InflationRepository,
    private val marketDataService: MarketDataService,
    salaryRepository: SalaryRepository,
    calculator: MonthlyBudgetCalculator
) : ViewModel() {
    private val currentMonth = YearMonth.now()
    private val comparisonMonths = listOf(currentMonth.minusMonths(13), currentMonth.minusMonths(1))

    val uiState: StateFlow<InflationUiState> = combine(
        repository.observeAll(),
        salaryRepository.observeAllSalaryRules(),
        calculator.getSummariesForMonths(comparisonMonths)
    ) { rates, salaryRules, summaries ->
        val latest = rates.maxByOrNull { it.month }
        val lastMonth = currentMonth.minusMonths(1)
        val factor = InflationRules.factor(rates, lastMonth.minusMonths(12), lastMonth)
        val lines = mutableListOf<RealValueLine>()

        val salaryNow = SalaryRules.effectiveForMonth(salaryRules, lastMonth)?.amount
        val salaryYearAgo = SalaryRules.effectiveForMonth(salaryRules, lastMonth.minusMonths(12))?.amount
        if (salaryNow != null && salaryYearAgo != null && salaryYearAgo > 0L) {
            val real = InflationRules.realChangeBp(rates, salaryYearAgo, lastMonth.minusMonths(12), salaryNow, lastMonth)
            val adjusted = InflationRules.realValue(rates, salaryYearAgo, lastMonth.minusMonths(12), lastMonth)
            lines += RealValueLine(
                title = "Maaşının reel değişimi (12 ay)",
                detail = "Bir yıl önceki maaşının bugünkü karşılığı ${adjusted?.amount?.let { com.umit.budgettracker.core.util.MoneyFormatter.format(it) } ?: "—"}; " +
                    "şu anki maaşın ${com.umit.budgettracker.core.util.MoneyFormatter.format(salaryNow)}.",
                changeBp = real,
                estimated = adjusted?.isEstimated ?: false
            )
        }
        val spendYearAgo = summaries.firstOrNull()?.totalExpenseAmount ?: 0L
        val spendLast = summaries.lastOrNull()?.totalExpenseAmount ?: 0L
        if (spendYearAgo > 0L && spendLast > 0L) {
            val real = InflationRules.realChangeBp(rates, spendYearAgo, comparisonMonths.first(), spendLast, comparisonMonths.last())
            lines += RealValueLine(
                title = "Harcamaların reel değişimi",
                detail = "${com.umit.budgettracker.core.util.DateUtils.formatMonthYear(comparisonMonths.last())} harcaması, bir yıl önceki aynı ayın enflasyonla güncellenmiş tutarına göre.",
                changeBp = real,
                estimated = false
            )
        }
        InflationUiState(
            rates = rates.sortedByDescending { it.month },
            last12Bp = factor?.changeBp(),
            last12Estimated = factor?.isEstimated ?: false,
            latest = latest,
            inconsistencies = InflationRules.inconsistentMonths(rates),
            missingMonths = InflationRules.missingMonths(rates, lastMonth),
            realLines = lines
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InflationUiState())

    private val _suggestions = MutableStateFlow<List<InflationRate>?>(null)
    /** TCMB rows that are missing or differ from what's stored; null when no fetch is pending. */
    val suggestions: StateFlow<List<InflationRate>?> = _suggestions.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(rate: InflationRate) {
        viewModelScope.launch {
            repository.upsert(rate)
            _message.value = "${com.umit.budgettracker.core.util.DateUtils.formatMonthYear(rate.month)} kaydedildi."
        }
    }

    fun delete(month: YearMonth) {
        viewModelScope.launch { repository.delete(month) }
    }

    fun fetchFromTcmb() {
        viewModelScope.launch {
            _busy.value = true
            val result = marketDataService.fetchCpi()
            _busy.value = false
            result.onSuccess { fetched ->
                val stored = uiState.value.rates.associateBy { it.month }
                val diff = fetched.filter { remote ->
                    val local = stored[remote.month]
                    local == null || local.monthlyRateBp != remote.monthlyRateBp || local.annualRateBp != remote.annualRateBp
                }.sortedByDescending { it.month }
                if (diff.isEmpty()) _message.value = "TCMB tablosundaki tüm aylar zaten kayıtlı."
                else _suggestions.value = diff
            }.onFailure {
                _message.value = "TCMB sayfası okunamadı. Oranları elle girebilirsin."
            }
        }
    }

    fun applySuggestions(selected: List<InflationRate>) {
        viewModelScope.launch {
            selected.forEach { repository.upsert(it) }
            _suggestions.value = null
            _message.value = "${selected.size} ay TCMB verisiyle güncellendi."
        }
    }

    fun dismissSuggestions() {
        _suggestions.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
