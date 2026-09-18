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

        val db = helper.runMigrationsAndValidate(dbName, 16, true, *DatabaseModule.migrations)

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
}
