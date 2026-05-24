package com.umit.budgettracker.core.export

import kotlinx.serialization.Serializable

@Serializable
data class BudgetTrackerExportDto(
    val appName: String = "BudgetTracker",
    val schemaVersion: Int,
    val exportedAt: String,
    val salaryRules: List<SalaryRuleDto>,
    val savingGoals: List<MonthlySavingGoalDto>,
    val categories: List<CategoryDto>,
    val paymentAccounts: List<PaymentAccountDto>,
    val expenses: List<ExpenseDto>,
    val installmentGroups: List<InstallmentGroupDto>,
    val loans: List<LoanDto>,
    val subscriptions: List<SubscriptionDto>,
    val subscriptionPriceHistory: List<SubscriptionPriceHistoryDto>,
    val categoryBudgets: List<CategoryBudgetDto>,
    val expenseTemplates: List<ExpenseTemplateDto>,
    val debtRecords: List<DebtRecordDto>,
    val netWorthSnapshots: List<NetWorthSnapshotDto>,
    val expenseAttachments: List<ExpenseAttachmentDto> = emptyList(),
    val creditCardStatementPayments: List<CreditCardStatementPaymentDto> = emptyList(),
    val expenseAdjustments: List<ExpenseAdjustmentDto> = emptyList()
)

@Serializable
data class SalaryRuleDto(val id: Long, val amount: Long, val effectiveStartMonth: String, val note: String?)

@Serializable
data class MonthlySavingGoalDto(val yearMonth: String, val amount: Long, val note: String?)

@Serializable
data class CategoryDto(val id: Long, val name: String, val iconName: String, val colorValue: Int, val type: String, val isDefault: Boolean, val isActive: Boolean, val sortOrder: Int)

@Serializable
data class PaymentAccountDto(val id: Long, val name: String, val type: String, val statementDay: Int?, val dueDay: Int?, val isActive: Boolean)

@Serializable
data class ExpenseDto(
    val id: Long,
    val title: String,
    val amount: Long,
    val expenseDate: Long,
    val categoryId: Long,
    val paymentAccountId: Long,
    val paymentSourceType: String,
    val note: String?,
    val installmentGroupId: Long?,
    val subscriptionId: Long? = null,
    val loanId: Long? = null,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null
)

@Serializable
data class InstallmentGroupDto(val id: Long, val title: String, val totalAmount: Long, val installmentCount: Int, val startDate: Long, val categoryId: Long, val paymentAccountId: Long, val note: String?)

@Serializable
data class LoanDto(val id: Long, val title: String, val principalAmount: Long, val monthlyPaymentAmount: Long, val installmentCount: Int, val startMonth: String, val paymentDay: Int, val categoryId: Long?, val paymentAccountId: Long?, val note: String?, val isActive: Boolean)

@Serializable
data class SubscriptionDto(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val paymentAccountId: Long,
    val billingDay: Int,
    val isActive: Boolean,
    val note: String?,
    val cancelledFromMonth: String? = null,
    val originalCurrency: String? = null,
    val exchangeRateToTry: Long? = null,
    val exchangeRateScale: Int? = null,
    val exchangeRateSource: String? = null,
    val exchangeRateUpdatedAt: Long? = null
)

@Serializable
data class SubscriptionPriceHistoryDto(val id: Long, val subscriptionId: Long, val amount: Long, val effectiveFromMonth: String)

@Serializable
data class CategoryBudgetDto(val id: Long, val categoryId: Long, val yearMonth: String, val limitAmount: Long, val note: String?)

@Serializable
data class ExpenseTemplateDto(val id: Long, val title: String, val defaultAmount: Long?, val categoryId: Long, val paymentAccountId: Long?, val note: String?, val isActive: Boolean)

@Serializable
data class DebtRecordDto(val id: Long, val title: String, val personName: String?, val amount: Long, val type: String, val dueDate: Long?, val isPaid: Boolean, val note: String?)

@Serializable
data class NetWorthSnapshotDto(val id: Long, val yearMonth: String, val cashAmount: Long, val bankAmount: Long, val investmentAmount: Long, val creditCardDebt: Long, val loanDebt: Long, val note: String?)

@Serializable
data class ExpenseAttachmentDto(val id: Long, val expenseId: Long, val type: String, val localPath: String, val mimeType: String?, val originalFileName: String?, val createdAt: Long)

@Serializable
data class CreditCardStatementPaymentDto(
    val id: Long,
    val accountId: Long,
    val paymentMonth: String,
    val amountAtPayment: Long,
    val isPaid: Boolean,
    val paidAt: Long?
)

@Serializable
data class ExpenseAdjustmentDto(
    val id: Long,
    val expenseId: Long,
    val amount: Long,
    val type: String,
    val adjustmentDate: Long,
    val note: String?
)
