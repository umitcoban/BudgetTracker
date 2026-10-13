package com.umit.budgettracker.feature.preferences

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.umit.budgettracker.core.datastore.AppPreferences
import com.umit.budgettracker.core.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var captureGranted by remember { mutableStateOf(viewModel.isCaptureAccessGranted()) }

    // Re-check notification access when returning from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) captureGranted = viewModel.isCaptureAccessGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Tercihler",
        subtitle = "Bu cihaza özel; yedeklere dahil edilmez",
        onBack = onBack,
        snackbarHostState = snackbar
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionLabel("Güvenlik") }
            item {
                FinanceCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(
                            icon = Icons.Default.Lock,
                            title = "Uygulama kilidi",
                            subtitle = "Parmak izi / yüz tanıma, yedek olarak PIN",
                            checked = prefs.lockEnabled && prefs.hasPin,
                            onChange = { enabled -> if (enabled) showPinDialog = true else viewModel.disableLock() }
                        )
                        if (prefs.lockEnabled && prefs.hasPin) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Kilitleme gecikmesi", style = MaterialTheme.typography.labelMedium)
                                SegmentedChoice(
                                    options = listOf(0, 60, 300),
                                    selected = prefs.lockDelaySeconds,
                                    onSelect = { seconds -> viewModel.update { it.copy(lockDelaySeconds = seconds) } },
                                    label = { when (it) { 0 -> "Hemen"; 60 -> "1 dk"; else -> "5 dk" } }
                                )
                                TextButton(onClick = { showPinDialog = true }) { Text("PIN'i değiştir") }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(
                            icon = Icons.Default.VisibilityOff,
                            title = "Son uygulamalarda gizle",
                            subtitle = "Ekran görüntüsünü ve önizlemeyi engeller",
                            checked = prefs.secureScreen,
                            onChange = { value -> viewModel.update { it.copy(secureScreen = value) } }
                        )
                    }
                }
            }

            item { SectionLabel("Veri kaynakları") }
            item {
                FinanceCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(freshnessLabel(prefs.lastRefreshAt), style = MaterialTheme.typography.titleSmall)
                                prefs.lastRefreshMessage?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                FilledTonalButton(onClick = viewModel::refreshNow) { Text("Şimdi güncelle") }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SourceSwitch("TCMB döviz kurları", "Resmi günlük bülten (today.xml)", prefs.sourceTcmb) { v -> viewModel.update { it.copy(sourceTcmb = v) } }
                        SourceSwitch("Truncgil altın fiyatları", "Gram, çeyrek, cumhuriyet… (resmi değil)", prefs.sourceTruncgil) { v -> viewModel.update { it.copy(sourceTruncgil = v) } }
                        SourceSwitch("Ons altın yedeği", "gold-api: Truncgil yoksa gram = ons × kur", prefs.sourceGoldApi) { v -> viewModel.update { it.copy(sourceGoldApi = v) } }
                        SourceSwitch("Resmi tatil takvimi", "Ödeme günü kaydırma notları (Nager.Date)", prefs.sourceHolidays) { v -> viewModel.update { it.copy(sourceHolidays = v) } }
                        SourceSwitch("Kripto fiyatları", "CoinGecko (kota sınırlı)", prefs.sourceCrypto) { v -> viewModel.update { it.copy(sourceCrypto = v) } }
                        Text(
                            "Yalnızca herkese açık fiyat ve tatil verisi indirilir; hiçbir kişisel veri gönderilmez. Kaynak çalışmazsa son bilinen değer ve elle giriş kullanılır.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            item { SectionLabel("Bildirimler") }
            item {
                FinanceCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(Icons.Default.TrendingUp, "Enflasyon hatırlatıcısı", "Her ayın 3'ünden itibaren, oran girilene kadar", prefs.notifyInflation) { v -> viewModel.update { it.copy(notifyInflation = v) } }
                        SwitchRow(Icons.Default.Insights, "Akıllı nakit uyarısı", "Önümüzdeki 14 günde açık riski", prefs.notifyCash) { v -> viewModel.update { it.copy(notifyCash = v) } }
                        SwitchRow(Icons.Default.EventAvailable, "Ödeme öncesi uyarılar", "Döviz aboneliği tutarı, tatile denk gelen ödemeler", prefs.notifyPrePayment) { v -> viewModel.update { it.copy(notifyPrePayment = v) } }
                        SwitchRow(Icons.Default.Savings, "Mevduat vadesi", "Vadeye 3 gün kala", prefs.notifyDeposit) { v -> viewModel.update { it.copy(notifyDeposit = v) } }
                        SwitchRow(Icons.Default.NotificationsActive, "Fiyat alarmları", "Varlıklar ekranında tanımladığın hedefler", prefs.notifyPriceAlerts) { v -> viewModel.update { it.copy(notifyPriceAlerts = v) } }
                        SwitchRow(Icons.Default.ShowChart, "Kur / altın hareketi", "USD, EUR, gram altın günlük değişimi", prefs.notifyFxMove) { v -> viewModel.update { it.copy(notifyFxMove = v) } }
                        if (prefs.notifyFxMove) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                SegmentedChoice(
                                    options = listOf(100, 200, 300, 500),
                                    selected = prefs.fxMoveThresholdBp,
                                    onSelect = { bp -> viewModel.update { it.copy(fxMoveThresholdBp = bp) } },
                                    label = { "≥ %${it / 100}" }
                                )
                            }
                        }
                        SwitchRow(Icons.Default.CalendarViewWeek, "Haftalık özet", "Pazar 20:00", prefs.notifyWeekly) { v -> viewModel.update { it.copy(notifyWeekly = v) } }
                    }
                }
            }

            item { SectionLabel("Acil durum fonu hedefi") }
            item {
                FinanceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Zorunlu giderlerin kaç ayını karşılasın?", style = MaterialTheme.typography.bodyMedium)
                        SegmentedChoice(
                            options = listOf(3, 6, 9, 12),
                            selected = prefs.emergencyTargetMonths,
                            onSelect = { months -> viewModel.update { it.copy(emergencyTargetMonths = months) } },
                            label = { "$it ay" }
                        )
                    }
                }
            }

            item { SectionLabel("Widget hızlı ekle") }
            item {
                FinanceCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Ana ekran widget'ında görünecek en fazla 3 kategori. Seçmezsen Market, Yemek ve Kahve gösterilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { category ->
                                val selected = category.id in prefs.widgetCategoryIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.update { current ->
                                            val ids = current.widgetCategoryIds
                                            current.copy(
                                                widgetCategoryIds = when {
                                                    selected -> ids - category.id
                                                    ids.size >= 3 -> ids.drop(1) + category.id
                                                    else -> ids + category.id
                                                }
                                            )
                                        }
                                    },
                                    label = { Text(category.name) }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Bildirimden harcama yakalama (deneysel)") }
            item {
                FinanceCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(
                            Icons.Default.MarkEmailUnread,
                            "Banka bildirimlerinden taslak oluştur",
                            "Yalnızca seçtiğin uygulamalar; onay olmadan harcama oluşmaz",
                            prefs.captureEnabled
                        ) { v -> viewModel.update { it.copy(captureEnabled = v) } }
                        if (prefs.captureEnabled) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!captureGranted) {
                                    Text(
                                        "Bildirim erişimi verilmemiş. Android ayarlarından BudgetTracker'a izin ver.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    OutlinedButton(onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }) { Text("Bildirim erişimini aç") }
                                } else {
                                    Text("Bildirim erişimi açık.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                OutlinedButton(onClick = {
                                    viewModel.loadApps()
                                    showAppPicker = true
                                }) { Text("Uygulamaları seç (${prefs.capturePackages.size})") }
                                Text(
                                    "SMS okunmaz. Bildirim metni saklanmaz; yalnızca tutar, tarih ve işyeri tahmini taslağa yazılır.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                viewModel.enableLock(pin)
                showPinDialog = false
            }
        )
    }
    if (showAppPicker) {
        AppPickerDialog(
            apps = viewModel.apps.collectAsState().value,
            selected = prefs.capturePackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { chosen ->
                viewModel.update { it.copy(capturePackages = chosen) }
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun SourceSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    SwitchRow(Icons.Default.CloudSync, title, subtitle, checked, onChange)

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin.all(Char::isDigit) && pin == repeat
    FormDialog(title = "PIN belirle", onDismiss = onDismiss, confirmEnabled = valid, onConfirm = { onConfirm(pin) }) {
        Text(
            "Biyometrik doğrulama kullanılamazsa bu PIN ile açarsın. PIN cihazda yalnızca şifrelenmiş özet olarak tutulur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8) },
            label = { Text("PIN (4–8 hane)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = repeat,
            onValueChange = { repeat = it.filter(Char::isDigit).take(8) },
            label = { Text("PIN tekrar") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = repeat.isNotEmpty() && repeat != pin,
            supportingText = if (repeat.isNotEmpty() && repeat != pin) ({ Text("PIN'ler eşleşmiyor") }) else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Banka / cüzdan uygulamaları") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Ara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = app.packageName in chosen, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(chosen) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
