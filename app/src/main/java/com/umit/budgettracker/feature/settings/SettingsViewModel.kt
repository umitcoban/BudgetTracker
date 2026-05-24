package com.umit.budgettracker.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umit.budgettracker.core.database.DatabaseBackupService
import com.umit.budgettracker.core.domain.calculator.MonthlyBudgetCalculator
import com.umit.budgettracker.core.export.*
import com.umit.budgettracker.core.dataimport.JsonImportService
import com.umit.budgettracker.core.dataimport.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val jsonExportService: JsonExportService,
    private val jsonImportService: JsonImportService,
    private val csvExportService: CsvExportService,
    private val pdfExportService: PdfExportService,
    private val fullBackupService: FullBackupService,
    private val backupService: DatabaseBackupService,
    private val calculator: MonthlyBudgetCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            when (val result = jsonExportService.exportToJson(uri)) {
                is ExportResult.Success -> _uiState.value = SettingsUiState.Success("JSON dışa aktarma tamamlandı.")
                is ExportResult.Error -> _uiState.value = SettingsUiState.Error(result.message)
            }
        }
    }

    fun importJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            when (val result = jsonImportService.importFromJson(uri)) {
                is ImportResult.Success -> _uiState.value = SettingsUiState.Success(result.summary)
                is ImportResult.Error -> _uiState.value = SettingsUiState.Error(result.message)
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            if (csvExportService.exportExpensesToCsv(uri)) {
                _uiState.value = SettingsUiState.Success("CSV dışa aktarma tamamlandı.")
            } else {
                _uiState.value = SettingsUiState.Error("CSV dışa aktarma başarısız oldu.")
            }
        }
    }

    fun exportPdf(uri: Uri, month: YearMonth) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val summary = calculator.getSummaryForMonth(month).first()
            if (pdfExportService.exportReportToPdf(uri, summary)) {
                _uiState.value = SettingsUiState.Success("PDF raporu oluşturuldu.")
            } else {
                _uiState.value = SettingsUiState.Error("PDF raporu oluşturulamadı.")
            }
        }
    }

    fun backupDatabase(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            backupService.backup(uri).onSuccess {
                _uiState.value = SettingsUiState.Success("Veritabanı yedeği alındı.")
            }.onFailure {
                _uiState.value = SettingsUiState.Error("Yedekleme başarısız oldu. Dosya yazılamadı.")
            }
        }
    }

    fun restoreDatabase(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            backupService.restore(uri).onSuccess {
                _uiState.value = SettingsUiState.Success("Geri yükleme tamamlandı. Uygulamayı yeniden başlatın.")
            }.onFailure {
                _uiState.value = SettingsUiState.Error("Geri yükleme başarısız oldu. Yedek dosyası geçersiz veya okunamadı.")
            }
        }
    }

    fun exportFullBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            when (val result = fullBackupService.exportFullBackup(uri)) {
                is ExportResult.Success -> _uiState.value = SettingsUiState.Success("Tam yedek oluşturuldu.")
                is ExportResult.Error -> _uiState.value = SettingsUiState.Error(result.message)
            }
        }
    }

    fun importFullBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            when (val result = fullBackupService.importFullBackup(uri)) {
                is ImportResult.Success -> _uiState.value = SettingsUiState.Success(result.summary)
                is ImportResult.Error -> _uiState.value = SettingsUiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = SettingsUiState.Idle
    }
}

sealed interface SettingsUiState {
    data object Idle : SettingsUiState
    data object Loading : SettingsUiState
    data class Success(val message: String) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}
