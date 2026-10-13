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
    private val fixedExpenseRepository: FixedExpenseRepository,
    private val savingGoalRepository: SavingGoalRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: PaymentAccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val installmentRepository: InstallmentRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val loanRepository: LoanRepository,
    private val loanPaymentRepository: LoanPaymentRepository,
    private val statementRuleRepository: CreditCardStatementRuleRepository,
    private val budgetRepository: CategoryBudgetRepository,
    private val templateRepository: ExpenseTemplateRepository,
    private val debtRepository: DebtRepository,
    private val netWorthRepository: NetWorthRepository,
    private val attachmentRepository: ExpenseAttachmentRepository,
    private val statementPaymentRepository: CreditCardStatementPaymentRepository,
    private val adjustmentRepository: ExpenseAdjustmentRepository,
    private val inflationRepository: InflationRepository,
    private val assetRepository: AssetRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val plannedExpenseRepository: PlannedExpenseRepository,
    private val assistantRepository: AssistantRepository
) {
    // encodeDefaults keeps appName and empty sections in the file so a backup is self-describing.
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportToJson(uri: Uri): ExportResult {
        return try {
            val dto = BudgetTrackerExportDto(
                schemaVersion = SCHEMA_VERSION,
                exportedAt = Instant.now().toString(),
                salaryRules = salaryRepository.observeAllSalaryRules().first().map { 
                    SalaryRuleDto(it.id, it.amount, it.effectiveStartMonth.toString(), it.note, it.payDay)
                },
                incomes = incomeRepository.observeAllIncomes().first().map {
                    IncomeDto(it.id, it.title, it.amount, it.incomeDate.toEpochDay(), it.type.name, it.note)
                },
                fixedExpenses = fixedExpenseRepository.observeAllFixedExpenses().first().map {
                    FixedExpenseDto(
                        it.id,
                        it.title,
                        it.amount,
                        it.dayOfMonth,
                        it.startMonth.toString(),
                        it.endMonth?.toString(),
                        it.categoryId,
                        it.paymentAccountId,
                        it.note,
                        it.isActive
                    )
                },
                savingGoals = savingGoalRepository.observeAllSavingGoals().first().map { 
                    MonthlySavingGoalDto(it.yearMonth.toString(), it.amount, it.note) 
                },
                categories = categoryRepository.observeAllCategories().first().map { 
                    CategoryDto(it.id, it.name, it.iconName, it.colorValue, it.type.name, it.isDefault, it.isActive, it.sortOrder) 
                },
                paymentAccounts = accountRepository.observeAllAccounts().first().map {
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
                        it.fixedExpenseId,
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
                    LoanDto(it.id, it.title, it.principalAmount, it.monthlyPaymentAmount, it.installmentCount, it.startMonth.toString(), it.paymentDay, it.categoryId, it.paymentAccountId, it.note, it.isActive, it.closedAt?.toEpochDay())
                },
                loanPayments = loanPaymentRepository.observeAllPayments().first().map {
                    LoanPaymentDto(it.id, it.loanId, it.paymentMonth.toString(), it.amount, it.paidAt.toEpochDay())
                },
                creditCardStatementRules = statementRuleRepository.observeAllRules().first().map {
                    CreditCardStatementRuleDto(it.id, it.accountId, it.effectiveFromMonth.toString(), it.statementDay, it.dueDay)
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
                    SubscriptionPriceHistoryDto(
                        it.id,
                        it.subscriptionId,
                        it.amount,
                        it.effectiveFromMonth.toString(),
                        it.originalCurrency,
                        it.exchangeRateToTry,
                        it.exchangeRateScale,
                        it.exchangeRateSource,
                        it.exchangeRateUpdatedAt
                    )
                },
                categoryBudgets = budgetRepository.observeAllBudgets().first().map { 
                    CategoryBudgetDto(it.id, it.categoryId, it.yearMonth.toString(), it.limitAmount, it.note) 
                },
                expenseTemplates = templateRepository.observeAllTemplates().first().map {
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
                },
                inflationRates = inflationRepository.getAll().map {
                    InflationRateDto(
                        month = it.month.toString(),
                        monthlyRateBp = it.monthlyRateBp,
                        annualRateBp = it.annualRateBp,
                        note = it.note,
                        source = it.source
                    )
                },
                assets = assetRepository.getAssets().map {
                    AssetDto(
                        id = it.id,
                        name = it.name,
                        type = it.type.name,
                        priceCode = it.priceCode,
                        quantity = it.quantity,
                        manualValue = it.manualValue,
                        costAmount = it.costAmount,
                        allocationTag = it.allocationTag,
                        interestRateBp = it.interestRateBp,
                        startDate = it.startDate?.toEpochDay(),
                        maturityDate = it.maturityDate?.toEpochDay(),
                        isEmergencyFund = it.isEmergencyFund,
                        isActive = it.isActive,
                        note = it.note
                    )
                },
                marketPrices = assetRepository.getPrices().map {
                    MarketPriceDto(
                        code = it.code,
                        name = it.name,
                        price = it.price,
                        previousPrice = it.previousPrice,
                        source = it.source,
                        updatedAt = it.updatedAt
                    )
                },
                priceAlerts = assetRepository.getAlerts().map {
                    PriceAlertDto(
                        id = it.id,
                        priceCode = it.priceCode,
                        condition = it.condition.name,
                        threshold = it.threshold,
                        isActive = it.isActive,
                        lastTriggeredAt = it.lastTriggeredAt,
                        note = it.note
                    )
                },
                savingsGoals = savingsGoalRepository.observeGoals().first().map {
                    SavingsGoalDto(
                        id = it.id,
                        name = it.name,
                        targetAmount = it.targetAmount,
                        targetMonth = it.targetMonth?.toString(),
                        priority = it.priority,
                        iconName = it.iconName,
                        linkedAssetId = it.linkedAssetId,
                        isActive = it.isActive,
                        note = it.note
                    )
                },
                goalContributions = savingsGoalRepository.observeContributions().first().map {
                    GoalContributionDto(
                        id = it.id,
                        goalId = it.goalId,
                        amount = it.amount,
                        date = it.date.toEpochDay(),
                        note = it.note
                    )
                },
                plannedExpenses = plannedExpenseRepository.observeAll().first().map {
                    PlannedExpenseDto(
                        id = it.id,
                        title = it.title,
                        amount = it.amount,
                        dueDate = it.dueDate.toEpochDay(),
                        categoryId = it.categoryId,
                        savedAmount = it.savedAmount,
                        isYearly = it.isYearly,
                        isCompleted = it.isCompleted,
                        note = it.note
                    )
                },
                merchantRules = assistantRepository.getMerchantRules().map {
                    MerchantRuleDto(id = it.id, pattern = it.pattern, categoryId = it.categoryId, hitCount = it.hitCount)
                },
                dismissedSuggestions = assistantRepository.observeDismissedSuggestionKeys().first().sorted()
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

/** Keep in step with the `dto.schemaVersion > N` guard in JsonImportService. */
const val SCHEMA_VERSION = 15

sealed class ExportResult {
    data object Success : ExportResult()
    data class Error(val message: String, val throwable: Throwable?) : ExportResult()
}
