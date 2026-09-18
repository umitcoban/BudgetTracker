package com.umit.budgettracker.core.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * android.graphics counterparts of core/ui/charts for the PDF report. Same conventions: Long
 * amounts, thin marks, 2pt paper gaps between fills, direct labels in text ink, no dual axis.
 */

internal data class PdfSlice(val label: String, val amount: Long, val color: Int)

internal data class PdfSeries(val name: String, val color: Int)

internal data class PdfBarGroup(val label: String, val values: List<Long>)

internal object PdfPalette {
    const val INK = 0xFF17211F.toInt()
    const val MUTED = 0xFF5F6B67.toInt()
    const val RULE = 0xFFD8DEDA.toInt()
    const val TRACK = 0xFFEBEFEC.toInt()
    const val PAPER = Color.WHITE
    const val ACCENT = 0xFF176B5B.toInt()
    const val CARD = 0xFF9A6512.toInt()
    const val INCOME = 0xFF187A55.toInt()
    const val NEGATIVE = 0xFFB63A46.toInt()
    const val OTHER = 0xFF9E9E9E.toInt()
}

internal class PdfPainter {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfPalette.INK; textSize = 10f }

    fun text(size: Float, color: Int = PdfPalette.INK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): Paint {
        return Paint(text).apply {
            textSize = size
            this.color = color
            isFakeBoldText = bold
            textAlign = align
        }
    }
}

/** Ring chart centred in [bounds]; [slices] must already be folded to a readable count. */
internal fun Canvas.drawDonut(bounds: RectF, slices: List<PdfSlice>, ringWidth: Float, painter: PdfPainter) {
    val total = slices.sumOf { it.amount }
    val diameter = min(bounds.width(), bounds.height()) - ringWidth
    val left = bounds.centerX() - diameter / 2f
    val top = bounds.centerY() - diameter / 2f
    val arc = RectF(left, top, left + diameter, top + diameter)
    val ring = Paint(painter.stroke).apply { strokeWidth = ringWidth }

    if (total <= 0L) {
        ring.color = PdfPalette.TRACK
        drawArc(arc, 0f, 360f, false, ring)
        return
    }
    var start = -90f
    slices.forEach { slice ->
        ring.color = slice.color
        val sweep = slice.amount.toFloat() / total * 360f
        drawArc(arc, start, sweep, false, ring)
        start += sweep
    }
    if (slices.size > 1) {
        val gap = Paint(painter.stroke).apply { strokeWidth = 2f; color = PdfPalette.PAPER }
        val outer = diameter / 2f + ringWidth / 2f + 1f
        val inner = diameter / 2f - ringWidth / 2f - 1f
        start = -90f
        slices.forEach { slice ->
            val radians = Math.toRadians(start.toDouble())
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            drawLine(
                bounds.centerX() + dx * inner, bounds.centerY() + dy * inner,
                bounds.centerX() + dx * outer, bounds.centerY() + dy * outer,
                gap
            )
            start += slice.amount.toFloat() / total * 360f
        }
    }
}

/** Colour dot + label + value + share rows, top-aligned at [x],[y]; returns the y after the last row. */
internal fun Canvas.drawSliceLegend(x: Float, y: Float, width: Float, slices: List<PdfSlice>, painter: PdfPainter): Float {
    val total = slices.sumOf { it.amount }
    val label = painter.text(9f)
    val value = painter.text(9f, align = Paint.Align.RIGHT)
    val share = painter.text(8f, PdfPalette.MUTED, align = Paint.Align.RIGHT)
    var cursor = y
    slices.forEach { slice ->
        painter.fill.color = slice.color
        drawCircle(x + 4f, cursor - 3f, 4f, painter.fill)
        drawText(slice.label, x + 14f, cursor, label)
        drawText(formatMoney(slice.amount), x + width - 36f, cursor, value)
        drawText(if (total > 0L) "%${slice.amount * 100L / total}" else "–", x + width, cursor, share)
        cursor += 15f
    }
    return cursor
}

/**
 * Stacked bars per group with an optional [line] series on the same value axis. Axis labels sit
 * under the plot; the caller draws the legend.
 */
internal fun Canvas.drawStackedBars(
    bounds: RectF,
    groups: List<PdfBarGroup>,
    series: List<PdfSeries>,
    line: List<Long>?,
    lineColor: Int,
    painter: PdfPainter
) {
    if (groups.isEmpty()) return
    val axisLabel = painter.text(8f, PdfPalette.MUTED, align = Paint.Align.CENTER)
    val axisHeight = 14f
    val plotBottom = bounds.bottom - axisHeight
    val plotHeight = plotBottom - bounds.top
    val maxValue = maxOf(
        groups.maxOf { it.values.sum() },
        line?.maxOrNull() ?: 0L,
        1L
    )
    val slot = bounds.width() / groups.size
    val barWidth = min(slot * 0.55f, 22f)

    painter.stroke.color = PdfPalette.RULE
    painter.stroke.strokeWidth = 0.75f
    drawLine(bounds.left, plotBottom, bounds.right, plotBottom, painter.stroke)

    groups.forEachIndexed { index, group ->
        val left = bounds.left + slot * index + (slot - barWidth) / 2f
        var bottom = plotBottom
        val segments = group.values.mapIndexed { seriesIndex, value -> seriesIndex to value }.filter { it.second > 0L }
        segments.forEachIndexed { position, (seriesIndex, value) ->
            val height = value.toFloat() / maxValue * plotHeight
            val top = bottom - height
            painter.fill.color = series[seriesIndex].color
            if (position == segments.lastIndex && height >= 4f) {
                val path = Path().apply {
                    moveTo(left, bottom)
                    lineTo(left, top + 4f)
                    quadTo(left, top, left + 4f, top)
                    lineTo(left + barWidth - 4f, top)
                    quadTo(left + barWidth, top, left + barWidth, top + 4f)
                    lineTo(left + barWidth, bottom)
                    close()
                }
                drawPath(path, painter.fill)
            } else {
                drawRect(left, top, left + barWidth, bottom, painter.fill)
            }
            if (position > 0) {
                painter.fill.color = PdfPalette.PAPER
                drawRect(left, bottom - 1f, left + barWidth, bottom + 1f, painter.fill)
            }
            bottom = top
        }
        drawText(group.label, bounds.left + slot * index + slot / 2f, bounds.bottom - 2f, axisLabel)
    }

    if (line != null && line.size == groups.size) {
        val path = Path()
        line.forEachIndexed { index, value ->
            val x = bounds.left + slot * index + slot / 2f
            val y = plotBottom - value.toFloat() / maxValue * plotHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        painter.stroke.color = lineColor
        painter.stroke.strokeWidth = 1.5f
        drawPath(path, painter.stroke)
        line.forEachIndexed { index, value ->
            val x = bounds.left + slot * index + slot / 2f
            val y = plotBottom - value.toFloat() / maxValue * plotHeight
            painter.fill.color = PdfPalette.PAPER
            drawCircle(x, y, 4f, painter.fill)
            painter.fill.color = lineColor
            drawCircle(x, y, 2.5f, painter.fill)
        }
    }
}

/** Horizontal legend of dots + names starting at [x],[y]. */
internal fun Canvas.drawSeriesLegend(x: Float, y: Float, series: List<PdfSeries>, painter: PdfPainter) {
    val label = painter.text(8f, PdfPalette.MUTED)
    var cursor = x
    series.forEach { entry ->
        painter.fill.color = entry.color
        drawCircle(cursor + 3f, y - 3f, 3f, painter.fill)
        drawText(entry.name, cursor + 10f, y, label)
        cursor += 10f + label.measureText(entry.name) + 14f
    }
}

/** Tiny line for table rows: 1pt stroke, last point marked. */
internal fun Canvas.drawSparkline(bounds: RectF, values: List<Long>, color: Int, painter: PdfPainter) {
    if (values.size < 2) return
    val minValue = values.min()
    val maxValue = values.max()
    val range = (maxValue - minValue).coerceAtLeast(1L).toFloat()
    val stepX = bounds.width() / (values.size - 1)
    fun y(value: Long): Float {
        val normalised = if (maxValue == minValue) 0.5f else (value - minValue) / range
        return bounds.bottom - normalised * bounds.height()
    }
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = bounds.left + stepX * index
        if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
    }
    painter.stroke.color = PdfPalette.RULE
    painter.stroke.strokeWidth = 1f
    drawPath(path, painter.stroke)
    painter.fill.color = color
    drawCircle(bounds.right, y(values.last()), 2f, painter.fill)
}

internal fun formatMoney(minorUnits: Long): String = com.umit.budgettracker.core.util.MoneyFormatter.format(minorUnits)
