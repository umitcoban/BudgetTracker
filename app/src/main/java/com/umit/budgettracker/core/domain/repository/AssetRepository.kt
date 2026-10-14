package com.umit.budgettracker.core.domain.repository

import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PriceAlert
import kotlinx.coroutines.flow.Flow

interface AssetRepository {
    fun observeAssets(): Flow<List<Asset>>
    suspend fun getAssets(): List<Asset>
    suspend fun upsertAsset(asset: Asset): Long
    suspend fun deleteAsset(id: Long): AssetDeletionResult

    fun observePrices(): Flow<List<MarketPrice>>
    suspend fun getPrices(): List<MarketPrice>

    /** Stores [prices], keeping each code's previous price for change alerts. */
    suspend fun savePrices(prices: List<MarketPrice>)

    fun observeAlerts(): Flow<List<PriceAlert>>
    suspend fun getAlerts(): List<PriceAlert>
    suspend fun upsertAlert(alert: PriceAlert)
    suspend fun deleteAlert(id: Long)
}

enum class AssetDeletionResult { Deleted, LinkedToGoal }
