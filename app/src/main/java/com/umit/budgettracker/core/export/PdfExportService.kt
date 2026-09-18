package com.umit.budgettracker.core.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.umit.budgettracker.core.domain.model.CategorySummary
import com.umit.budgettracker.core.domain.model.CategoryTrend
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.absoluteValue

class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Writes a multi-page A4 report: the selected month (figures, category donut, warnings,
     * category table) followed by a twelve-month page (channel bars + income line, month table,
     * category yearly totals).
     */
    fun exportReportToPdf(uri: Uri, data: PdfReportData): Boolean {
        return try {
            val document = PdfDocument()
            val writer = PdfPageWriter(document, "BudgetTracker Aylık Rapor")
            writeMonthPage(writer, data)
            writer.newPage()
            writeYearPage(writer, data)
            writer.finish()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                document.writeTo(outputStream)
            }
            document.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeMonthPage(writer: PdfPageWriter, data: PdfReportData) {
        val summary = data.month
        val previous = data.history.getOrNull(data.history.size - 2)
        val painter = writer.painter

        writer.text("Dönem: ${DateUtils.formatMonthYear(summary.yearMonth)}", painter.text(11f))
        writer.text("Oluşturma: ${LocalDate.now().format(DATE_FORMAT)}", painter.text(9f, PdfPalette.MUTED))
        writer.space(14f)

        // Hero figure
        val remaining = summary.remainingAfterSavingAndFixedPayments
        writer.text("Plan sonrası kullanılabilir", painter.text(10f, PdfPalette.MUTED))
        writer.text(
            formatMoney(remaining),
            painter.text(26f, if (remaining < 0L) PdfPalette.NEGATIVE else PdfPalette.ACCENT, bold = true),
            lineHeight = 32f
        )
        if (previous != null) {
            val diff = remaining - previous.remainingAfterSavingAndFixedPayments
            val direction = if (diff >= 0L) "daha yüksek" else "daha düşük"
            writer.text(
                "Önceki aya göre ${formatMoney(diff.absoluteValue)} $direction",
                painter.text(9f, PdfPalette.MUTED)
            )
        }
        writer.space(12f)

        writer.section("Ayın rakamları")
        val rows = listOf(
            "Maaş" to summary.salaryAmount,
            "Ek gelirler" to summary.additionalIncomeAmount,
            "Toplam gelir" to summary.totalIncomeAmount,
            "Toplam harcama" to summary.totalExpenseAmount,
            "Nakit / banka harcaması" to summary.directExpenseAmount,
            "Kredi kartı ekstresi" to summary.creditCardPaymentAmount,
            "Abonelikler (planlı)" to summary.subscriptionPlannedAmount,
            "Kredi ödemeleri" to summary.loanPaymentAmount,
            "Sabit giderler" to summary.fixedExpenseAmount,
            "Tasarruf hedefi" to summary.savingGoalAmount,
            "Birikim önerisi" to summary.suggestedSavingAmount,
            "Sabit ödemeler sonrası kalan" to summary.remainingAfterFixedPayments
        )
        writer.keyValueGrid(rows.map { (label, amount) -> label to formatMoney(amount) }, columns = 2)
        writer.space(10f)

        if (summary.warnings.isNotEmpty()) {
            writer.section("Bu ay dikkat")
            summary.warnings.forEach { warning ->
                writer.bullet(warning.message, painter.text(9f, PdfPalette.NEGATIVE))
            }
            writer.space(8f)
        }

        val spent = summary.categorySummaries.filter { it.amount > 0L }
        if (spent.isNotEmpty()) {
            writer.section("Kategori dağılımı")
            val slices = spent
                .map { PdfSlice(it.categoryName, it.amount, it.colorValue) }
                .foldTail(MAX_DONUT_SLICES)
            val chartHeight = maxOf(120f, slices.size * 15f + 10f)
            writer.ensureSpace(chartHeight + 10f)
            val top = writer.y
            writer.canvas.drawDonut(
                RectF(writer.left, top, writer.left + 120f, top + 120f),
                slices,
                ringWidth = 18f,
                painter = painter
            )
            writer.canvas.drawSliceLegend(
                x = writer.left + 140f,
                y = top + 14f,
                width = writer.contentWidth - 140f,
                slices = slices,
                painter = painter
            )
            writer.y = top + chartHeight + 10f

            writer.categoryTable(summary.categorySummaries, summary.totalExpenseAmount)
        }
    }

    private fun writeYearPage(writer: PdfPageWriter, data: PdfReportData) {
        val painter = writer.painter
        val history = data.history
        val first = history.first().yearMonth
        val last = history.last().yearMonth
        writer.text(
            "Son ${history.size} ay: ${DateUtils.formatMonthYear(first)} – ${DateUtils.formatMonthYear(last)}",
            painter.text(11f)
        )
        writer.space(12f)

        writer.section("Gelir ve harcama")
        val series = listOf(
            PdfSeries("Nakit / banka", PdfPalette.ACCENT),
            PdfSeries("Kredi kartı", PdfPalette.CARD),
            PdfSeries("Gelir", PdfPalette.INCOME)
        )
        writer.ensureSpace(180f)
        writer.canvas.drawSeriesLegend(writer.left, writer.y + 4f, series, painter)
        writer.y += 12f
        writer.canvas.drawStackedBars(
            bounds = RectF(writer.left, writer.y, writer.right, writer.y + 150f),
            groups = history.map { month ->
                PdfBarGroup(
                    label = DateUtils.formatMonthYear(month.yearMonth).take(3),
                    values = listOf(month.directExpenseAmount, month.creditCardPaymentAmount)
                )
            },
            series = series.take(2),
            line = history.map { it.totalIncomeAmount },
            lineColor = PdfPalette.INCOME,
            painter = painter
        )
        writer.y += 160f

        writer.section("Aylara göre sonuç")
        writer.table(
            headers = listOf("Ay", "Gelir", "Harcama", "Plan sonrası kalan", "Harcama / gelir"),
            widths = listOf(0.22f, 0.2f, 0.2f, 0.22f, 0.16f),
            rows = history.map { month ->
                val ratio = if (month.totalIncomeAmount > 0L) "%${month.totalExpenseAmount * 100L / month.totalIncomeAmount}" else "–"
                listOf(
                    DateUtils.formatMonthYear(month.yearMonth),
                    formatMoney(month.totalIncomeAmount),
                    formatMoney(month.totalExpenseAmount),
                    formatMoney(month.remainingAfterSavingAndFixedPayments),
                    ratio
                )
            } + listOf(
                listOf(
                    "Toplam",
                    formatMoney(history.sumOf { it.totalIncomeAmount }),
                    formatMoney(history.sumOf { it.totalExpenseAmount }),
                    formatMoney(history.sumOf { it.remainingAfterSavingAndFixedPayments }),
                    history.sumOf { it.totalIncomeAmount }.let { income ->
                        if (income > 0L) "%${history.sumOf { it.totalExpenseAmount } * 100L / income}" else "–"
                    }
                )
            ),
            boldLastRow = true
        )
        writer.space(10f)

        val trends = data.categoryTrends.filter { trend -> trend.points.any { it.amount > 0L } }
        if (trends.isNotEmpty()) {
            writer.section("Kategori toplamları")
            writer.categoryTrendTable(trends.take(MAX_TREND_ROWS))
        }
    }

    private fun List<PdfSlice>.foldTail(keep: Int): List<PdfSlice> {
        if (size <= keep + 1) return this
        return take(keep) + PdfSlice("Diğer", drop(keep).sumOf { it.amount }, PdfPalette.OTHER)
    }

    private companion object {
        const val MAX_DONUT_SLICES = 5
        const val MAX_TREND_ROWS = 12
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}

/**
 * Cursor-based A4 page writer: keeps a running y, starts a new page when a block would not fit,
 * and stamps the header and page number on every page.
 */
private class PdfPageWriter(
    private val document: PdfDocument,
    private val title: String
) {
    val painter = PdfPainter()
    val left = MARGIN
    val right = PAGE_WIDTH - MARGIN
    val contentWidth = right - left
    var y = 0f

    private var page: PdfDocument.Page? = null
    private var pageNumber = 0

    val canvas: Canvas get() = page!!.canvas

    init {
        newPage()
    }

    fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber += 1
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create()
        page = document.startPage(info)
        canvas.drawText(title, left, MARGIN, painter.text(14f, bold = true))
        canvas.drawText("Sayfa $pageNumber", right, MARGIN, painter.text(8f, PdfPalette.MUTED, align = Paint.Align.RIGHT))
        painter.stroke.color = PdfPalette.RULE
        painter.stroke.strokeWidth = 0.75f
        canvas.drawLine(left, MARGIN + 8f, right, MARGIN + 8f, painter.stroke)
        y = MARGIN + 28f
    }

    fun finish() {
        page?.let { document.finishPage(it) }
        page = null
    }

    fun ensureSpace(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN) newPage()
    }

    fun space(height: Float) {
        y += height
    }

    fun text(value: String, paint: Paint, lineHeight: Float = paint.textSize + 5f) {
        ensureSpace(lineHeight)
        canvas.drawText(value, left, y + paint.textSize, paint)
        y += lineHeight
    }

    fun section(heading: String) {
        ensureSpace(24f)
        y += 4f
        canvas.drawText(heading, left, y + 11f, painter.text(11f, bold = true))
        y += 20f
    }

    fun bullet(value: String, paint: Paint) {
        ensureSpace(14f)
        canvas.drawText("•", left, y + paint.textSize, paint)
        canvas.drawText(value, left + 10f, y + paint.textSize, paint)
        y += paint.textSize + 5f
    }

    fun keyValueGrid(entries: List<Pair<String, String>>, columns: Int) {
        val columnWidth = contentWidth / columns
        val label = painter.text(9f, PdfPalette.MUTED)
        val value = painter.text(9f, align = Paint.Align.RIGHT)
        entries.chunked(columns).forEach { row ->
            ensureSpace(16f)
            row.forEachIndexed { index, (key, amount) ->
                val x = left + columnWidth * index
                canvas.drawText(key, x, y + 10f, label)
                canvas.drawText(amount, x + columnWidth - 12f, y + 10f, value)
            }
            y += 16f
        }
    }

    fun table(headers: List<String>, widths: List<Float>, rows: List<List<String>>, boldLastRow: Boolean = false) {
        val headerPaint = painter.text(8f, PdfPalette.MUTED, bold = true)
        val cellPaint = painter.text(9f)
        val boldPaint = painter.text(9f, bold = true)
        drawTableRow(headers, widths, headerPaint, 14f)
        rule()
        rows.forEachIndexed { index, row ->
            val paint = if (boldLastRow && index == rows.lastIndex) boldPaint else cellPaint
            if (boldLastRow && index == rows.lastIndex) rule()
            drawTableRow(row, widths, paint, 15f)
        }
    }

    fun categoryTable(categories: List<CategorySummary>, totalExpenseAmount: Long) {
        val widths = listOf(0.34f, 0.2f, 0.12f, 0.2f, 0.14f)
        table(
            headers = listOf("Kategori", "Tutar", "Pay", "Bütçe", "Kullanım"),
            widths = widths,
            rows = categories.map { category ->
                val share = if (totalExpenseAmount > 0L) "%${category.amount * 100L / totalExpenseAmount}" else "–"
                val budget = category.budgetLimit
                listOf(
                    category.categoryName,
                    formatMoney(category.amount),
                    share,
                    budget?.let { formatMoney(it) } ?: "–",
                    budget?.takeIf { it > 0L }?.let { "%${category.amount * 100L / it}" } ?: "–"
                )
            }
        )
    }

    fun categoryTrendTable(trends: List<CategoryTrend>) {
        val widths = listOf(0.3f, 0.2f, 0.2f, 0.16f, 0.14f)
        val headerPaint = painter.text(8f, PdfPalette.MUTED, bold = true)
        val cellPaint = painter.text(9f)
        drawTableRow(listOf("Kategori", "Toplam", "Aylık ortalama", "Eğilim", "Son ay"), widths, headerPaint, 14f)
        rule()
        trends.forEach { trend ->
            ensureSpace(16f)
            val total = trend.points.sumOf { it.amount }
            val average = total / trend.points.size
            val change = trend.changePercent?.let { if (it >= 0) "+%$it" else "−%${it.absoluteValue}" } ?: "–"
            val cells = listOf(trend.categoryName, formatMoney(total), formatMoney(average), "", change)
            val rowTop = y
            drawTableRow(cells, widths, cellPaint, 16f)
            val sparkLeft = left + contentWidth * (widths[0] + widths[1] + widths[2]) + 4f
            val sparkWidth = contentWidth * widths[3] - 12f
            canvas.drawSparkline(
                RectF(sparkLeft, rowTop + 3f, sparkLeft + sparkWidth, rowTop + 12f),
                trend.points.map { it.amount },
                trend.colorValue,
                painter
            )
        }
    }

    private fun drawTableRow(cells: List<String>, widths: List<Float>, paint: Paint, rowHeight: Float) {
        ensureSpace(rowHeight)
        var x = left
        cells.forEachIndexed { index, cell ->
            val width = contentWidth * widths[index]
            val alignRight = index > 0
            val cellPaint = Paint(paint).apply { textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT }
            canvas.drawText(cell, if (alignRight) x + width - 6f else x, y + rowHeight - 4f, cellPaint)
            x += width
        }
        y += rowHeight
    }

    private fun rule() {
        painter.stroke.color = PdfPalette.RULE
        painter.stroke.strokeWidth = 0.5f
        canvas.drawLine(left, y, right, y, painter.stroke)
        y += 2f
    }

    private companion object {
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
    }
}
