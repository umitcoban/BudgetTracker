package com.umit.budgettracker.core.domain.model

import java.time.LocalDate

/**
 * A holding the user owns. Quantities use [com.umit.budgettracker.core.domain.calculator.AssetValuationRules.QUANTITY_SCALE]
 * (micro-units, so 2,5 gr = 2_500_000) and prices use
 * [com.umit.budgettracker.core.domain.calculator.AssetValuationRules.PRICE_SCALE] (TRY with six decimals).
 *
 * Priced holdings (gold, currency, fund, crypto, stock) point at a [MarketPrice] through [priceCode];
 * cash, deposits and "other" carry their TRY value directly in [manualValue] (kuruş).
 */
data class Asset(
    val id: Long,
    val name: String,
    val type: AssetType,
    val priceCode: String?,
    val quantity: Long,
    val manualValue: Long?,
    /** Total purchase cost in kuruş; null when unknown (no profit/loss shown). */
    val costAmount: Long?,
    /** Free-form allocation label for the distribution chart, e.g. "Para piyasası", "Altın". */
    val allocationTag: String?,
    /** Deposit gross annual rate in basis points (%45 → 4500). */
    val interestRateBp: Int? = null,
    val startDate: LocalDate? = null,
    val maturityDate: LocalDate? = null,
    val isEmergencyFund: Boolean,
    val isActive: Boolean,
    val note: String?
)

enum class AssetType(val label: String) {
    GOLD("Altın"),
    CURRENCY("Döviz"),
    FUND("Fon"),
    DEPOSIT("Mevduat"),
    CASH("Nakit / Banka"),
    CRYPTO("Kripto"),
    STOCK("Hisse"),
    OTHER("Diğer");

    /** Types whose value comes from a market price rather than a typed-in TRY amount. */
    val isPriced: Boolean get() = this in setOf(GOLD, CURRENCY, FUND, CRYPTO, STOCK)
}

/** Latest known TRY price for a [code]; [price] is scaled by `PRICE_SCALE`. */
data class MarketPrice(
    val code: String,
    val name: String,
    val price: Long,
    val previousPrice: Long?,
    val source: String,
    val updatedAt: Long
)

data class PriceAlert(
    val id: Long,
    val priceCode: String,
    val condition: AlertCondition,
    /** Threshold price, `PRICE_SCALE`. */
    val threshold: Long,
    val isActive: Boolean,
    val lastTriggeredAt: Long?,
    val note: String?
)

enum class AlertCondition(val label: String) {
    ABOVE("Üstüne çıkınca"),
    BELOW("Altına inince")
}

/** One holding with its derived value; every amount in kuruş. */
data class AssetValuation(
    val asset: Asset,
    val value: Long,
    val unitPrice: Long?,
    val priceUpdatedAt: Long?,
    val priceSource: String?,
    val profit: Long?,
    /** Profit relative to cost in basis points (%6,90 → 798); null without a cost. */
    val profitBp: Int?,
    /** Deposits only: gross interest accrued up to today. */
    val accruedInterest: Long? = null,
    /** Deposits only: gross interest expected at maturity. */
    val interestAtMaturity: Long? = null,
    val daysToMaturity: Long? = null
)

data class PortfolioSummary(
    val valuations: List<AssetValuation>,
    val totalValue: Long,
    val totalCost: Long,
    val totalProfit: Long,
    val totalProfitBp: Int?,
    val emergencyFundValue: Long,
    /** TRY sums per allocation tag (falls back to the type label), largest first. */
    val allocation: List<Pair<String, Long>>,
    val oldestPriceUpdate: Long?
)
