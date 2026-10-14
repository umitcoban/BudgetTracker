package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.domain.model.Asset
import com.umit.budgettracker.core.domain.model.AssetType
import com.umit.budgettracker.core.domain.model.AssetValuation
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PortfolioSummary
import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Integer-only asset valuation.
 *
 * Rounding rule: `value = quantity × price` is computed exactly with [BigInteger] and rounded
 * half-up to whole kuruş once, at the end. Fund prices carry six decimals (0,140017 ₺) so prices
 * are stored at [PRICE_SCALE]; quantities at [QUANTITY_SCALE] so grams of gold or fractions of a
 * coin are representable.
 */
object AssetValuationRules {
    const val QUANTITY_SCALE = 1_000_000L
    const val PRICE_SCALE = 1_000_000L

    /** quantity (QUANTITY_SCALE) × price (PRICE_SCALE, TRY) → kuruş, half-up. */
    fun valueOf(quantity: Long, price: Long): Long {
        val divisor = BigInteger.valueOf(QUANTITY_SCALE).multiply(BigInteger.valueOf(PRICE_SCALE / 100L))
        val product = BigInteger.valueOf(quantity).multiply(BigInteger.valueOf(price))
        return roundHalfUp(product, divisor).toLong()
    }

    /** Profit over cost in basis points; null when there is no positive cost. */
    fun profitBp(value: Long, cost: Long?): Int? {
        if (cost == null || cost <= 0L) return null
        return roundHalfUp(
            BigInteger.valueOf(value - cost).multiply(BigInteger.valueOf(10_000L)),
            BigInteger.valueOf(cost)
        ).toInt()
    }

    /** Simple gross interest: principal × rate × days / 365. */
    fun simpleInterest(principal: Long, rateBp: Int, days: Long): Long {
        if (principal <= 0L || rateBp <= 0 || days <= 0L) return 0L
        return BigInteger.valueOf(principal)
            .multiply(BigInteger.valueOf(rateBp.toLong()))
            .multiply(BigInteger.valueOf(days))
            .divide(BigInteger.valueOf(10_000L * 365L))
            .toLong()
    }

    fun valuate(asset: Asset, prices: Map<String, MarketPrice>, today: LocalDate): AssetValuation {
        val price = asset.priceCode?.let { prices[it] }
        if (asset.type == AssetType.DEPOSIT) return valuateDeposit(asset, today)
        val value = when {
            asset.type.isPriced && price != null -> valueOf(asset.quantity, price.price)
            else -> asset.manualValue ?: 0L
        }
        return AssetValuation(
            asset = asset,
            value = value,
            unitPrice = price?.price,
            priceUpdatedAt = price?.updatedAt,
            priceSource = price?.source,
            profit = asset.costAmount?.let { value - it },
            profitBp = profitBp(value, asset.costAmount)
        )
    }

    private fun valuateDeposit(asset: Asset, today: LocalDate): AssetValuation {
        val principal = asset.manualValue ?: 0L
        val rate = asset.interestRateBp ?: 0
        val start = asset.startDate
        val maturity = asset.maturityDate
        val accrued = if (start != null) {
            val end = if (maturity != null && maturity.isBefore(today)) maturity else today
            simpleInterest(principal, rate, ChronoUnit.DAYS.between(start, end).coerceAtLeast(0L))
        } else {
            0L
        }
        val atMaturity = if (start != null && maturity != null) {
            simpleInterest(principal, rate, ChronoUnit.DAYS.between(start, maturity).coerceAtLeast(0L))
        } else {
            null
        }
        val value = principal + accrued
        val cost = asset.costAmount ?: principal
        return AssetValuation(
            asset = asset,
            value = value,
            unitPrice = null,
            priceUpdatedAt = null,
            priceSource = null,
            profit = value - cost,
            profitBp = profitBp(value, cost),
            accruedInterest = accrued,
            interestAtMaturity = atMaturity,
            daysToMaturity = maturity?.let { ChronoUnit.DAYS.between(today, it) }
        )
    }

    fun summarize(assets: List<Asset>, prices: List<MarketPrice>, today: LocalDate): PortfolioSummary {
        val priceMap = prices.associateBy { it.code }
        val valuations = assets.filter { it.isActive }.map { valuate(it, priceMap, today) }
        val withCost = valuations.filter { it.asset.costAmount != null || it.asset.type == AssetType.DEPOSIT }
        val totalCost = withCost.sumOf { it.asset.costAmount ?: it.asset.manualValue ?: 0L }
        val totalProfit = withCost.sumOf { it.profit ?: 0L }
        val allocation = valuations
            .groupBy { it.asset.allocationTag?.takeIf { tag -> tag.isNotBlank() } ?: it.asset.type.label }
            .map { (tag, items) -> tag to items.sumOf { it.value } }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
        return PortfolioSummary(
            valuations = valuations.sortedByDescending { it.value },
            totalValue = valuations.sumOf { it.value },
            totalCost = totalCost,
            totalProfit = totalProfit,
            totalProfitBp = profitBp(totalCost + totalProfit, totalCost),
            emergencyFundValue = valuations.filter { it.asset.isEmergencyFund }.sumOf { it.value },
            allocation = allocation,
            oldestPriceUpdate = valuations.mapNotNull { it.priceUpdatedAt }.minOrNull()
        )
    }

    /** Integer quotient rounded half away from zero. */
    internal fun roundHalfUp(numerator: BigInteger, denominator: BigInteger): BigInteger {
        val (quotient, remainder) = numerator.divideAndRemainder(denominator).let { it[0] to it[1] }
        return if (remainder.abs().shiftLeft(1) >= denominator.abs()) {
            quotient + BigInteger.valueOf(numerator.signum().toLong() * denominator.signum().toLong())
        } else {
            quotient
        }
    }
}
