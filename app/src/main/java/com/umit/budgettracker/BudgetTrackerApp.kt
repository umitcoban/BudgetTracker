package com.umit.budgettracker

import android.app.Application
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BudgetTrackerApp : Application() {
    @Inject lateinit var workScheduler: WorkScheduler

    override fun onCreate() {
        super.onCreate()
        // Price / holiday refresh, weekly summary and widget refresh; KEEP policy, so cheap to repeat.
        runCatching { workScheduler.schedulePeriodic() }
    }
}
