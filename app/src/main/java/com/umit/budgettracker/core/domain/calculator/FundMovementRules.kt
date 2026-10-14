package com.umit.budgettracker.core.domain.calculator

import com.umit.budgettracker.core.parsing.ParsedFundMovement
import com.umit.budgettracker.core.parsing.ParsedFundRow
import java.math.BigInteger
import java.time.LocalDate

/** A holding rebuilt from transaction history. Quantity at QUANTITY_SCALE, last price at PRICE_SCALE, money in kuruş. */
data class FundPosition(
    val code: String,
    val name: String?,
    val quantity: Long,
    /** Remaining cost of the units still held (average-cost method). */
    val cost: Long,
    val lastPrice: Long,
    val lastDate: LocalDate,
    val realizedProfit: Long,
    val movementCount: Int,
    /** A sale was larger than the units bought in the file: the history starts after the first purchase. */
    val incompleteHistory: Boolean
)

/**
 * Turns buys and sells into current holdings with the average-cost method brokers use for the
 * "maliyet" column: a buy adds its amount to the cost; a sale removes the sold share of the cost
 * and books the difference to the sale amount as realised profit.
 */
object FundMovementRules {
    fun positions(movements: List<ParsedFundMovement>): List<FundPosition> =
        movements.withIndex()
            .sortedWith(compareBy({ it.value.date }, { it.index }))
            .map { it.value }
            .groupBy { it.code }
            .map { (code, items) -> position(code, items) }
            .sortedBy { it.code }

    private fun position(code: String, items: List<ParsedFundMovement>): FundPosition {
        var quantity = 0L
        var cost = 0L
        var realized = 0L
        var incomplete = false
        items.forEach { movement ->
            if (movement.isBuy) {
                quantity += movement.quantity
                cost += movement.amount
            } else {
                val sold = minOf(movement.quantity, quantity)
                if (movement.quantity > quantity) incomplete = true
                val costOut = if (quantity == 0L) 0L else proportional(cost, sold, quantity)
                realized += movement.amount - costOut
                quantity -= sold
                cost -= costOut
            }
        }
        val last = items.last()
        return FundPosition(
            code = code,
            name = items.firstNotNullOfOrNull { it.name },
            quantity = quantity,
            cost = if (quantity == 0L) 0L else cost,
            lastPrice = last.price,
            lastDate = last.date,
            realizedProfit = realized,
            movementCount = items.size,
            incompleteHistory = incomplete
        )
    }

    /** Positions as importable rows, valued at each fund's last transaction price. */
    fun toRows(positions: List<FundPosition>): List<ParsedFundRow> = positions.map { position ->
        val value = AssetValuationRules.valueOf(position.quantity, position.lastPrice)
        ParsedFundRow(
            code = position.code,
            quantity = position.quantity,
            price = position.lastPrice,
            value = value,
            profit = if (position.quantity == 0L) null else value - position.cost,
            cost = if (position.quantity == 0L) null else position.cost,
            sourceLine = "${position.movementCount} hareket",
            name = position.name,
            priceDate = position.lastDate,
            realizedProfit = position.realizedProfit.takeIf { it != 0L }
        )
    }

    /** total × part / whole, half-up, without overflowing on large quantities. */
    private fun proportional(total: Long, part: Long, whole: Long): Long {
        val product = BigInteger.valueOf(total).multiply(BigInteger.valueOf(part))
        val (quotient, remainder) = product.divideAndRemainder(BigInteger.valueOf(whole)).let { it[0] to it[1] }
        return (if (remainder.shiftLeft(1) >= BigInteger.valueOf(whole)) quotient + BigInteger.ONE else quotient).longValueExact()
    }
}
