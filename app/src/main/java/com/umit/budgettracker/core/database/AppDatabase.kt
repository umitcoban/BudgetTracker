package com.umit.budgettracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.umit.budgettracker.core.database.converter.Converters
import com.umit.budgettracker.core.database.dao.*
import com.umit.budgettracker.core.database.entity.*

@Database(
    entities = [
        SalaryRuleEntity::class,
        MonthlySavingGoalEntity::class,
        CategoryEntity::class,
        PaymentAccountEntity::class,
        ExpenseEntity::class,
        InstallmentGroupEntity::class,
        LoanEntity::class,
        SubscriptionEntity::class,
        SubscriptionPriceHistoryEntity::class,
        CategoryBudgetEntity::class,
        ExpenseTemplateEntity::class,
        DebtRecordEntity::class,
        NetWorthSnapshotEntity::class,
        ExpenseAttachmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun salaryRuleDao(): SalaryRuleDao
    abstract fun monthlySavingGoalDao(): MonthlySavingGoalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun installmentGroupDao(): InstallmentGroupDao
    abstract fun loanDao(): LoanDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun expenseTemplateDao(): ExpenseTemplateDao
    abstract fun debtRecordDao(): DebtRecordDao
    abstract fun netWorthSnapshotDao(): NetWorthSnapshotDao
    abstract fun expenseAttachmentDao(): ExpenseAttachmentDao

    companion object {
        const val DATABASE_NAME = "budget_tracker_db"
    }
}
