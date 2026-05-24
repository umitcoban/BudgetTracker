package com.umit.budgettracker.core.dataimport

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.umit.budgettracker.core.database.AppDatabase
import com.umit.budgettracker.core.export.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class JsonImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromJson(uri: Uri): ImportResult {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return ImportResult.Error("Dosya okunamadı.")

            val dto = json.decodeFromString<BudgetTrackerExportDto>(content)
            
            if (dto.appName != "BudgetTracker") {
                return ImportResult.Error("Bu dosya BudgetTracker uygulamasına ait değil.")
            }
            
            if (dto.schemaVersion > 6) {
                return ImportResult.Error("Bu yedek dosyası uygulamanın desteklemediği bir sürüme ait.")
            }

            db.withTransaction {
                db.clearAllTables()

                db.categoryDao().insertAll(dto.categories.map { it.toEntity() })
                db.paymentAccountDao().insertAll(dto.paymentAccounts.map { it.toEntity() })
                db.installmentGroupDao().insertAll(dto.installmentGroups.map { it.toEntity() })
                db.salaryRuleDao().insertAll(dto.salaryRules.map { it.toEntity() })
                db.monthlySavingGoalDao().insertAll(dto.savingGoals.map { it.toEntity() })
                db.expenseDao().insertAll(dto.expenses.map { it.toEntity() })
                db.loanDao().insertAll(dto.loans.map { it.toEntity() })
                db.subscriptionDao().insertAll(dto.subscriptions.map { it.toEntity() })
                
                dto.subscriptionPriceHistory.forEach { db.subscriptionDao().insertPriceHistory(it.toEntity()) }
                db.categoryBudgetDao().insertAll(dto.categoryBudgets.map { it.toEntity() })
                db.expenseTemplateDao().insertAll(dto.expenseTemplates.map { it.toEntity() })
                db.debtRecordDao().insertAll(dto.debtRecords.map { it.toEntity() })
                db.netWorthSnapshotDao().insertAll(dto.netWorthSnapshots.map { it.toEntity() })
                
                if (dto.schemaVersion >= 2) {
                    db.expenseAttachmentDao().insertAll(dto.expenseAttachments.map { it.toEntity() })
                }
                if (dto.schemaVersion >= 3) {
                    db.creditCardStatementPaymentDao().insertAll(dto.creditCardStatementPayments.map { it.toEntity() })
                }
                if (dto.schemaVersion >= 4) {
                    db.expenseAdjustmentDao().insertAll(dto.expenseAdjustments.map { it.toEntity() })
                }
            }

            ImportResult.Success(
                summary = "İçe aktarma tamamlandı.\n" +
                        "${dto.expenses.size} harcama, ${dto.categories.size} kategori yüklendi."
            )
        } catch (e: Exception) {
            ImportResult.Error("Yedek dosyası geçersiz veya okunamadı.")
        }
    }
}

sealed class ImportResult {
    data class Success(val summary: String) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

private fun CategoryDto.toEntity() = com.umit.budgettracker.core.database.entity.CategoryEntity(id, name, iconName, colorValue, type, isDefault, isActive, sortOrder)
private fun PaymentAccountDto.toEntity() = com.umit.budgettracker.core.database.entity.PaymentAccountEntity(id, name, type, statementDay, dueDay, isActive)
private fun SalaryRuleDto.toEntity() = com.umit.budgettracker.core.database.entity.SalaryRuleEntity(id, amount, effectiveStartMonth, note)
private fun MonthlySavingGoalDto.toEntity() = com.umit.budgettracker.core.database.entity.MonthlySavingGoalEntity(yearMonth, amount, note)
private fun ExpenseDto.toEntity() = com.umit.budgettracker.core.database.entity.ExpenseEntity(
    id = id,
    title = title,
    amount = amount,
    expenseDate = expenseDate,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    paymentSourceType = paymentSourceType,
    note = note,
    installmentGroupId = installmentGroupId,
    subscriptionId = subscriptionId,
    loanId = loanId,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt
)
private fun InstallmentGroupDto.toEntity() = com.umit.budgettracker.core.database.entity.InstallmentGroupEntity(id, title, totalAmount, installmentCount, startDate, categoryId, paymentAccountId, note)
private fun LoanDto.toEntity() = com.umit.budgettracker.core.database.entity.LoanEntity(id, title, principalAmount, monthlyPaymentAmount, installmentCount, startMonth, paymentDay, categoryId, paymentAccountId, note, isActive)
private fun SubscriptionDto.toEntity() = com.umit.budgettracker.core.database.entity.SubscriptionEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    paymentAccountId = paymentAccountId,
    billingDay = billingDay,
    isActive = isActive,
    note = note,
    cancelledFromMonth = cancelledFromMonth,
    originalCurrency = originalCurrency,
    exchangeRateToTry = exchangeRateToTry,
    exchangeRateScale = exchangeRateScale,
    exchangeRateSource = exchangeRateSource,
    exchangeRateUpdatedAt = exchangeRateUpdatedAt
)
private fun SubscriptionPriceHistoryDto.toEntity() = com.umit.budgettracker.core.database.entity.SubscriptionPriceHistoryEntity(id, subscriptionId, amount, effectiveFromMonth)
private fun CategoryBudgetDto.toEntity() = com.umit.budgettracker.core.database.entity.CategoryBudgetEntity(id, categoryId, yearMonth, limitAmount, note)
private fun ExpenseTemplateDto.toEntity() = com.umit.budgettracker.core.database.entity.ExpenseTemplateEntity(id, title, defaultAmount, categoryId, paymentAccountId, note, isActive)
private fun DebtRecordDto.toEntity() = com.umit.budgettracker.core.database.entity.DebtRecordEntity(id, title, personName, amount, type, dueDate, isPaid, note)
private fun NetWorthSnapshotDto.toEntity() = com.umit.budgettracker.core.database.entity.NetWorthSnapshotEntity(id, yearMonth, cashAmount, bankAmount, investmentAmount, creditCardDebt, loanDebt, note)
private fun ExpenseAttachmentDto.toEntity() = com.umit.budgettracker.core.database.entity.ExpenseAttachmentEntity(id, expenseId, type, localPath, mimeType, originalFileName, createdAt)
private fun CreditCardStatementPaymentDto.toEntity() = com.umit.budgettracker.core.database.entity.CreditCardStatementPaymentEntity(id, accountId, paymentMonth, amountAtPayment, isPaid, paidAt)
private fun ExpenseAdjustmentDto.toEntity() = com.umit.budgettracker.core.database.entity.ExpenseAdjustmentEntity(id, expenseId, amount, type, adjustmentDate, note)
