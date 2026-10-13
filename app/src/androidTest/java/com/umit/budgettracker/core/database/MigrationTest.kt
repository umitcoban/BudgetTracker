package com.umit.budgettracker.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.umit.budgettracker.core.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Replays the exported schema history on a real SQLite database. Each step seeds rows at the
 * older version, migrates, and lets Room validate the result against the committed schema JSON —
 * the same check a user's phone performs on first launch after an update.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate15To16_keepsSalaryRulesAndAddsNullablePayDay() {
        helper.createDatabase(dbName, 15).apply {
            execSQL(
                "INSERT INTO salary_rules (id, amount, effectiveStartMonth, note, createdAt, updatedAt) " +
                    "VALUES (1, 9000000, '2026-01', 'eski', 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 16, true, *DatabaseModule.migrations)

        db.query("SELECT amount, effectiveStartMonth, note, payDay FROM salary_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9_000_000L, cursor.getLong(0))
            assertEquals("2026-01", cursor.getString(1))
            assertEquals("eski", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun migrateFromOldestExportedSchema_toLatest_preservesRows() {
        helper.createDatabase(dbName, 10).apply {
            execSQL("INSERT INTO categories (id, name, iconName, colorValue, type, isDefault, isActive, sortOrder) VALUES (1, 'Market', 'cart', 1, 'EXPENSE', 1, 1, 0)")
            execSQL("INSERT INTO payment_accounts (id, name, type, statementDay, dueDay, isActive, createdAt, updatedAt) VALUES (1, 'Kart', 'CREDIT_CARD', 11, 20, 1, 1, 1)")
            execSQL(
                "INSERT INTO expenses (id, title, amount, expenseDate, categoryId, paymentAccountId, paymentSourceType, note, createdAt, updatedAt) " +
                    "VALUES (1, 'Eski harcama', 12345, 20600, 1, 1, 'CREDIT_CARD', NULL, 1, 1)"
            )
            execSQL("INSERT INTO salary_rules (id, amount, effectiveStartMonth, note, createdAt, updatedAt) VALUES (1, 5000000, '2025-06', NULL, 1, 1)")
            execSQL(
                "INSERT INTO loans (id, title, principalAmount, monthlyPaymentAmount, installmentCount, startMonth, paymentDay, categoryId, paymentAccountId, note, isActive, createdAt, updatedAt) " +
                    "VALUES (1, 'Konut', 1200000, 100000, 12, '2025-06', 15, NULL, NULL, NULL, 1, 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, LATEST_VERSION, true, *DatabaseModule.migrations)

        db.query("SELECT title, amount FROM expenses").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Eski harcama", cursor.getString(0))
            assertEquals(12_345L, cursor.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM loans WHERE closedAt IS NULL").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM credit_card_statement_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        db.query("SELECT payDay FROM salary_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
    }

    @Test
    fun migrate16To17_keepsExistingDataAddsPlanningTablesAndSeedsInflation() {
        helper.createDatabase(dbName, 16).apply {
            execSQL("INSERT INTO categories (id, name, iconName, colorValue, type, isDefault, isActive, sortOrder) VALUES (1, 'Market', 'cart', 1, 'EXPENSE', 1, 1, 0)")
            execSQL("INSERT INTO payment_accounts (id, name, type, statementDay, dueDay, isActive, createdAt, updatedAt) VALUES (1, 'Banka', 'BANK_ACCOUNT', NULL, NULL, 1, 1, 1)")
            execSQL(
                "INSERT INTO expenses (id, title, amount, expenseDate, categoryId, paymentAccountId, paymentSourceType, note, createdAt, updatedAt) " +
                    "VALUES (7, 'Mevcut harcama', 45075, 20738, 1, 1, 'BANK_ACCOUNT', NULL, 1, 1)"
            )
            execSQL("INSERT INTO salary_rules (id, amount, effectiveStartMonth, note, payDay, createdAt, updatedAt) VALUES (1, 9000000, '2026-01', NULL, 28, 1, 1)")
            execSQL("INSERT INTO net_worth_snapshots (id, yearMonth, cashAmount, bankAmount, investmentAmount, creditCardDebt, loanDebt, note, createdAt, updatedAt) VALUES (1, '2026-09', 100, 200, 300, 0, 0, NULL, 1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 17, true, *DatabaseModule.migrations)

        db.query("SELECT title, amount FROM expenses WHERE id = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mevcut harcama", cursor.getString(0))
            assertEquals(45_075L, cursor.getLong(1))
        }
        db.query("SELECT payDay FROM salary_rules").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(28, cursor.getInt(0))
        }
        db.query("SELECT investmentAmount FROM net_worth_snapshots").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(300L, cursor.getLong(0))
        }
        db.query("SELECT COUNT(*), MIN(month), MAX(month) FROM inflation_rates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(12L, cursor.getLong(0))
            assertEquals("2025-10", cursor.getString(1))
            assertEquals("2026-09", cursor.getString(2))
        }
        db.query("SELECT monthlyRateBp, annualRateBp FROM inflation_rates WHERE month = '2026-09'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(184, cursor.getInt(0))
            assertEquals(2973, cursor.getInt(1))
        }
        listOf(
            "assets", "market_prices", "price_alerts", "savings_goals", "goal_contributions",
            "planned_expenses", "public_holidays", "merchant_rules", "dismissed_suggestions", "expense_drafts"
        ).forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("$table should start empty", 0L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val LATEST_VERSION = 17
    }
}
