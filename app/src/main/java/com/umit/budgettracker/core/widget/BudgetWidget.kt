package com.umit.budgettracker.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umit.budgettracker.MainActivity
import com.umit.budgettracker.R
import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Home-screen widget: this month's remaining budget plus quick-add chips. No logic of its own. */
class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WorkScheduler(context.applicationContext).updateWidgets()
    }

    override fun onEnabled(context: Context) {
        WorkScheduler(context.applicationContext).updateWidgets()
    }
}

@Singleton
class BudgetWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calculator: MonthlyBudgetCalculator,
    private val categoryRepository: CategoryRepository,
    private val settings: SettingsDataStore
) {
    suspend fun updateAll() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val month = YearMonth.now()
        val summary = calculator.getSummaryForMonth(month).first()
        val categories = categoryRepository.observeActiveCategories().first()
        val chips = quickCategories(categories, settings.current().widgetCategoryIds)
        val income = summary.totalIncomeAmount
        val spentPercent = if (income > 0L) ((summary.totalExpenseAmount + summary.projectedFixedPaymentsAmount) * 100L / income).toInt() else 0

        val views = RemoteViews(context.packageName, R.layout.widget_budget).apply {
            setTextViewText(R.id.widget_month, DateUtils.formatMonthYear(month))
            setTextViewText(R.id.widget_percent, "%$spentPercent harcandı / planlı")
            setTextViewText(R.id.widget_remaining, MoneyFormatter.format(summary.remainingAfterFixedPayments))
            setTextColor(
                R.id.widget_remaining,
                context.getColor(if (summary.remainingAfterFixedPayments < 0L) R.color.widget_negative else R.color.widget_text)
            )
            setProgressBar(R.id.widget_progress, 100, spentPercent.coerceIn(0, 100), false)
            setOnClickPendingIntent(R.id.widget_root, openApp(REQUEST_OPEN, null))
            setOnClickPendingIntent(R.id.widget_add, openApp(REQUEST_ADD, null, quickAdd = true))
            listOf(R.id.widget_chip_1, R.id.widget_chip_2, R.id.widget_chip_3).forEachIndexed { index, viewId ->
                val category = chips.getOrNull(index)
                if (category == null) {
                    setViewVisibility(viewId, android.view.View.GONE)
                } else {
                    setViewVisibility(viewId, android.view.View.VISIBLE)
                    setTextViewText(viewId, category.name)
                    setOnClickPendingIntent(viewId, openApp(REQUEST_CHIP + index, category.id, quickAdd = true))
                }
            }
        }
        manager.updateAppWidget(ids, views)
    }

    private fun openApp(requestCode: Int, categoryId: Long?, quickAdd: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = if (quickAdd) MainActivity.ACTION_QUICK_ADD else Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            categoryId?.let { putExtra(MainActivity.EXTRA_CATEGORY_ID, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val REQUEST_OPEN = 3100
        const val REQUEST_ADD = 3101
        const val REQUEST_CHIP = 3110
        val DEFAULT_CHIPS = listOf("Market", "Yemek", "Kahve")

        fun quickCategories(categories: List<Category>, chosenIds: List<Long>): List<Category> {
            val chosen = chosenIds.mapNotNull { id -> categories.firstOrNull { it.id == id } }
            if (chosen.isNotEmpty()) return chosen.take(3)
            return DEFAULT_CHIPS.mapNotNull { name -> categories.firstOrNull { it.name == name } }
                .ifEmpty { categories.take(3) }
        }
    }
}
