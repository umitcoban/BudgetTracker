package com.umit.budgettracker.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PortfolioSummary
import com.umit.budgettracker.core.domain.model.PriceAlert
import com.umit.budgettracker.core.domain.repository.AssetDeletionResult
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.network.sources.MarketDataService
import com.umit.budgettracker.core.network.sources.PriceCatalog
import com.umit.budgettracker.core.work.BackgroundChecks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.LocalDate
import javax.inject.Inject

data class AssetsUiState(
    val portfolio: PortfolioSummary = PortfolioSummary(emptyList(), 0, 0, 0, null, 0, emptyList(), null),
    val prices: List<MarketPrice> = emptyList(),
    val alerts: List<PriceAlert> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val isLoaded: Boolean = false
) {
    /** Prices of codes the user holds or watches, shown in the "Fiyatlar" section. */
    val relevantPrices: List<MarketPrice>
        get() {
            val codes = assets.mapNotNull { it.priceCode }.toSet() + alerts.map { it.priceCode }
            return prices.filter { it.code in codes || it.code in setOf("FX:USD", "FX:EUR", "GOLD:GRAM") }
                .sortedBy { PriceCatalog.all.indexOfFirst { def -> def.code == it.code }.let { index -> if (index < 0) 999 else index } }
        }
}

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val marketDataService: MarketDataService,
    private val backgroundChecks: BackgroundChecks
) : ViewModel() {
    val uiState: StateFlow<AssetsUiState> = combine(
        repository.observeAssets(),
        repository.observePrices(),
        repository.observeAlerts()
    ) { assets, prices, alerts ->
        AssetsUiState(
            portfolio = AssetValuationRules.summarize(assets, prices, LocalDate.now()),
            prices = prices,
            alerts = alerts,
            assets = assets,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetsUiState())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val report = marketDataService.refreshPrices()
            runCatching { backgroundChecks.afterPriceRefresh() }
            _refreshing.value = false
            _message.value = report.message
        }
    }

    fun saveAsset(asset: Asset, manualPrice: Long?) {
        viewModelScope.launch {
            repository.upsertAsset(asset)
            val code = asset.priceCode
            if (manualPrice != null && code != null) setManualPrice(code, manualPrice)
            _message.value = "${asset.name} kaydedildi."
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch {
            _message.value = when (repository.deleteAsset(asset.id)) {
                AssetDeletionResult.Deleted -> "${asset.name} silindi."
                AssetDeletionResult.LinkedToGoal -> "Bu varlık bir birikim hedefine bağlı. Önce hedefteki bağlantıyı kaldır ya da varlığı pasif yap."
            }
        }
    }

    /**
     * Buy (positive [quantity]) or sell (negative). Selling reduces the cost proportionally so the
     * remaining units keep their average cost.
     */
    fun addTransaction(asset: Asset, quantity: Long, amount: Long) {
        viewModelScope.launch {
            val updated = if (quantity >= 0L) {
                asset.copy(quantity = asset.quantity + quantity, costAmount = (asset.costAmount ?: 0L) + amount)
            } else {
                val sold = (-quantity).coerceAtMost(asset.quantity)
                val remainingCost = asset.costAmount?.let { cost ->
                    if (asset.quantity == 0L) 0L else
                        BigInteger.valueOf(cost).multiply(BigInteger.valueOf(asset.quantity - sold)).divide(BigInteger.valueOf(asset.quantity)).toLong()
                }
                asset.copy(quantity = asset.quantity - sold, costAmount = remainingCost)
            }
            repository.upsertAsset(updated)
            _message.value = if (quantity >= 0L) "Alım eklendi." else "Satış kaydedildi."
        }
    }

    fun setManualPrice(code: String, price: Long) {
        viewModelScope.launch {
            repository.savePrices(
                listOf(MarketPrice(code, PriceCatalog.displayName(code), price, null, "Elle", System.currentTimeMillis()))
            )
        }
    }

    fun saveAlert(alert: PriceAlert) {
        viewModelScope.launch {
            repository.upsertAlert(alert)
            _message.value = "Fiyat alarmı kaydedildi."
        }
    }

    fun deleteAlert(alert: PriceAlert) {
        viewModelScope.launch { repository.deleteAlert(alert.id) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
