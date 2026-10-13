package com.umit.budgettracker.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.umit.budgettracker.core.parsing.TurkishNumbers
import com.umit.budgettracker.core.util.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Standard secondary-screen chrome: back arrow ("Geri"), title with optional subtitle, actions,
 * optional FAB and snackbar. Every pushed screen uses it so back navigation and spacing match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content
    )
}

/** Default list padding for detail screens: side gutters plus room for a FAB at the bottom. */
val ScreenContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconBadge(icon = icon, size = 48)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    container: Color = tint.copy(alpha = 0.12f),
    size: Int = 40
) {
    Box(
        modifier = modifier.size(size.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

/** Large hero number card used at the top of feature screens. */
@Composable
fun HeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                )
                trailing?.invoke()
            }
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
            footer?.invoke(this)
        }
    }
}

@Composable
fun HeroMetricRow(items: List<Pair<String, String>>) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { (label, value) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f))
                Spacer(Modifier.height(3.dp))
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** A tappable row with a leading icon badge, title/subtitle and trailing content. */
@Composable
fun ListRowCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null
) {
    FinanceCard(modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon = icon, tint = iconTint)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing?.invoke()
            }
            below?.invoke(this)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/**
 * Full-height form dialog with a scrollable body; keeps long forms usable with the keyboard open.
 */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Kaydet",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                extraAction?.invoke()
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    )
}

/** TRY input that accepts Turkish formatting ("1.250,50"); see [MoneyInput]. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    suffix: String = "₺",
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

object MoneyInput {
    fun parse(text: String): Long? = TurkishNumbers.parseMinor(text)

    /** kuruş → "1250,50" for editing. */
    fun format(minor: Long?): String {
        if (minor == null) return ""
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val cents = abs % 100
        return if (cents == 0L) "$sign${abs / 100}" else "$sign${abs / 100},${cents.toString().padStart(2, '0')}"
    }
}

/** Generic exposed dropdown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "Seç"
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Read-only field that opens a month picker; [allowClear] adds "Yok". */
@Composable
fun MonthField(
    label: String,
    value: YearMonth?,
    onChange: (YearMonth?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label = label, text = value?.let { DateUtils.formatMonthYear(it) } ?: "Seçilmedi", icon = Icons.Default.CalendarMonth, modifier = modifier) { open = true }
    if (open) {
        MonthPickerDialog(
            initial = value ?: YearMonth.now(),
            allowClear = allowClear,
            onDismiss = { open = false },
            onPick = {
                onChange(it)
                open = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    PickerField(label = label, text = value?.let { DateUtils.formatFullDate(it) } ?: "Seçilmedi", icon = Icons.Default.Event, modifier = modifier) { open = true }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Vazgeç") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun PickerField(label: String, text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth?) -> Unit,
    allowClear: Boolean = false
) {
    var year by remember { mutableIntStateOf(initial.year) }
    val months = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki yıl") }
                Text("$year", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }) { Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki yıl") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.chunked(4).forEachIndexed { row, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { column, name ->
                            val month = YearMonth.of(year, row * 4 + column + 1)
                            val selected = month == initial
                            FilterChip(
                                selected = selected,
                                onClick = { onPick(month) },
                                label = { Text(name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (allowClear) TextButton(onClick = { onPick(null) }) { Text("Yok") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

/** Thin labelled progress bar used by goals, emergency fund and reserves. */
@Composable
fun LabeledProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    startLabel: String? = null,
    endLabel: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(startLabel.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Segmented single choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/** "Son güncelleme: dün 18:00" style freshness label. */
fun freshnessLabel(updatedAt: Long?, now: Long = System.currentTimeMillis()): String {
    if (updatedAt == null) return "Henüz güncellenmedi"
    val zone = java.time.ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(updatedAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val clock = "%02d:%02d".format(time.hour, time.minute)
    return when (time.toLocalDate()) {
        today -> "Son güncelleme: bugün $clock"
        today.minusDays(1) -> "Son güncelleme: dün $clock"
        else -> "Son güncelleme: ${DateUtils.formatDate(time.toLocalDate())} $clock"
    }
}
