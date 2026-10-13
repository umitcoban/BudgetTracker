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
import com.umit.budgettracker.core.ui.components.FinanceCard
import com.umit.budgettracker.core.ui.components.IconBadge
import androidx.compose.ui.text.style.TextOverflow
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    val isLoading = uiState is SettingsUiState.Loading

    // Launchers
    val createJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportJson(it) }
    }
    val openJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pendingRestore = PendingRestore(
                title = "JSON İçe Aktar",
                message = "İçe aktarma mevcut tüm verileri dosyadakilerle değiştirecek. Bu işlem geri alınamaz. Devam etmek istiyor musun?",
                action = { viewModel.importJson(it) }
            )
        }
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
        uri?.let {
            pendingRestore = PendingRestore(
                title = "Veritabanını Geri Yükle",
                message = "Geri yükleme mevcut veritabanının tamamını yedek dosyasıyla değiştirecek. Bu işlem geri alınamaz. Devam etmek istiyor musun?",
                action = { viewModel.restoreDatabase(it) }
            )
        }
    }
    val createFullBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportFullBackup(it) }
    }
    val openFullBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            pendingRestore = PendingRestore(
                title = "Tam Yedek Yükle",
                message = "Tam yedek yükleme mevcut tüm verileri ve ekli fotoğrafları değiştirecek. Bu işlem geri alınamaz. Devam etmek istiyor musun?",
                action = { viewModel.importFullBackup(it) }
            )
        }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Menü", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Araçlar, planlama ve ayarlar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                // In the top bar rather than the list so a running backup doesn't shift the scroll position.
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            hubSections.forEach { section ->
                item(key = section.title) { HubSectionTitle(section.title) }
                section.tiles.chunked(2).forEach { row ->
                    item(key = section.title + row.first().screen.route) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { tile ->
                                HubTile(tile, modifier = Modifier.weight(1f)) { onNavigate(tile.screen.route) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item { HubSectionTitle("Kayıt yönetimi") }
            item {
                FinanceCard {
                    Column {
                        recordScreens.forEachIndexed { index, screen ->
                            SettingsItem(title = screen.title, icon = screen.icon, onClick = { onNavigate(screen.route) })
                            if (index != recordScreens.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            item { HubSectionTitle("Uygulama") }
            item {
                FinanceCard {
                    SettingsItem(
                        title = Screen.Preferences.title,
                        subtitle = "Kilit, veri kaynakları, bildirimler, widget",
                        icon = Screen.Preferences.icon,
                        onClick = { onNavigate(Screen.Preferences.route) }
                    )
                }
            }

            item { HubSectionTitle("Yedekleme ve dışa aktarma") }
            item {
                FinanceCard {
                    Column {
                        val backupItems = listOf(
                            Triple("Tam yedek al (JSON + fotoğraflar)", Icons.Default.Archive) { createFullBackupLauncher.launch("budgettracker_full_backup.zip") },
                            Triple("Tam yedek yükle", Icons.Default.Unarchive) { openFullBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            Triple("JSON dışa aktar", Icons.Default.Download) { createJsonLauncher.launch("budgettracker_backup.json") },
                            Triple("JSON içe aktar", Icons.Default.Upload) { openJsonLauncher.launch(arrayOf("application/json")) },
                            Triple("CSV dışa aktar (harcamalar)", Icons.Default.TableChart) { createCsvLauncher.launch("budgettracker_expenses.csv") },
                            Triple("PDF rapor (bu ay + 12 ay + yıl özeti)", Icons.Default.PictureAsPdf) { createPdfLauncher.launch("budgettracker_report.pdf") },
                            Triple("Veritabanı yedeği al (.db)", Icons.Default.Storage) { createDbLauncher.launch("budgettracker.db") },
                            Triple("Veritabanı geri yükle", Icons.Default.SettingsBackupRestore) { openDbLauncher.launch(arrayOf("*/*")) }
                        )
                        backupItems.forEachIndexed { index, (title, icon, action) ->
                            SettingsItem(title = title, icon = icon, enabled = !isLoading, onClick = action)
                            if (index != backupItems.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        pendingRestore?.let { pending ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text(pending.title) },
                text = { Text(pending.message) },
                confirmButton = {
                    TextButton(onClick = {
                        pending.action()
                        pendingRestore = null
                    }) { Text("Devam Et") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestore = null }) { Text("Vazgeç") }
                }
            )
        }
    }
}

/** A destructive restore waiting for the user's explicit confirmation. */
private data class PendingRestore(
    val title: String,
    val message: String,
    val action: () -> Unit
)

private data class HubTile(val screen: Screen, val subtitle: String)
private data class HubSection(val title: String, val tiles: List<HubTile>)

private val hubSections = listOf(
    HubSection(
        "Varlık ve birikim",
        listOf(
            HubTile(Screen.Assets, "Altın, döviz, fon, mevduat"),
            HubTile(Screen.Goals, "Kumbaralar ve acil fon"),
            HubTile(Screen.NetWorth, "Aylık net değer"),
            HubTile(Screen.Inflation, "TÜFE ve reel değer")
        )
    ),
    HubSection(
        "Planlama ve analiz",
        listOf(
            HubTile(Screen.PlannedExpenses, "Kasko, MTV, büyük alımlar"),
            HubTile(Screen.Simulation, "Senaryo ve zam planı"),
            HubTile(Screen.CashFlow, "Takvim ve nakit tahmini"),
            HubTile(Screen.YearEnd, "Yılın özeti"),
            HubTile(Screen.CategoryBudgets, "Aylık limitler"),
            HubTile(Screen.Insights, "Tekrarlayanlar, taslaklar")
        )
    ),
    HubSection(
        "Hızlı araçlar",
        listOf(
            HubTile(Screen.ReceiptScan, "Fotoğraftan harcama"),
            HubTile(Screen.StatementImport, "Banka ekstresiyle eşleştir"),
            HubTile(Screen.FundImport, "Portföy tablosunu oku"),
            HubTile(Screen.QuickAdd, "Tutar yaz, kaydet")
        )
    )
)

private val recordScreens = listOf(
    Screen.Categories,
    Screen.Subscriptions,
    Screen.FixedExpenses,
    Screen.Loans,
    Screen.Installments,
    Screen.DebtTracking,
    Screen.ExpenseTemplates
)

@Composable
private fun HubSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun HubTile(tile: HubTile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FinanceCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon = tile.screen.icon, size = 38)
            Column {
                Text(tile.screen.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    tile.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}
