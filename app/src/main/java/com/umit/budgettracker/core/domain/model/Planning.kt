package com.umit.budgettracker.core.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** Monthly CPI entered by the user (or confirmed from TCMB). Rates are basis points: %1,84 → 184. */
data class InflationRate(
    val month: YearMonth,
    val monthlyRateBp: Int,
    val annualRateBp: Int?,
    val note: String?,
    val source: String?
)

/** A purpose-bound savings pot ("Tatil", "Araba"). Its balance is the sum of its contributions. */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val targetMonth: YearMonth?,
    /** 1 = yüksek, 2 = orta, 3 = düşük. */
    val priority: Int,
    val iconName: String,
    /** When set, the goal's balance follows that asset's current value instead of contributions. */
    val linkedAssetId: Long?,
    val isActive: Boolean,
    val note: String?
)

data class GoalContribution(
    val id: Long,
    val goalId: Long,
    /** Positive for deposits, negative for withdrawals. */
    val amount: Long,
    val date: LocalDate,
    val note: String?
)

data class GoalProgress(
    val goal: SavingsGoal,
    val savedAmount: Long,
    val remainingAmount: Long,
    /** 0..100, integer. */
    val percent: Int,
    /** Months left including the current one; null without a target month. */
    val monthsLeft: Int?,
    /** What must be put aside each month to finish on time; null without a target month. */
    val requiredMonthly: Long?,
    /** Average monthly contribution over the last three months. */
    val averageMonthly: Long,
    /** Finish month at the current pace; null when the pace is zero or the goal is complete. */
    val projectedFinish: YearMonth?,
    /** Positive when the projected finish is after the target month. */
    val delayMonths: Int?,
    val isCompleted: Boolean
)

data class GoalAllocation(val goalId: Long, val goalName: String, val amount: Long)

data class EmergencyFundStatus(
    val essentialMonthlyExpense: Long,
    val fundAmount: Long,
    /** Months covered ×10 (2,5 ay → 25) so the UI can print one decimal without floating point. */
    val coveredMonthsTenths: Int?,
    val targetMonths: Int,
    val targetAmount: Long,
    val shortfall: Long
)

/** A dated one-off (or yearly) cost to prepare for: kasko, MTV, beyaz eşya. */
data class PlannedExpense(
    val id: Long,
    val title: String,
    val amount: Long,
    val dueDate: LocalDate,
    val categoryId: Long?,
    /** Already set aside towards this cost. */
    val savedAmount: Long,
    val isYearly: Boolean,
    val isCompleted: Boolean,
    val note: String?
)

data class PublicHoliday(val date: LocalDate, val name: String)

/** Learned "merchant text → category" mapping used by receipt OCR and statement import. */
data class MerchantRule(
    val id: Long,
    val pattern: String,
    val categoryId: Long,
    val hitCount: Int
)

/** A spending candidate captured from a bank notification; never counted until approved. */
data class ExpenseDraft(
    val id: Long,
    val title: String,
    val amount: Long,
    val date: LocalDate,
    val sourceLabel: String,
    val createdAt: Long
)

data class RecurringSuggestion(
    val key: String,
    val title: String,
    val typicalAmount: Long,
    val typicalDay: Int,
    val monthCount: Int,
    val categoryId: Long,
    val paymentAccountId: Long
)
