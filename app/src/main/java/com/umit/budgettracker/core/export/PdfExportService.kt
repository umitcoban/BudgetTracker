package com.umit.budgettracker.core.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.umit.budgettracker.core.domain.model.MonthlyBudgetSummary
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject

class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun exportReportToPdf(uri: Uri, summary: MonthlyBudgetSummary): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val rows = buildList {
                add("Maaş" to MoneyFormatter.format(summary.salaryAmount))
                add("Ek Gelirler" to MoneyFormatter.format(summary.additionalIncomeAmount))
                add("Toplam Gelir" to MoneyFormatter.format(summary.totalIncomeAmount))
                add("Tasarruf Hedefi" to MoneyFormatter.format(summary.savingGoalAmount))
                add("Toplam Harcama" to MoneyFormatter.format(summary.totalExpenseAmount))
                add("Abonelikler" to MoneyFormatter.format(summary.subscriptionPlannedAmount))
                add("Kredi Ödemeleri" to MoneyFormatter.format(summary.loanPaymentAmount))
                add("Sabit Giderler" to MoneyFormatter.format(summary.fixedExpenseAmount))
                add("Toplam Planlı Sabit Ödeme" to MoneyFormatter.format(summary.projectedFixedPaymentsAmount))
                add("Birikim Önerisi" to MoneyFormatter.format(summary.suggestedSavingAmount))
                add("Kalan (Birikim Sonrası)" to MoneyFormatter.format(summary.remainingAfterSaving))
                add("Sabit Ödemeler Sonrası Kalan" to MoneyFormatter.format(summary.remainingAfterFixedPayments))
                addAll(summary.categorySummaries.map { "Kategori: ${it.categoryName}" to MoneyFormatter.format(it.amount) })
            }

            rows.chunked(32).forEachIndexed { index, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                val paint = Paint().apply { textSize = 12f }
                canvas.drawText("BudgetTracker Aylık Rapor", 50f, 50f, paint)
                canvas.drawText("Dönem: ${DateUtils.formatMonthYear(summary.yearMonth)}", 50f, 72f, paint)
                var y = 105f
                pageRows.forEach { (label, value) ->
                    drawRow(canvas, paint, label, value, 50f, y)
                    y += 20f
                }
                pdfDocument.finishPage(page)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun drawRow(canvas: Canvas, paint: Paint, label: String, value: String, x: Float, y: Float) {
        canvas.drawText(label, x, y, paint)
        canvas.drawText(value, 400f, y, paint)
    }
}
