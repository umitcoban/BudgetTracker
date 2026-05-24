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
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            var y = 50f
            
            // Title
            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText("BudgetTracker Aylık Rapor", 50f, y, paint)
            y += 40f

            // Month
            paint.textSize = 14f
            paint.isFakeBoldText = false
            canvas.drawText("Dönem: ${DateUtils.formatMonthYear(summary.yearMonth)}", 50f, y, paint)
            y += 30f

            // Summary section
            drawRow(canvas, paint, "Maaş", MoneyFormatter.format(summary.salaryAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Ek Gelirler", MoneyFormatter.format(summary.additionalIncomeAmount), 50f, y)
            y += 20f
            paint.isFakeBoldText = true
            drawRow(canvas, paint, "Toplam Gelir", MoneyFormatter.format(summary.totalIncomeAmount), 50f, y)
            paint.isFakeBoldText = false
            y += 20f
            drawRow(canvas, paint, "Tasarruf Hedefi", MoneyFormatter.format(summary.savingGoalAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Toplam Harcama", MoneyFormatter.format(summary.totalExpenseAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Abonelikler", MoneyFormatter.format(summary.subscriptionPlannedAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Kredi Ödemeleri", MoneyFormatter.format(summary.loanPaymentAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Sabit Giderler", MoneyFormatter.format(summary.fixedExpenseAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Toplam Planlı Sabit Ödeme", MoneyFormatter.format(summary.projectedFixedPaymentsAmount), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Birikim Önerisi", MoneyFormatter.format(summary.suggestedSavingAmount), 50f, y)
            y += 20f
            paint.isFakeBoldText = true
            drawRow(canvas, paint, "Kalan (Birikim Sonrası)", MoneyFormatter.format(summary.remainingAfterSaving), 50f, y)
            y += 20f
            drawRow(canvas, paint, "Sabit Ödemeler Sonrası Kalan", MoneyFormatter.format(summary.remainingAfterFixedPayments), 50f, y)
            y += 40f

            // Categories
            paint.isFakeBoldText = true
            canvas.drawText("Kategori Dağılımı", 50f, y, paint)
            y += 25f
            paint.isFakeBoldText = false
            
            summary.categorySummaries.forEach { cat ->
                drawRow(canvas, paint, cat.categoryName, MoneyFormatter.format(cat.amount), 70f, y)
                y += 20f
                if (y > 800) return@forEach // Basic overflow protection for MVP
            }

            pdfDocument.finishPage(page)

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
