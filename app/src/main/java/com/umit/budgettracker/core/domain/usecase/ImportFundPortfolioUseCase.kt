package com.umit.budgettracker.core.domain.usecase

import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.network.sources.PriceCatalog
import com.umit.budgettracker.core.parsing.ParsedFundRow
import javax.inject.Inject

data class FundImportResult(val created: Int, val updated: Int, val pricesSaved: Int)

/**
 * Applies confirmed fund rows: every row stores its price; holdings are created or (when
 * [updateHoldings]) brought to the imported quantity and cost. Rows are matched by fund code, so
 * re-importing the same table is idempotent. No account / customer numbers are ever read.
 */
class ImportFundPortfolioUseCase @Inject constructor(
    private val assetRepository: AssetRepository
) {
    suspend operator fun invoke(rows: List<ParsedFundRow>, updateHoldings: Boolean, now: Long = System.currentTimeMillis()): FundImportResult {
        if (rows.isEmpty()) return FundImportResult(0, 0, 0)
        assetRepository.savePrices(rows.map { row ->
            MarketPrice(
                code = PriceCatalog.fundCode(row.code),
                name = row.code,
                price = row.price,
                previousPrice = null,
                source = "İçe aktarma",
                updatedAt = now
            )
        })
        val existing = assetRepository.getAssets().filter { it.type == AssetType.FUND }.associateBy { it.priceCode }
        var created = 0
        var updated = 0
        rows.forEach { row ->
            val code = PriceCatalog.fundCode(row.code)
            val current = existing[code]
            when {
                current == null -> {
                    assetRepository.upsertAsset(
                        Asset(
                            id = 0,
                            name = row.code,
                            type = AssetType.FUND,
                            priceCode = code,
                            quantity = row.quantity,
                            manualValue = null,
                            costAmount = row.cost,
                            allocationTag = null,
                            isEmergencyFund = false,
                            isActive = true,
                            note = null
                        )
                    )
                    created++
                }
                updateHoldings && (current.quantity != row.quantity || (row.cost != null && current.costAmount != row.cost)) -> {
                    assetRepository.upsertAsset(current.copy(quantity = row.quantity, costAmount = row.cost ?: current.costAmount, isActive = true))
                    updated++
                }
            }
        }
        return FundImportResult(created, updated, rows.size)
    }
}
