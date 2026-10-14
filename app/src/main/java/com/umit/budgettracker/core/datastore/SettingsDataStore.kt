package com.umit.budgettracker.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Device-local preferences. None of these are part of JSON / ZIP backups on purpose: the app
 * lock (PIN hash), notification-capture allow-list and source toggles belong to this phone.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme")
    private val currencyKey = stringPreferencesKey("currency")

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[themeKey] ?: "system"
    }

    val currencyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[currencyKey] ?: "TRY"
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme
        }
    }

    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[currencyKey] = currency
        }
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { it.toAppPreferences() }

    suspend fun current(): AppPreferences = preferences.first()

    suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        context.dataStore.edit { stored ->
            val next = transform(stored.toAppPreferences())
            stored[Keys.sourceTcmb] = next.sourceTcmb
            stored[Keys.sourceTruncgil] = next.sourceTruncgil
            stored[Keys.sourceGoldApi] = next.sourceGoldApi
            stored[Keys.sourceHolidays] = next.sourceHolidays
            stored[Keys.sourceCrypto] = next.sourceCrypto
            stored[Keys.notifyInflation] = next.notifyInflation
            stored[Keys.notifyCash] = next.notifyCash
            stored[Keys.notifyWeekly] = next.notifyWeekly
            stored[Keys.notifyPriceAlerts] = next.notifyPriceAlerts
            stored[Keys.notifyFxMove] = next.notifyFxMove
            stored[Keys.fxMoveThresholdBp] = next.fxMoveThresholdBp
            stored[Keys.notifyDeposit] = next.notifyDeposit
            stored[Keys.notifyPrePayment] = next.notifyPrePayment
            stored[Keys.emergencyTargetMonths] = next.emergencyTargetMonths
            stored[Keys.lockEnabled] = next.lockEnabled
            stored[Keys.lockDelaySeconds] = next.lockDelaySeconds
            stored[Keys.secureScreen] = next.secureScreen
            stored[Keys.captureEnabled] = next.captureEnabled
            stored[Keys.capturePackages] = next.capturePackages
            stored[Keys.widgetCategoryIds] = next.widgetCategoryIds.joinToString(",")
            next.pinHash?.let { stored[Keys.pinHash] = it } ?: stored.remove(Keys.pinHash)
            next.pinSalt?.let { stored[Keys.pinSalt] = it } ?: stored.remove(Keys.pinSalt)
            stored[Keys.pinLength] = next.pinLength
            next.lastRefreshAt?.let { stored[Keys.lastRefreshAt] = it } ?: stored.remove(Keys.lastRefreshAt)
            next.lastRefreshMessage?.let { stored[Keys.lastRefreshMessage] = it } ?: stored.remove(Keys.lastRefreshMessage)
            next.holidaysSyncedAt?.let { stored[Keys.holidaysSyncedAt] = it } ?: stored.remove(Keys.holidaysSyncedAt)
            next.lastInflationReminderMonth?.let { stored[Keys.lastInflationReminderMonth] = it } ?: stored.remove(Keys.lastInflationReminderMonth)
            next.lastCashWarningDate?.let { stored[Keys.lastCashWarningDate] = it } ?: stored.remove(Keys.lastCashWarningDate)
        }
    }

    private fun Preferences.toAppPreferences() = AppPreferences(
        sourceTcmb = this[Keys.sourceTcmb] ?: true,
        sourceTruncgil = this[Keys.sourceTruncgil] ?: true,
        sourceGoldApi = this[Keys.sourceGoldApi] ?: true,
        sourceHolidays = this[Keys.sourceHolidays] ?: true,
        sourceCrypto = this[Keys.sourceCrypto] ?: false,
        notifyInflation = this[Keys.notifyInflation] ?: true,
        notifyCash = this[Keys.notifyCash] ?: true,
        notifyWeekly = this[Keys.notifyWeekly] ?: true,
        notifyPriceAlerts = this[Keys.notifyPriceAlerts] ?: true,
        notifyFxMove = this[Keys.notifyFxMove] ?: false,
        fxMoveThresholdBp = this[Keys.fxMoveThresholdBp] ?: 200,
        notifyDeposit = this[Keys.notifyDeposit] ?: true,
        notifyPrePayment = this[Keys.notifyPrePayment] ?: true,
        emergencyTargetMonths = this[Keys.emergencyTargetMonths] ?: 6,
        lockEnabled = this[Keys.lockEnabled] ?: false,
        lockDelaySeconds = this[Keys.lockDelaySeconds] ?: 0,
        secureScreen = this[Keys.secureScreen] ?: false,
        pinHash = this[Keys.pinHash],
        pinSalt = this[Keys.pinSalt],
        pinLength = this[Keys.pinLength] ?: 4,
        captureEnabled = this[Keys.captureEnabled] ?: false,
        capturePackages = this[Keys.capturePackages] ?: emptySet(),
        widgetCategoryIds = this[Keys.widgetCategoryIds]?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList(),
        lastRefreshAt = this[Keys.lastRefreshAt],
        lastRefreshMessage = this[Keys.lastRefreshMessage],
        holidaysSyncedAt = this[Keys.holidaysSyncedAt],
        lastInflationReminderMonth = this[Keys.lastInflationReminderMonth],
        lastCashWarningDate = this[Keys.lastCashWarningDate]
    )

    private object Keys {
        val sourceTcmb = booleanPreferencesKey("source_tcmb")
        val sourceTruncgil = booleanPreferencesKey("source_truncgil")
        val sourceGoldApi = booleanPreferencesKey("source_gold_api")
        val sourceHolidays = booleanPreferencesKey("source_holidays")
        val sourceCrypto = booleanPreferencesKey("source_crypto")
        val notifyInflation = booleanPreferencesKey("notify_inflation")
        val notifyCash = booleanPreferencesKey("notify_cash")
        val notifyWeekly = booleanPreferencesKey("notify_weekly")
        val notifyPriceAlerts = booleanPreferencesKey("notify_price_alerts")
        val notifyFxMove = booleanPreferencesKey("notify_fx_move")
        val fxMoveThresholdBp = intPreferencesKey("fx_move_threshold_bp")
        val notifyDeposit = booleanPreferencesKey("notify_deposit")
        val notifyPrePayment = booleanPreferencesKey("notify_pre_payment")
        val emergencyTargetMonths = intPreferencesKey("emergency_target_months")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockDelaySeconds = intPreferencesKey("lock_delay_seconds")
        val secureScreen = booleanPreferencesKey("secure_screen")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinLength = intPreferencesKey("pin_length")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val capturePackages = stringSetPreferencesKey("capture_packages")
        val widgetCategoryIds = stringPreferencesKey("widget_category_ids")
        val lastRefreshAt = longPreferencesKey("last_refresh_at")
        val lastRefreshMessage = stringPreferencesKey("last_refresh_message")
        val holidaysSyncedAt = longPreferencesKey("holidays_synced_at")
        val lastInflationReminderMonth = stringPreferencesKey("last_inflation_reminder_month")
        val lastCashWarningDate = stringPreferencesKey("last_cash_warning_date")
    }
}

data class AppPreferences(
    val sourceTcmb: Boolean = true,
    val sourceTruncgil: Boolean = true,
    val sourceGoldApi: Boolean = true,
    val sourceHolidays: Boolean = true,
    val sourceCrypto: Boolean = false,
    val notifyInflation: Boolean = true,
    val notifyCash: Boolean = true,
    val notifyWeekly: Boolean = true,
    val notifyPriceAlerts: Boolean = true,
    val notifyFxMove: Boolean = false,
    val fxMoveThresholdBp: Int = 200,
    val notifyDeposit: Boolean = true,
    val notifyPrePayment: Boolean = true,
    val emergencyTargetMonths: Int = 6,
    val lockEnabled: Boolean = false,
    /** 0 = hemen, 60 = 1 dk, 300 = 5 dk. */
    val lockDelaySeconds: Int = 0,
    val secureScreen: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 4,
    val captureEnabled: Boolean = false,
    val capturePackages: Set<String> = emptySet(),
    val widgetCategoryIds: List<Long> = emptyList(),
    val lastRefreshAt: Long? = null,
    val lastRefreshMessage: String? = null,
    val holidaysSyncedAt: Long? = null,
    val lastInflationReminderMonth: String? = null,
    val lastCashWarningDate: String? = null
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null
}
