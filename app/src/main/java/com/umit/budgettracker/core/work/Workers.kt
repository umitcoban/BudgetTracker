package com.umit.budgettracker.core.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umit.budgettracker.core.network.sources.MarketDataService
import com.umit.budgettracker.core.widget.BudgetWidgetUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Workers reach Hilt singletons through this entry point (no hilt-work dependency needed). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun marketDataService(): MarketDataService
    fun backgroundChecks(): BackgroundChecks
    fun widgetUpdater(): BudgetWidgetUpdater
}

private fun Context.workerDeps(): WorkerEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

/** Twice a day with network: refresh public prices, then evaluate price alerts. */
class PriceRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.workerDeps()
        val report = deps.marketDataService().refreshPrices()
        runCatching { deps.backgroundChecks().afterPriceRefresh() }
        runCatching { deps.widgetUpdater().updateAll() }
        // Silent failure: the last known prices stay; only transient errors get a backoff retry.
        return if (report.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

class HolidayRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val stored = applicationContext.workerDeps().marketDataService().refreshHolidays()
        return if (stored == 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

/** Triggered by the 09:00 alarm: inflation reminder, cash warning, pre-payment and deposit notes. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = applicationContext.workerDeps()
        runCatching { deps.backgroundChecks().runDaily() }
        runCatching { deps.widgetUpdater().updateAll() }
        return Result.success()
    }
}

class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { applicationContext.workerDeps().backgroundChecks().weeklySummary() }
        return Result.success()
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { applicationContext.workerDeps().widgetUpdater().updateAll() }
        return Result.success()
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Idempotent: KEEP leaves existing schedules alone so app starts don't reset timers. */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PRICE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            HOLIDAY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HolidayRefreshWorker>(14, TimeUnit.DAYS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            WEEKLY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextSundayEvening(), TimeUnit.MILLISECONDS)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            WIDGET_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(3, TimeUnit.HOURS).build()
        )
    }

    fun refreshPricesNow() {
        workManager.enqueueUniqueWork(
            PRICE_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        )
    }

    fun refreshHolidaysNow() {
        workManager.enqueueUniqueWork(
            HOLIDAY_NOW_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HolidayRefreshWorker>().setConstraints(networkConstraint).build()
        )
    }

    fun runDailyChecks() {
        workManager.enqueueUniqueWork(DAILY_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DailyCheckWorker>().build())
    }

    fun updateWidgets() {
        workManager.enqueueUniqueWork(WIDGET_NOW_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }

    private fun delayUntilNextSundayEvening(): Long {
        val now = LocalDateTime.now()
        var target = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).withHour(20).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return Duration.between(now, target).toMillis()
    }

    private companion object {
        const val PRICE_WORK = "price_refresh"
        const val PRICE_NOW_WORK = "price_refresh_now"
        const val HOLIDAY_WORK = "holiday_refresh"
        const val HOLIDAY_NOW_WORK = "holiday_refresh_now"
        const val WEEKLY_WORK = "weekly_summary"
        const val DAILY_WORK = "daily_checks"
        const val WIDGET_WORK = "widget_refresh"
        const val WIDGET_NOW_WORK = "widget_refresh_now"
    }
}
