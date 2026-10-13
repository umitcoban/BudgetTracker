package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.CashFlowProjector
import com.umit.budgettracker.core.domain.calculator.CashForecastRules
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.model.CashForecast
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * "Akıllı nakit uyarısı": starts from the cash/bank balances the user keeps under Varlıklar
 * (type [AssetType.CASH]) and walks the cash-flow calendar forward to find the tightest day.
 */
class CashForecastUseCase @Inject constructor(
    private val projector: CashFlowProjector,
    private val assetRepository: AssetRepository
) {
    fun observe(today: LocalDate = LocalDate.now()): Flow<CashForecast?> {
        val month = YearMonth.from(today)
        return combine(
            projector.observeRange(listOf(month, month.plusMonths(1))),
            assetRepository.observeAssets(),
            assetRepository.observePrices()
        ) { events, assets, prices ->
            val cashAssets = assets.filter { it.isActive && it.type == AssetType.CASH }
            if (cashAssets.isEmpty()) return@combine null
            val balance = AssetValuationRules.summarize(cashAssets, prices, today).totalValue
            CashForecastRules.forecast(balance, events, today, today.plusDays(HORIZON_DAYS))
        }
    }

    suspend fun current(today: LocalDate = LocalDate.now()): CashForecast? = observe(today).first()

    companion object {
        const val HORIZON_DAYS = 45L

        /** "20 Ekim Kart ekstresi 18.400 ₺, hesapta tahmini 14.100 ₺ → 4.300 ₺ açık, maaş 28 Ekim'de." */
        fun describe(forecast: CashForecast): String? {
            if (forecast.shortfall <= 0L) return null
            val pressure = forecast.pressureEvent ?: return null
            val before = forecast.lowestBalance + pressure.amount
            return buildString {
                append("${DateUtils.formatDayMonth(pressure.date)} ${pressure.title} ${MoneyFormatter.format(pressure.amount)}, ")
                append("hesapta tahmini ${MoneyFormatter.format(before.coerceAtLeast(0L))} → ")
                append("${MoneyFormatter.format(forecast.shortfall)} açık")
                forecast.nextIncome?.let { income ->
                    val label = if (income.type == CashFlowEventType.INCOME && income.title == "Maaş") "maaş" else income.title
                    append(", $label ${DateUtils.formatDayMonth(income.date)}'de")
                }
                append(".")
            }
        }
    }
}
