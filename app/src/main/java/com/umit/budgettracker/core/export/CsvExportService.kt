package com.umit.budgettracker.core.export

import android.content.Context
import android.net.Uri
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import com.umit.budgettracker.core.util.MoneyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import javax.inject.Inject

class CsvExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository
) {
    suspend fun exportExpensesToCsv(uri: Uri): Boolean {
        return try {
            val expenses = expenseRepository.observeAllExpenses().first()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                    // Header
                    writer.write("ID,Tarih,Başlık,Miktar (Kuruş),Miktar (TL),Kategori,Hesap,Hesap Türü,Not,Taksit Grubu ID,Abonelik ID,Kredi ID,Sabit Gider ID\n")
                    
                    expenses.forEach { e ->
                        val line = listOf(
                            e.id.toString(),
                            e.expenseDate.toString(),
                            escapeCsv(e.title),
                            e.amount.toString(),
                            escapeCsv(MoneyFormatter.format(e.amount)),
                            escapeCsv(e.category?.name ?: ""),
                            escapeCsv(e.account?.name ?: ""),
                            escapeCsv(e.paymentSourceType.name),
                            escapeCsv(e.note ?: ""),
                            e.installmentGroupId?.toString() ?: "",
                            e.subscriptionId?.toString() ?: "",
                            e.loanId?.toString() ?: "",
                            e.fixedExpenseId?.toString() ?: ""
                        ).joinToString(",")
                        writer.write(line + "\n")
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    internal fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
