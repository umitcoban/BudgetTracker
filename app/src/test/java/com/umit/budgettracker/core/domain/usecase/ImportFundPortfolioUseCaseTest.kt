package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PriceAlert
import com.umit.budgettracker.core.domain.repository.AssetDeletionResult
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.parsing.ParsedFundRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ImportFundPortfolioUseCaseTest {
    private val now = LocalDate.of(2026, 10, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun row(code: String, quantity: Long, price: Long, priceDate: LocalDate? = null) = ParsedFundRow(
        code = code, quantity = quantity, price = price, value = 0, profit = null, cost = 1_000L,
        sourceLine = "", priceDate = priceDate
    )

    @Test
    fun historyPriceNeverReplacesANewerOne() = runBlocking {
        val repository = FakeAssetRepository()
        repository.prices += MarketPrice("FUND:GTA", "GTA", 1_600_000L, null, "TEFAS", now)

        val result = ImportFundPortfolioUseCase(repository)(listOf(row("GTA", 5_000_000L, 1_450_000L, LocalDate.of(2026, 7, 1))), updateHoldings = true, now = now)

        assertEquals(0, result.pricesSaved)
        assertEquals(1_600_000L, repository.prices.single().price)
        assertEquals(1, result.created)
    }

    @Test
    fun soldFundClosesTheHoldingAndCreatesNothing() = runBlocking {
        val repository = FakeAssetRepository()
        repository.assets += Asset(7, "MAC", AssetType.FUND, "FUND:MAC", 3_000_000_000L, null, 250_000L, null, isEmergencyFund = false, isActive = true, note = null)

        val result = ImportFundPortfolioUseCase(repository)(
            listOf(row("MAC", 0L, 700_000L, LocalDate.of(2026, 10, 1)), row("XYZ", 0L, 1_000_000L, LocalDate.of(2026, 10, 1))),
            updateHoldings = true,
            now = now
        )

        assertEquals(0, result.created)
        assertEquals(1, result.updated)
        val mac = repository.assets.single()
        assertEquals(0L, mac.quantity)
        assertFalse(mac.isActive)
    }

    private class FakeAssetRepository : AssetRepository {
        val assets = mutableListOf<Asset>()
        val prices = mutableListOf<MarketPrice>()

        override fun observeAssets(): Flow<List<Asset>> = flowOf(assets)
        override suspend fun getAssets(): List<Asset> = assets.toList()
        override suspend fun upsertAsset(asset: Asset): Long {
            val id = if (asset.id == 0L) (assets.maxOfOrNull { it.id } ?: 0L) + 1 else asset.id
            assets.removeAll { it.id == id }
            assets += asset.copy(id = id)
            return id
        }
        override suspend fun deleteAsset(id: Long): AssetDeletionResult = AssetDeletionResult.Deleted.also { assets.removeAll { it.id == id } }
        override fun observePrices(): Flow<List<MarketPrice>> = flowOf(prices)
        override suspend fun getPrices(): List<MarketPrice> = prices.toList()
        override suspend fun savePrices(prices: List<MarketPrice>) {
            prices.forEach { price -> this.prices.removeAll { it.code == price.code }; this.prices += price }
        }
        override fun observeAlerts(): Flow<List<PriceAlert>> = flowOf(emptyList())
        override suspend fun getAlerts(): List<PriceAlert> = emptyList()
        override suspend fun upsertAlert(alert: PriceAlert) = Unit
        override suspend fun deleteAlert(id: Long) = Unit
    }
}
