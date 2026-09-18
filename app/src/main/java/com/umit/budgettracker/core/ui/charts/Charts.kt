package com.umit.budgettracker.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umit.budgettracker.core.util.MoneyFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.min

/*
 * Small Canvas-based charts shared by Dashboard and Reports. Conventions:
 * - money is Long minor units end to end; only the pixel projection uses Float
 * - marks are thin, fills are separated by a 2dp surface gap, data ends are rounded 4dp
 * - text stays in theme text colors; a colored mark beside it carries identity
 * - every chart with a plot is tappable and reports the selected index back to the caller
 */

data class ChartSlice(val label: String, val amount: Long, val color: Color)

data class ChartSeries(val name: String, val color: Color)

data class StackedBarGroup(val label: String, val values: List<Long>)

private val SegmentGap = 2.dp
private val DataEndRadius = 4.dp
private val LineWidth = 2.dp

/**
 * Part-to-whole ring. Keep [slices] to at most six (fold the tail into "Diğer" before calling).
 * The centre shows the selected slice, or the total when nothing is selected.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 22.dp
) {
    val total = slices.sumOf { it.amount }
    val surface = MaterialTheme.colorScheme.surface
    val track = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val valueStyle = MaterialTheme.typography.titleMedium.copy(color = ink, fontWeight = FontWeight.SemiBold)

    Canvas(
        modifier
            .pointerInput(slices) {
                detectTapGestures { tap ->
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val outer = min(size.width, size.height) / 2f
                    val inner = outer - ringWidth.toPx()
                    val distance = hypot(tap.x - centre.x, tap.y - centre.y)
                    if (distance !in inner..outer || total <= 0L) {
                        onSelect(null)
                        return@detectTapGestures
                    }
                    var angle = Math.toDegrees(atan2(tap.y - centre.y, tap.x - centre.x).toDouble()).toFloat()
                    angle = (angle + 90f + 360f) % 360f
                    var start = 0f
                    val hit = slices.indexOfFirst { slice ->
                        val sweep = slice.amount.toFloat() / total * 360f
                        val inside = angle >= start && angle < start + sweep
                        start += sweep
                        inside
                    }
                    onSelect(hit.takeIf { it >= 0 }?.takeIf { it != selectedIndex })
                }
            }
    ) {
        val stroke = ringWidth.toPx()
        val diameter = min(size.width, size.height) - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        if (total <= 0L) {
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        } else {
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.amount.toFloat() / total * 360f
                val dimmed = selectedIndex != null && selectedIndex != index
                drawArc(
                    color = if (dimmed) slice.color.copy(alpha = 0.35f) else slice.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke)
                )
                start += sweep
            }
            // 2dp surface gaps between segments: radial lines across the ring at each boundary.
            if (slices.size > 1) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = diameter / 2f + stroke / 2f + 1f
                val innerRadius = diameter / 2f - stroke / 2f - 1f
                start = -90f
                slices.forEach { slice ->
                    val radians = Math.toRadians(start.toDouble())
                    val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
                    drawLine(
                        color = surface,
                        start = centre + direction * innerRadius,
                        end = centre + direction * outerRadius,
                        strokeWidth = SegmentGap.toPx()
                    )
                    start += slice.amount.toFloat() / total * 360f
                }
            }
        }

        val selected = selectedIndex?.let { slices.getOrNull(it) }
        val label = selected?.label ?: "Toplam"
        val value = MoneyFormatter.format(selected?.amount ?: total)
        val share = selected?.let { if (total > 0L) " · %${it.amount * 100L / total}" else "" } ?: ""
        val labelLayout = textMeasurer.measure(label + share, labelStyle)
        val valueLayout = textMeasurer.measure(value, valueStyle)
        val blockHeight = labelLayout.size.height + valueLayout.size.height
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawText(
            labelLayout,
            topLeft = Offset(centre.x - labelLayout.size.width / 2f, centre.y - blockHeight / 2f)
        )
        drawText(
            valueLayout,
            topLeft = Offset(centre.x - valueLayout.size.width / 2f, centre.y - blockHeight / 2f + labelLayout.size.height)
        )
    }
}

/** Direct labels for [DonutChart]: colour dot, name, amount, share. Tapping a row selects it. */
@Composable
fun DonutLegend(
    slices: List<ChartSlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.amount }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEachIndexed { index, slice ->
            val dimmed = selectedIndex != null && selectedIndex != index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index.takeIf { it != selectedIndex }) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (dimmed) slice.color.copy(alpha = 0.35f) else slice.color, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    MoneyFormatter.format(slice.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (total > 0L) "%${slice.amount * 100L / total}" else "–",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

/**
 * Vertical stacked bars, one group per label, segments in [series] order from the baseline up.
 * Tapping a bar selects it (others are dimmed); the caller shows the values for the selection.
 */
@Composable
fun StackedBarChart(
    groups: List<StackedBarGroup>,
    series: List<ChartSeries>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp
) {
    val maxTotal = groups.maxOfOrNull { it.values.sum() }?.coerceAtLeast(1L) ?: 1L
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(groups) {
                detectTapGestures { tap ->
                    if (groups.isEmpty()) return@detectTapGestures
                    val slot = size.width / groups.size
                    val index = (tap.x / slot).toInt().coerceIn(0, groups.lastIndex)
                    onSelect(index.takeIf { it != selectedIndex })
                }
            }
    ) {
        if (groups.isEmpty()) return@Canvas
        val axisHeight = textMeasurer.measure("Ay", axisStyle).size.height + 6.dp.toPx()
        val plotHeight = size.height - axisHeight
        val slot = size.width / groups.size
        val barWidth = min(slot * 0.55f, 28.dp.toPx())
        val gap = SegmentGap.toPx()
        val radius = DataEndRadius.toPx()

        drawLine(baseline, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1.dp.toPx())

        groups.forEachIndexed { index, group ->
            val dimmed = selectedIndex != null && selectedIndex != index
            val left = slot * index + (slot - barWidth) / 2f
            var bottom = plotHeight
            val segments = group.values.mapIndexed { seriesIndex, value -> seriesIndex to value }
                .filter { (_, value) -> value > 0L }
            segments.forEachIndexed { position, (seriesIndex, value) ->
                val segmentHeight = value.toFloat() / maxTotal * plotHeight
                val top = bottom - segmentHeight
                val color = series[seriesIndex].color.let { if (dimmed) it.copy(alpha = 0.35f) else it }
                val isTop = position == segments.lastIndex
                drawSegment(Rect(left, top, left + barWidth, bottom), color, if (isTop) radius else 0f)
                if (position > 0) {
                    // surface gap between stacked fills
                    drawRect(surface, Offset(left, bottom - gap / 2f), Size(barWidth, gap))
                }
                bottom = top
            }

            val labelLayout = textMeasurer.measure(group.label, axisStyle)
            drawText(
                labelLayout,
                topLeft = Offset(slot * index + (slot - labelLayout.size.width) / 2f, plotHeight + 6.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawSegment(rect: Rect, color: Color, topRadius: Float) {
    if (topRadius <= 0f || rect.height < topRadius) {
        drawRect(color, rect.topLeft, rect.size)
        return
    }
    val path = Path().apply {
        moveTo(rect.left, rect.bottom)
        lineTo(rect.left, rect.top + topRadius)
        quadraticBezierTo(rect.left, rect.top, rect.left + topRadius, rect.top)
        lineTo(rect.right - topRadius, rect.top)
        quadraticBezierTo(rect.right, rect.top, rect.right, rect.top + topRadius)
        lineTo(rect.right, rect.bottom)
        close()
    }
    drawPath(path, color)
}

/** Legend row for multi-series charts: colour dot + series name, in series order. */
@Composable
fun ChartLegendRow(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(entry.color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact trend line for stat tiles and list rows: 2dp line in a de-emphasis hue with the latest
 * point marked in [markerColor]. No axes, no labels — the surrounding text supplies the number.
 */
@Composable
fun Sparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.outline,
    markerColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).coerceAtLeast(1L).toFloat()
        val markerRadius = 3.dp.toPx()
        val usableHeight = size.height - markerRadius * 2f
        val stepX = (size.width - markerRadius * 2f) / (values.size - 1)

        fun point(index: Int): Offset {
            val normalised = if (maxValue == minValue) 0.5f else (values[index] - minValue) / range
            return Offset(markerRadius + stepX * index, markerRadius + usableHeight * (1f - normalised))
        }

        val path = Path().apply {
            values.indices.forEach { index ->
                val p = point(index)
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(path, lineColor, style = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(markerColor, markerRadius, point(values.lastIndex))
    }
}

/**
 * Folds everything after the first [keep] slices into a single "Diğer" slice so a donut never
 * carries more classes than a reader can separate.
 */
fun List<ChartSlice>.foldTail(keep: Int = 5, otherColor: Color, otherLabel: String = "Diğer"): List<ChartSlice> {
    if (size <= keep + 1) return this
    val head = take(keep)
    val tail = drop(keep)
    return head + ChartSlice(otherLabel, tail.sumOf { it.amount }, otherColor)
}
