package com.umit.budgettracker.feature.preferences

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.datastore.AppPreferences
import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.model.Category
import com.umit.budgettracker.core.domain.repository.CategoryRepository
import com.umit.budgettracker.core.network.sources.MarketDataService
import com.umit.budgettracker.core.security.AppLockManager
import com.umit.budgettracker.core.security.PinHasher
import com.umit.budgettracker.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore,
    private val lockManager: AppLockManager,
    private val marketDataService: MarketDataService,
    private val workScheduler: WorkScheduler,
    categoryRepository: CategoryRepository
) : ViewModel() {
    val preferences: StateFlow<AppPreferences> = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    val categories: StateFlow<List<Category>> = categoryRepository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun update(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun enableLock(pin: String) {
        if (!PinHasher.isValidPin(pin)) {
            _message.value = "PIN 4–8 haneli rakamlardan oluşmalı."
            return
        }
        viewModelScope.launch {
            lockManager.setPin(pin)
            settings.update { it.copy(lockEnabled = true) }
            _message.value = "Uygulama kilidi açıldı."
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            lockManager.disableLock()
            _message.value = "Uygulama kilidi kapatıldı."
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            _refreshing.value = true
            val report = marketDataService.refreshPrices()
            val holidays = marketDataService.refreshHolidays()
            _refreshing.value = false
            _message.value = report.message + if (holidays > 0) " $holidays resmi tatil kaydedildi." else ""
            workScheduler.updateWidgets()
        }
    }

    fun isCaptureAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .filter { it.packageName != context.packageName }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
