package com.umit.budgettracker.core.export

import android.content.Context
import android.net.Uri
import com.umit.budgettracker.core.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

class JsonExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val salaryRepository: SalaryRepository,
    private val incomeRepository: IncomeRepository,
    private val savingGoalRepository: SavingGoalRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val installmentRepository: InstallmentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val loanRepository: LoanRepository,
    private val budgetRepository: CategoryBudgetRepository,
    private val templateRepository: ExpenseTemplateRepository,
    private val debtRepository: DebtRepository,
    private val netWorthRepository: NetWorthRepository,
    private val attachmentRepository: ExpenseAttachmentRepository,
    private val statementPaymentRepository: CreditCardStatementPaymentRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository
) {
    private val json = Json { prettyPrint = true }

    suspend fun exportToJson(uri: Uri): ExportResult {
        return try {
            val dto = BudgetTrackerExportDto(
                schemaVersion = 7,
                exportedAt = Instant.now().toString(),
                salaryRules = salaryRepository.observeAllSalaryRules().first().map { 
                    SalaryRuleDto(it.id, it.amount, it.effectiveStartMonth.toString(), it.note) 
                },
                incomes = incomeRepository.observeAllIncomes().first().map {
                    IncomeDto(it.id, it.title, it.amount, it.incomeDate.toEpochDay(), it.type.name, it.note)
                },
                savingGoals = savingGoalRepository.observeAllSavingGoals().first().map { 
                    MonthlySavingGoalDto(it.yearMonth.toString(), it.amount, it.note) 
                },
                categories = categoryRepository.observeAllCategories().first().map { 
                    CategoryDto(it.id, it.name, it.iconName, it.colorValue, it.type.name, it.isDefault, it.isActive, it.sortOrder) 
                },
                paymentAccounts = accountRepository.observeActiveAccounts().first().map { 
                    PaymentAccountDto(it.id, it.name, it.type.name, it.statementDay, it.dueDay, it.isActive) 
                },
                expenses = expenseRepository.observeAllExpenses().first().map { 
                    ExpenseDto(
                        it.id,
                        it.title,
                        it.amount,
                        it.expenseDate.toEpochDay(),
                        it.categoryId,
                        it.paymentAccountId,
                        it.paymentSourceType.name,
                        it.note,
                        it.installmentGroupId,
                        it.subscriptionId,
                        it.loanId,
                        it.originalAmount,
                        it.originalCurrency,
                        it.exchangeRateToTry,
                        it.exchangeRateScale,
                        it.exchangeRateSource,
                        it.exchangeRateUpdatedAt
                    )
                },
                installmentGroups = installmentRepository.observeInstallmentGroups().first().map { 
                    InstallmentGroupDto(it.id, it.title, it.totalAmount, it.installmentCount, it.startDate.toEpochDay(), it.categoryId, it.paymentAccountId, it.note) 
                },
                loans = loanRepository.observeAllLoans().first().map { 
                    LoanDto(it.id, it.title, it.principalAmount, it.monthlyPaymentAmount, it.installmentCount, it.startMonth.toString(), it.paymentDay, it.categoryId, it.paymentAccountId, it.note, it.isActive) 
                },
                subscriptions = subscriptionRepository.observeAllSubscriptions().first().map { 
                    SubscriptionDto(
                        it.id,
                        it.title,
                        it.categoryId,
                        it.paymentAccountId,
                        it.billingDay,
                        it.isActive,
                        it.note,
                        it.cancelledFromMonth?.toString(),
                        it.originalCurrency,
                        it.exchangeRateToTry,
                        it.exchangeRateScale,
                        it.exchangeRateSource,
                        it.exchangeRateUpdatedAt
                    )
                },
                subscriptionPriceHistory = subscriptionRepository.observeAllPriceHistory().first().map { 
                    SubscriptionPriceHistoryDto(it.id, it.subscriptionId, it.amount, it.effectiveFromMonth.toString()) 
                },
                categoryBudgets = budgetRepository.observeAllBudgets().first().map { 
                    CategoryBudgetDto(it.id, it.categoryId, it.yearMonth.toString(), it.limitAmount, it.note) 
                },
                expenseTemplates = templateRepository.observeActiveTemplates().first().map { 
                    ExpenseTemplateDto(it.id, it.title, it.defaultAmount, it.categoryId, it.paymentAccountId, it.note, it.isActive) 
                },
                debtRecords = debtRepository.observeAllDebtRecords().first().map { 
                    DebtRecordDto(it.id, it.title, it.personName, it.amount, it.type.name, it.dueDate?.toEpochDay(), it.isPaid, it.note) 
                },
                netWorthSnapshots = netWorthRepository.observeSnapshots().first().map { 
                    NetWorthSnapshotDto(it.id, it.yearMonth.toString(), it.cashAmount, it.bankAmount, it.investmentAmount, it.creditCardDebt, it.loanDebt, it.note) 
                },
                expenseAttachments = attachmentRepository.getAllAttachments().map {
                    ExpenseAttachmentDto(it.id, it.expenseId, it.type.name, it.localPath, it.mimeType, it.originalFileName, it.createdAt)
                },
                creditCardStatementPayments = statementPaymentRepository.observeAllPayments().first().map {
                    CreditCardStatementPaymentDto(it.id, it.accountId, it.paymentMonth.toString(), it.amountAtPayment, it.isPaid, it.paidAt)
                },
                expenseAdjustments = adjustmentRepository.observeAllAdjustments().first().map {
                    ExpenseAdjustmentDto(it.id, it.expenseId, it.amount, it.type.name, it.adjustmentDate.toEpochDay(), it.note)
                }
            )

            val jsonString = json.encodeToString(dto)
            context.contentResolver.openOutputStream(uri)?.use { 
                it.write(jsonString.toByteArray()) 
            }
            ExportResult.Success
        } catch (e: Exception) {
            ExportResult.Error("JSON dışa aktarma başarısız oldu.", e)
        }
    }
}

sealed class ExportResult {
    data object Success : ExportResult()
    data class Error(val message: String, val throwable: Throwable?) : ExportResult()
}
