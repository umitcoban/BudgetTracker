package com.umit.budgettracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.navigation.Screen
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingFullBackupImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val isLoading = uiState is SettingsUiState.Loading

    // Launchers
    val createJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportJson(it) }
    }
    val openJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importJson(it) }
    }
    val createCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it) }
    }
    val createPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportPdf(it, YearMonth.now()) }
    }
    val createDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.backupDatabase(it) }
    }
    val openDbLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreDatabase(it) }
    }
    val createFullBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportFullBackup(it) }
    }
    val openFullBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingFullBackupImportUri = uri
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SettingsUiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            is SettingsUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ayarlar") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item { SettingsSectionTitle("Uygulama Yönetimi") }
            item {
                SettingsItem(
                    title = "Kategoriler",
                    icon = Icons.Default.Category,
                    onClick = { onNavigate(Screen.Categories.route) }
                )
            }
            item {
                SettingsItem(
                    title = "Abonelikler",
                    icon = Icons.Default.Sync,
                    onClick = { onNavigate(Screen.Subscriptions.route) }
                )
            }
            item {
                SettingsItem(
                    title = "Krediler",
                    icon = Icons.Default.AccountBalance,
                    onClick = { onNavigate(Screen.Loans.route) }
                )
            }

            item { SettingsSectionTitle("Yedekleme ve Dışa Aktarma") }
            
            item {
                SettingsItem(
                    title = "Tam Yedek Al (JSON + Fotoğraflar)",
                    icon = Icons.Default.Archive,
                    enabled = !isLoading,
                    onClick = { createFullBackupLauncher.launch("budgettracker_full_backup.zip") }
                )
            }
            item {
                SettingsItem(
                    title = "Tam Yedek Yükle",
                    icon = Icons.Default.Unarchive,
                    enabled = !isLoading,
                    onClick = { openFullBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                )
            }
            item {
                SettingsItem(
                    title = "JSON Dışa Aktar",
                    icon = Icons.Default.Download,
                    enabled = !isLoading,
                    onClick = { createJsonLauncher.launch("budgettracker_backup.json") }
                )
            }
            item {
                SettingsItem(
                    title = "JSON İçe Aktar",
                    icon = Icons.Default.Upload,
                    enabled = !isLoading,
                    onClick = { openJsonLauncher.launch(arrayOf("application/json")) }
                )
            }
            item {
                SettingsItem(
                    title = "CSV Dışa Aktar (Harcamalar)",
                    icon = Icons.Default.TableChart,
                    enabled = !isLoading,
                    onClick = { createCsvLauncher.launch("budgettracker_expenses.csv") }
                )
            }
            item {
                SettingsItem(
                    title = "PDF Rapor Dışa Aktar (Bu Ay)",
                    icon = Icons.Default.PictureAsPdf,
                    enabled = !isLoading,
                    onClick = { createPdfLauncher.launch("budgettracker_report.pdf") }
                )
            }
            item {
                SettingsItem(
                    title = "Veritabanı Yedeği Al (.db)",
                    icon = Icons.Default.Storage,
                    enabled = !isLoading,
                    onClick = { createDbLauncher.launch("budgettracker.db") }
                )
            }
            item {
                SettingsItem(
                    title = "Veritabanı Geri Yükle",
                    icon = Icons.Default.SettingsBackupRestore,
                    enabled = !isLoading,
                    onClick = { openDbLauncher.launch(arrayOf("*/*")) }
                )
            }
        }

        pendingFullBackupImportUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingFullBackupImportUri = null },
                title = { Text("Tam Yedek Yükle") },
                text = { Text("Tam yedek yükleme mevcut tüm verileri ve ekli fotoğrafları değiştirecek. Bu işlem geri alınamaz. Devam etmek istiyor musun?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.importFullBackup(uri)
                        pendingFullBackupImportUri = null
                    }) { Text("Devam Et") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFullBackupImportUri = null }) { Text("Vazgeç") }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}
