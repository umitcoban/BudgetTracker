package com.umit.budgettracker.core.export

import android.content.Context
import android.net.Uri
import com.umit.budgettracker.core.dataimport.ImportResult
import com.umit.budgettracker.core.dataimport.JsonImportService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class FullBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExportService: JsonExportService,
    private val jsonImportService: JsonImportService
) {
    suspend fun exportFullBackup(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            // 1. Create a temp directory
            val tempDir = File(context.cacheDir, "full_backup_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            // 2. Generate data.json in temp dir
            val jsonFile = File(tempDir, "data.json")
            val jsonUri = Uri.fromFile(jsonFile)
            val jsonResult = jsonExportService.exportToJson(jsonUri)
            if (jsonResult is ExportResult.Error) return@withContext jsonResult

            // 3. Create the ZIP file using SAF
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // Add data.json
                    addToZip(zipOut, jsonFile, "data.json")

                    // Add attachments folder
                    val attachmentsDir = File(context.filesDir, "attachments")
                    if (attachmentsDir.exists()) {
                        addFolderToZip(zipOut, attachmentsDir, "attachments")
                    }
                }
            }
            
            ExportResult.Success
        } catch (e: Exception) {
            ExportResult.Error("Tam yedek oluşturulamadı.", e)
        }
    }

    suspend fun importFullBackup(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "full_backup_import_temp")
        try {
            if (tempDir.exists()) tempDir.deleteRecursively()
            if (!tempDir.mkdirs()) return@withContext ImportResult.Error("Bu işlem şu anda tamamlanamadı.")

            extractZipSafely(uri, tempDir)

            val dataFile = File(tempDir, "data.json")
            if (!dataFile.exists() || !dataFile.isFile) {
                return@withContext ImportResult.Error("Yedek dosyası geçersiz. data.json bulunamadı.")
            }

            when (val importResult = jsonImportService.importFromJson(Uri.fromFile(dataFile))) {
                is ImportResult.Error -> return@withContext ImportResult.Error(importResult.message)
                is ImportResult.Success -> {
                    val attachmentResult = replaceAttachments(File(tempDir, "attachments"))
                    if (!attachmentResult) {
                        return@withContext ImportResult.Error("Veriler yüklendi ancak ekli fotoğraflar geri yüklenemedi.")
                    }
                    ImportResult.Success(
                        "Tam yedek başarıyla yüklendi.\nDeğişikliklerin tam uygulanması için uygulamayı yeniden başlatmanız önerilir."
                    )
                }
            }
        } catch (e: InvalidBackupZipException) {
            ImportResult.Error(e.message ?: "Yedek dosyası geçersiz.")
        } catch (e: Exception) {
            ImportResult.Error("Tam yedek yüklenemedi.")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractZipSafely(uri: Uri, destinationDir: File) {
        var hasEntry = false
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    hasEntry = true
                    val target = safeZipTarget(destinationDir, entry.name)
                    if (entry.isDirectory) {
                        if (!target.exists() && !target.mkdirs()) {
                            throw IOException("Dizin oluşturulamadı.")
                        }
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output -> zipInput.copyTo(output) }
                    }
                    zipInput.closeEntry()
                }
            }
        } ?: throw InvalidBackupZipException("Dosya okunamadı.")

        if (!hasEntry) throw InvalidBackupZipException("Yedek dosyası geçersiz.")
    }

    private fun safeZipTarget(destinationDir: File, entryName: String): File {
        if (entryName.startsWith("/") || entryName.startsWith("\\") || entryName.contains("..")) {
            throw InvalidBackupZipException("Yedek dosyası geçersiz.")
        }
        val target = File(destinationDir, entryName)
        val destinationPath = destinationDir.canonicalPath + File.separator
        val targetPath = target.canonicalPath
        if (!targetPath.startsWith(destinationPath)) {
            throw InvalidBackupZipException("Yedek dosyası geçersiz.")
        }
        return target
    }

    private fun replaceAttachments(extractedAttachmentsDir: File): Boolean {
        val attachmentsDir = File(context.filesDir, "attachments")
        val backupDir = File(context.cacheDir, "attachments_restore_backup")
        return try {
            if (backupDir.exists()) backupDir.deleteRecursively()
            if (attachmentsDir.exists() && !attachmentsDir.renameTo(backupDir)) {
                attachmentsDir.copyRecursively(backupDir, overwrite = true)
                attachmentsDir.deleteRecursively()
            }
            if (extractedAttachmentsDir.exists()) {
                extractedAttachmentsDir.copyRecursively(attachmentsDir, overwrite = true)
            }
            backupDir.deleteRecursively()
            true
        } catch (e: Exception) {
            attachmentsDir.deleteRecursively()
            if (backupDir.exists()) backupDir.renameTo(attachmentsDir)
            false
        }
    }

    private fun addToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { input ->
            val entry = ZipEntry(entryName)
            zipOut.putNextEntry(entry)
            input.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    private fun addFolderToZip(zipOut: ZipOutputStream, folder: File, baseName: String) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addFolderToZip(zipOut, file, "$baseName/${file.name}")
            } else {
                addToZip(zipOut, file, "$baseName/${file.name}")
            }
        }
    }

    private class InvalidBackupZipException(message: String) : Exception(message)
}
