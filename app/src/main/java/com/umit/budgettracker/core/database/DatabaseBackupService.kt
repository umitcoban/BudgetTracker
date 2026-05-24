package com.umit.budgettracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

class DatabaseBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
) {
    suspend fun backup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Use checkpoint to ensure main DB file is up to date
            db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use {
                it.moveToFirst()
            }
            
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Veritabanı dosyası bulunamadı."))
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Çıkış dosyası açılamadı."))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restore(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            val tempRestoreFile = File(context.cacheDir, "restore_candidate.db")
            if (tempRestoreFile.exists()) tempRestoreFile.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRestoreFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Dosya okunamadı."))

            if (!isReadableSqliteDatabase(tempRestoreFile)) {
                tempRestoreFile.delete()
                return@withContext Result.failure(Exception("Yedek dosyası geçersiz."))
            }
            
            // Backup current DB before overwrite
            if (dbFile.exists()) {
                val backupFile = File(dbFile.absolutePath + ".bak")
                dbFile.copyTo(backupFile, overwrite = true)
            }

            tempRestoreFile.copyTo(dbFile, overwrite = true)
            tempRestoreFile.delete()
            
            // Delete WAL and SHM files to avoid consistency issues
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isReadableSqliteDatabase(file: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { sqliteDatabase ->
                sqliteDatabase.rawQuery("SELECT name FROM sqlite_master LIMIT 1", null).use { cursor ->
                    cursor.moveToFirst()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
