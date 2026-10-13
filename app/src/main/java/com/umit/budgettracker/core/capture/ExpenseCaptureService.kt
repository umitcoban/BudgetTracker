package com.umit.budgettracker.core.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.model.ExpenseDraft
import com.umit.budgettracker.core.domain.repository.AssistantRepository
import com.umit.budgettracker.core.parsing.NotificationExpenseParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CaptureEntryPoint {
    fun settings(): SettingsDataStore
    fun assistantRepository(): AssistantRepository
}

/**
 * Optional (off by default). Reads notifications only from apps the user allow-listed, turns a
 * spending message into an [ExpenseDraft] (title + amount + date) and discards the raw text.
 * A draft never counts anywhere until the user approves it on the Taslaklar screen.
 */
class ExpenseCaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deps by lazy { EntryPointAccessors.fromApplication(applicationContext, CaptureEntryPoint::class.java) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val postedAt = sbn.postTime
        val packageName = sbn.packageName
        scope.launch {
            val prefs = deps.settings().current()
            if (!prefs.captureEnabled || packageName !in prefs.capturePackages) return@launch
            val parsed = NotificationExpenseParser.parse(title, text) ?: return@launch
            deps.assistantRepository().addDraft(
                ExpenseDraft(
                    id = 0,
                    title = parsed.merchant,
                    amount = parsed.amount,
                    date = Instant.ofEpochMilli(postedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                    sourceLabel = appLabel(packageName),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
