package com.umit.budgettracker.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.umit.budgettracker.core.database.AppDatabase
import com.umit.budgettracker.core.database.dao.*
import com.umit.budgettracker.core.database.entity.CategoryEntity
import com.umit.budgettracker.core.database.entity.PaymentAccountEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        provider: Provider<CategoryDao>,
        accountProvider: Provider<PaymentAccountDao>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    seedCategories(provider.get())
                    seedAccounts(accountProvider.get())
                }
            }
        }).build()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNullableColumnIfMissing(db, "subscriptions", "cancelledFromMonth", "TEXT")
            addNullableColumnIfMissing(db, "expenses", "subscriptionId", "INTEGER")
            addNullableColumnIfMissing(db, "expenses", "loanId", "INTEGER")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expense_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `expenseId` INTEGER NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `adjustmentDate` INTEGER NOT NULL,
                    `note` TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expense_adjustments_expenseId` ON `expense_adjustments` (`expenseId`)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNullableColumnIfMissing(db, "expenses", "originalAmount", "INTEGER")
            addNullableColumnIfMissing(db, "expenses", "originalCurrency", "TEXT")
            addNullableColumnIfMissing(db, "expenses", "exchangeRateToTry", "INTEGER")
            addNullableColumnIfMissing(db, "expenses", "exchangeRateScale", "INTEGER")
            addNullableColumnIfMissing(db, "expenses", "exchangeRateSource", "TEXT")
            addNullableColumnIfMissing(db, "expenses", "exchangeRateUpdatedAt", "INTEGER")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNullableColumnIfMissing(db, "subscriptions", "originalCurrency", "TEXT")
            addNullableColumnIfMissing(db, "subscriptions", "exchangeRateToTry", "INTEGER")
            addNullableColumnIfMissing(db, "subscriptions", "exchangeRateScale", "INTEGER")
            addNullableColumnIfMissing(db, "subscriptions", "exchangeRateSource", "TEXT")
            addNullableColumnIfMissing(db, "subscriptions", "exchangeRateUpdatedAt", "INTEGER")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `incomes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `incomeDate` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `note` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_incomes_incomeDate` ON `incomes` (`incomeDate`)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fixed_expenses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `dayOfMonth` INTEGER NOT NULL,
                    `startMonth` TEXT NOT NULL,
                    `endMonth` TEXT,
                    `categoryId` INTEGER,
                    `paymentAccountId` INTEGER,
                    `note` TEXT,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixed_expenses_startMonth` ON `fixed_expenses` (`startMonth`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixed_expenses_endMonth` ON `fixed_expenses` (`endMonth`)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNullableColumnIfMissing(db, "expenses", "fixedExpenseId", "INTEGER")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM `salary_rules`
                WHERE `id` NOT IN (
                    SELECT MAX(`id`) FROM `salary_rules` GROUP BY `effectiveStartMonth`
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_salary_rules_effectiveStartMonth` ON `salary_rules` (`effectiveStartMonth`)"
            )
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNullableColumnIfMissing(db, "subscription_price_history", "originalCurrency", "TEXT")
            addNullableColumnIfMissing(db, "subscription_price_history", "exchangeRateToTry", "INTEGER")
            addNullableColumnIfMissing(db, "subscription_price_history", "exchangeRateScale", "INTEGER")
            addNullableColumnIfMissing(db, "subscription_price_history", "exchangeRateSource", "TEXT")
            addNullableColumnIfMissing(db, "subscription_price_history", "exchangeRateUpdatedAt", "INTEGER")
            db.execSQL(
                """
                UPDATE subscription_price_history
                SET originalCurrency = COALESCE(
                    (SELECT subscriptions.originalCurrency FROM subscriptions WHERE subscriptions.id = subscription_price_history.subscriptionId),
                    'TRY'
                ),
                exchangeRateToTry = (SELECT subscriptions.exchangeRateToTry FROM subscriptions WHERE subscriptions.id = subscription_price_history.subscriptionId),
                exchangeRateScale = (SELECT subscriptions.exchangeRateScale FROM subscriptions WHERE subscriptions.id = subscription_price_history.subscriptionId),
                exchangeRateSource = (SELECT subscriptions.exchangeRateSource FROM subscriptions WHERE subscriptions.id = subscription_price_history.subscriptionId),
                exchangeRateUpdatedAt = (SELECT subscriptions.exchangeRateUpdatedAt FROM subscriptions WHERE subscriptions.id = subscription_price_history.subscriptionId)
                WHERE originalCurrency IS NULL
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `credit_card_statement_payments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `accountId` INTEGER NOT NULL,
                    `paymentMonth` TEXT NOT NULL,
                    `amountAtPayment` INTEGER NOT NULL,
                    `isPaid` INTEGER NOT NULL,
                    `paidAt` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_card_statement_payments_accountId_paymentMonth`
                ON `credit_card_statement_payments` (`accountId`, `paymentMonth`)
                """.trimIndent()
            )
        }
    }

    private fun addNullableColumnIfMissing(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String
    ) {
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return
            }
        }
        db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnType")
    }

    private suspend fun seedCategories(dao: CategoryDao) {
        val categories = listOf(
            CategoryEntity(name = "Market", iconName = "shopping_cart", colorValue = 0xFF4CAF50.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Fatura", iconName = "receipt", colorValue = 0xFF2196F3.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Kira", iconName = "home", colorValue = 0xFF795548.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Ulaşım", iconName = "directions_bus", colorValue = 0xFFFF9800.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Yakıt", iconName = "local_gas_station", colorValue = 0xFFF44336.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Yemek", iconName = "restaurant", colorValue = 0xFFE91E63.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Kahve", iconName = "local_cafe", colorValue = 0xFF6D4C41.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Teknoloji", iconName = "computer", colorValue = 0xFF607D8B.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Sağlık", iconName = "medical_services", colorValue = 0xFFFF5252.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Eğitim", iconName = "school", colorValue = 0xFF3F51B5.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Eğlence", iconName = "movie", colorValue = 0xFF9C27B0.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Giyim", iconName = "checkroom", colorValue = 0xFF00BCD4.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Ev", iconName = "home_work", colorValue = 0xFF8BC34A.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Abonelik", iconName = "subscriptions", colorValue = 0xFFFFC107.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Kredi", iconName = "account_balance", colorValue = 0xFF455A64.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Taksit", iconName = "payments", colorValue = 0xFFCDDC39.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Tatil", iconName = "beach_access", colorValue = 0xFF03A9F4.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Hediye", iconName = "card_giftcard", colorValue = 0xFFFF4081.toInt(), type = "EXPENSE", isDefault = true),
            CategoryEntity(name = "Diğer", iconName = "more_horiz", colorValue = 0xFF9E9E9E.toInt(), type = "EXPENSE", isDefault = true)
        )
        dao.insertAll(categories)
    }

    private suspend fun seedAccounts(dao: PaymentAccountDao) {
        val accounts = listOf(
            PaymentAccountEntity(name = "Nakit", type = "CASH"),
            PaymentAccountEntity(name = "Banka Hesabı", type = "BANK_ACCOUNT")
        )
        dao.insertAll(accounts)
    }

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun providePaymentAccountDao(db: AppDatabase): PaymentAccountDao = db.paymentAccountDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideSalaryRuleDao(db: AppDatabase): SalaryRuleDao = db.salaryRuleDao()

    @Provides
    fun provideMonthlySavingGoalDao(db: AppDatabase): MonthlySavingGoalDao = db.monthlySavingGoalDao()

    @Provides
    fun provideInstallmentGroupDao(db: AppDatabase): InstallmentGroupDao = db.installmentGroupDao()

    @Provides
    fun provideLoanDao(db: AppDatabase): LoanDao = db.loanDao()

    @Provides
    fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideCategoryBudgetDao(db: AppDatabase): CategoryBudgetDao = db.categoryBudgetDao()

    @Provides
    fun provideExpenseTemplateDao(db: AppDatabase): ExpenseTemplateDao = db.expenseTemplateDao()

    @Provides
    fun provideDebtRecordDao(db: AppDatabase): DebtRecordDao = db.debtRecordDao()

    @Provides
    fun provideNetWorthSnapshotDao(db: AppDatabase): NetWorthSnapshotDao = db.netWorthSnapshotDao()

    @Provides
    fun provideExpenseAttachmentDao(db: AppDatabase): ExpenseAttachmentDao = db.expenseAttachmentDao()

    @Provides
    fun provideCreditCardStatementPaymentDao(db: AppDatabase): CreditCardStatementPaymentDao =
        db.creditCardStatementPaymentDao()

    @Provides
    fun provideExpenseAdjustmentDao(db: AppDatabase): ExpenseAdjustmentDao = db.expenseAdjustmentDao()

    @Provides
    fun provideIncomeDao(db: AppDatabase): IncomeDao = db.incomeDao()

    @Provides
    fun provideFixedExpenseDao(db: AppDatabase): FixedExpenseDao = db.fixedExpenseDao()
}
