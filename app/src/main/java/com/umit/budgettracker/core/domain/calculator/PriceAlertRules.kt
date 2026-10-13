package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.AlertCondition
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PriceAlert
import java.math.BigInteger

data class PriceMove(val price: MarketPrice, val changeBp: Int)

object PriceAlertRules {
    /** An alert that fired stays quiet this long, so a price hovering at the threshold doesn't spam. */
    const val COOLDOWN_MILLIS = 12L * 60 * 60 * 1000

    /** Prices watched for "döviz hareketi" notifications. */
    val WATCHED_MOVES = listOf("FX:USD", "FX:EUR", "GOLD:GRAM")

    fun triggered(alerts: List<PriceAlert>, prices: List<MarketPrice>, now: Long): List<Pair<PriceAlert, MarketPrice>> {
        val byCode = prices.associateBy { it.code }
        return alerts.filter { it.isActive }.mapNotNull { alert ->
            val price = byCode[alert.priceCode] ?: return@mapNotNull null
            val hit = when (alert.condition) {
                AlertCondition.ABOVE -> price.price >= alert.threshold
                AlertCondition.BELOW -> price.price <= alert.threshold
            }
            val cooling = alert.lastTriggeredAt?.let { now - it < COOLDOWN_MILLIS } ?: false
            if (hit && !cooling) alert to price else null
        }
    }

    /** Watched prices that moved at least [thresholdBp] since their previous stored value. */
    fun significantMoves(prices: List<MarketPrice>, thresholdBp: Int): List<PriceMove> =
        prices.filter { it.code in WATCHED_MOVES }.mapNotNull { price ->
            val previous = price.previousPrice ?: return@mapNotNull null
            if (previous <= 0L) return@mapNotNull null
            val bp = AssetValuationRules.roundHalfUp(
                BigInteger.valueOf(price.price - previous).multiply(BigInteger.valueOf(10_000L)),
                BigInteger.valueOf(previous)
            ).toInt()
            if (kotlin.math.abs(bp) >= thresholdBp) PriceMove(price, bp) else null
        }
}
