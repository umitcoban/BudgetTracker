package com.umit.budgettracker.core.work

import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.CashFlowProjector
import com.umit.budgettracker.core.domain.calculator.InflationRules
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.calculator.PriceAlertRules
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.CashFlowEventType
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.domain.repository.InflationRepository
import com.umit.budgettracker.core.domain.usecase.CashForecastUseCase
import com.umit.budgettracker.core.navigation.Screen
import com.umit.budgettracker.core.network.sources.PriceCatalog
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** What the background workers check; each check is independent and can be switched off. */
class BackgroundChecks @Inject constructor(
    private val settings: SettingsDataStore,
    private val notifier: AppNotifier,
    private val inflationRepository: InflationRepository,
    private val assetRepository: AssetRepository,
    private val expenseRepository: ExpenseRepository,
    private val projector: CashFlowProjector,
    private val cashForecast: CashForecastUseCase,
    private val calculator: MonthlyBudgetCalculator
) {
    suspend fun runDaily(today: LocalDate = LocalDate.now()) {
        val prefs = settings.current()
        if (prefs.notifyInflation) runCatching { inflationReminder(today, prefs.lastInflationReminderMonth) }
        if (prefs.notifyCash) runCatching { cashWarning(today, prefs.lastCashWarningDate) }
        if (prefs.notifyPrePayment) runCatching { prePaymentNotes(today) }
        if (prefs.notifyDeposit) runCatching { depositMaturity(today) }
    }

    /** TÜİK announces CPI on the 3rd; remind until the previous month is entered. */
    private suspend fun inflationReminder(today: LocalDate, lastReminded: String?) {
        if (today.dayOfMonth !in 3..10) return
        val target = YearMonth.from(today).minusMonths(1)
        if (lastReminded == YearMonth.from(today).toString()) return
        val rates = inflationRepository.getAll()
        if (rates.any { it.month == target }) return
        notifier.notify(
            AppNotifier.Channel.REMINDERS,
            AppNotifier.ID_INFLATION,
            "Enflasyonu gir",
            "${DateUtils.formatMonthYear(target)} TÜFE açıklandı. Aylık ve yıllık oranı girersen reel değer hesapları güncel kalır.",
            Screen.Inflation.route
        )
        settings.update { it.copy(lastInflationReminderMonth = YearMonth.from(today).toString()) }
    }

    private suspend fun cashWarning(today: LocalDate, lastWarned: String?) {
        if (lastWarned == today.toString()) return
        val forecast = cashForecast.current(today) ?: return
        if (forecast.shortfall <= 0L || forecast.lowestDate.isAfter(today.plusDays(CASH_WARNING_DAYS))) return
        val message = CashForecastUseCase.describe(forecast) ?: return
        notifier.notify(AppNotifier.Channel.BUDGET, AppNotifier.ID_CASH, "Nakit açığı riski", message, Screen.CashFlow.route)
        settings.update { it.copy(lastCashWarningDate = today.toString()) }
    }

    /** Foreign-currency subscriptions and weekend/holiday due dates in the next three days. */
    private suspend fun prePaymentNotes(today: LocalDate) {
        val month = YearMonth.from(today)
        val events = projector.observeRange(listOf(month, month.plusMonths(1))).first()
            .filter { it.type.isPlanned && !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(PRE_PAYMENT_DAYS)) }
        if (events.isEmpty()) return

        val fxLines = events.filter { it.type == CashFlowEventType.SUBSCRIPTION && it.description?.contains("kur") == true }.map { event ->
            val previous = event.sourceId?.let { lastMonthSubscriptionAmount(it, month) }
            val diff = previous?.let { event.amount - it }
            buildString {
                append("${event.title} ${daysLabel(today, event.date)}: ≈ ${MoneyFormatter.format(event.amount)} (${event.description?.substringBefore(" ·")})")
                if (diff != null && diff != 0L) {
                    append(if (diff > 0) ", geçen aydan ${MoneyFormatter.format(diff)} fazla" else ", geçen aydan ${MoneyFormatter.format(-diff)} az")
                }
            }
        }
        if (fxLines.isNotEmpty()) {
            notifier.notify(AppNotifier.Channel.REMINDERS, AppNotifier.ID_PRE_PAYMENT, "Döviz aboneliği yaklaşıyor", fxLines.joinToString("\n"), Screen.Subscriptions.route)
        }

        val shiftLines = events.filter { it.bankDayNote != null }.map { "${it.title} (${MoneyFormatter.format(it.amount)}): ${it.bankDayNote}" }
        if (shiftLines.isNotEmpty()) {
            notifier.notify(AppNotifier.Channel.REMINDERS, AppNotifier.ID_BANK_DAY, "Ödeme günü tatile denk geliyor", shiftLines.joinToString("\n"), Screen.CashFlow.route)
        }
    }

    private suspend fun lastMonthSubscriptionAmount(subscriptionId: Long, month: YearMonth): Long? =
        expenseRepository.observeExpensesForMonth(month.minusMonths(1)).first()
            .firstOrNull { it.subscriptionId == subscriptionId }?.amount

    private suspend fun depositMaturity(today: LocalDate) {
        val due = assetRepository.getAssets().filter { asset ->
            asset.isActive && asset.type == AssetType.DEPOSIT && asset.maturityDate?.let { date ->
                !date.isBefore(today) && !date.isAfter(today.plusDays(DEPOSIT_NOTICE_DAYS))
            } == true
        }
        if (due.isEmpty()) return
        val prices = assetRepository.getPrices().associateBy { it.code }
        val text = due.joinToString("\n") { asset ->
            val valuation = AssetValuationRules.valuate(asset, prices, today)
            "${asset.name}: ${daysLabel(today, asset.maturityDate!!)} vade sonu · beklenen brüt faiz ${MoneyFormatter.format(valuation.interestAtMaturity ?: 0L)}"
        }
        notifier.notify(AppNotifier.Channel.REMINDERS, AppNotifier.ID_DEPOSIT, "Mevduat vadesi yaklaşıyor", text, Screen.Assets.route)
    }

    /** After a price refresh: user alerts and (optionally) big FX / gold moves. */
    suspend fun afterPriceRefresh(now: Long = System.currentTimeMillis()) {
        val prefs = settings.current()
        val prices = assetRepository.getPrices()
        if (prefs.notifyPriceAlerts) {
            PriceAlertRules.triggered(assetRepository.getAlerts(), prices, now).forEach { (alert, price) ->
                notifier.notify(
                    AppNotifier.Channel.PRICES,
                    AppNotifier.ID_PRICE_ALERT_BASE + alert.id.toInt(),
                    "${PriceCatalog.displayName(alert.priceCode)} hedefe ulaştı",
                    "Güncel: ${ValueFormats.price(price.price, price.code)} · hedef ${alert.condition.label.lowercase()} ${ValueFormats.price(alert.threshold, alert.priceCode)}",
                    Screen.Assets.route
                )
                assetRepository.upsertAlert(alert.copy(lastTriggeredAt = now))
            }
        }
        if (prefs.notifyFxMove) {
            val moves = PriceAlertRules.significantMoves(prices, prefs.fxMoveThresholdBp)
            if (moves.isNotEmpty()) {
                notifier.notify(
                    AppNotifier.Channel.PRICES,
                    AppNotifier.ID_FX_MOVE,
                    "Kur / altın hareketi",
                    moves.joinToString("\n") { "${it.price.name}: ${ValueFormats.price(it.price.price, it.price.code)} (${InflationRules.formatBp(it.changeBp)})" },
                    Screen.Assets.route
                )
            }
        }
    }

    /** Sunday 20:00: last seven days of spending and what is left this month. */
    suspend fun weeklySummary(today: LocalDate = LocalDate.now()) {
        if (!settings.current().notifyWeekly) return
        val weekStart = today.minusDays(6)
        val expenses = expenseRepository.observeExpensesForDateRange(weekStart, today).first()
        val total = expenses.sumOf { it.amount }
        val top = expenses.groupBy { it.category?.name ?: "Diğer" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }
        val summary = calculator.getSummaryForMonth(YearMonth.from(today)).first()
        val text = buildString {
            append("Bu hafta ${expenses.size} harcama, toplam ${MoneyFormatter.format(total)}.")
            top?.let { append(" En çok: ${it.key} (${MoneyFormatter.format(it.value)}).") }
            append(" Ay sonuna planlı ödemelerden sonra ${MoneyFormatter.format(summary.remainingAfterFixedPayments)} kalıyor.")
        }
        notifier.notify(AppNotifier.Channel.SUMMARY, AppNotifier.ID_WEEKLY, "Haftalık özet", text, Screen.Dashboard.route)
    }

    private fun daysLabel(today: LocalDate, date: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, date)) {
        0L -> "bugün"
        1L -> "yarın"
        else -> "$days gün sonra"
    }

    companion object {
        const val CASH_WARNING_DAYS = 14L
        const val PRE_PAYMENT_DAYS = 3L
        const val DEPOSIT_NOTICE_DAYS = 3L
    }
}
