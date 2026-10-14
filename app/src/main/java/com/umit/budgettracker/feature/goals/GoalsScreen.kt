package com.umit.budgettracker.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

private val goalIcons: List<Pair<String, ImageVector>> = listOf(
    "savings" to Icons.Default.Savings,
    "beach" to Icons.Default.BeachAccess,
    "car" to Icons.Default.DirectionsCar,
    "home" to Icons.Default.Home,
    "school" to Icons.Default.School,
    "device" to Icons.Default.PhoneIphone,
    "gift" to Icons.Default.CardGiftcard,
    "health" to Icons.Default.HealthAndSafety,
    "flight" to Icons.Default.Flight,
    "ring" to Icons.Default.Diamond
)

private fun goalIcon(name: String): ImageVector = goalIcons.firstOrNull { it.first == name }?.second ?: Icons.Default.Savings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    onOpenAssets: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<Long?>(null) }
    var contributionFor by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmSuggestion by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavingsGoal?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    DetailScaffold(
        title = "Birikim Hedefleri",
        subtitle = "Amaç bazlı kumbaralar ve acil fon",
        onBack = onBack,
        snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Hedef ekle") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.emergency?.let { emergency ->
                item { EmergencyFundCard(emergency, onOpenAssets) }
            }

            if (state.suggestion.isNotEmpty()) {
                item {
                    FinanceCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text("Bu ay artan ${MoneyFormatter.format(state.leftoverThisMonth)}", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Öneri: " + state.suggestion.joinToString { "${it.goalName}'e ${MoneyFormatter.format(it.amount)}" } + ".",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row {
                                Spacer(Modifier.weight(1f))
                                FilledTonalButton(onClick = { confirmSuggestion = true }) { Text("Katkı olarak ekle") }
                            }
                        }
                    }
                }
            }

            item { FinanceSectionHeader(title = "Hedefler", subtitle = "${state.progresses.count { it.goal.isActive }} aktif") }
            if (state.isLoaded && state.progresses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Flag,
                        title = "İlk hedefini oluştur",
                        message = "Tatil, araba, ev peşinatı… Hedef tutarı ve tarihi gir; ayda ne kadar ayırman gerektiğini ve gidişatı göstereyim.",
                        actionLabel = "Hedef ekle",
                        onAction = { creating = true }
                    )
                }
            }
            items(state.progresses.sortedWith(compareBy({ !it.goal.isActive }, { it.isCompleted }, { it.goal.priority })), key = { it.goal.id }) { progress ->
                GoalCard(
                    progress = progress,
                    linkedAssetName = progress.goal.linkedAssetId?.let { id -> state.assets.firstOrNull { it.id == id }?.name },
                    onClick = { detailFor = progress.goal.id },
                    onContribute = { contributionFor = progress.goal }
                )
            }
        }
    }

    if (creating || editing != null) {
        GoalDialog(
            existing = editing,
            assets = state.assets,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { goal -> { deleting = goal; editing = null } },
            onSave = {
                viewModel.saveGoal(it)
                creating = false
                editing = null
            }
        )
    }
    contributionFor?.let { goal ->
        ContributionDialog(
            goal = goal,
            onDismiss = { contributionFor = null },
            onSave = { amount, date, note ->
                viewModel.addContribution(goal.id, amount, date, note)
                contributionFor = null
            }
        )
    }
    if (confirmSuggestion) {
        ConfirmDialog(
            title = "Önerilen katkılar",
            message = state.suggestion.joinToString("\n") { "• ${it.goalName}: ${MoneyFormatter.format(it.amount)}" } +
                "\n\nBu yalnızca kayıttır; hesabından para hareketi yapılmaz.",
            confirmLabel = "Ekle",
            onConfirm = {
                viewModel.applySuggestion(state.suggestion)
                confirmSuggestion = false
            },
            onDismiss = { confirmSuggestion = false }
        )
    }
    deleting?.let { goal ->
        ConfirmDialog(
            title = "Hedefi sil",
            message = "${goal.name} silinecek. Katkı kaydı olan hedefler silinemez; pasif yapabilirsin.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.deleteGoal(goal); deleting = null },
            onDismiss = { deleting = null }
        )
    }
    detailFor?.let { goalId ->
        val progress = state.progresses.firstOrNull { it.goal.id == goalId }
        if (progress != null) {
            ModalBottomSheet(onDismissRequest = { detailFor = null }) {
                GoalDetailSheet(
                    progress = progress,
                    contributions = state.contributions.filter { it.goalId == goalId },
                    onEdit = { editing = progress.goal; detailFor = null },
                    onContribute = { contributionFor = progress.goal },
                    onDeleteContribution = viewModel::deleteContribution
                )
            }
        }
    }
}

@Composable
private fun EmergencyFundCard(status: EmergencyFundStatus, onOpenAssets: () -> Unit) {
    val covered = status.coveredMonthsTenths
    val enough = covered != null && covered >= status.targetMonths * 10
    FinanceCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Default.HealthAndSafety,
                    tint = if (enough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Acil durum fonu", style = MaterialTheme.typography.titleMedium)
                    Text("Birikimin kaç ay yeter?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    covered?.let { "${it / 10},${it % 10} ay" } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }
            if (status.essentialMonthlyExpense <= 0L) {
                Text(
                    "Zorunlu gider ortalaması için son 3 ayda kira, fatura, abonelik, kredi, taksit ya da sabit gider kaydı gerekiyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LabeledProgress(
                    progress = if (status.targetAmount > 0L) (status.fundAmount.toFloat() / status.targetAmount) else 0f,
                    color = if (enough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    startLabel = "Fon ${MoneyFormatter.format(status.fundAmount)}",
                    endLabel = "Hedef ${status.targetMonths} ay = ${MoneyFormatter.format(status.targetAmount)}"
                )
                Text(
                    "Zorunlu aylık gider (son 3 ay ort.): ${MoneyFormatter.format(status.essentialMonthlyExpense)}" +
                        if (status.shortfall > 0L) " · eksik ${MoneyFormatter.format(status.shortfall)}" else " · hedef tamam",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpenAssets) {
                Text("Acil fona sayılan varlıkları seç")
            }
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress, linkedAssetName: String?, onClick: () -> Unit, onContribute: () -> Unit) {
    val goal = progress.goal
    val late = (progress.delayMonths ?: 0) > 0
    FinanceCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = goalIcon(goal.iconName), tint = if (goal.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.name + if (!goal.isActive) " (pasif)" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            goal.targetMonth?.let { DateUtils.formatMonthYear(it) },
                            "Hedef ${MoneyFormatter.format(goal.targetAmount)}",
                            linkedAssetName?.let { "↔ $it" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (progress.isCompleted) {
                    StatusPill("TAMAM", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                }
            }
            LabeledProgress(
                progress = progress.percent / 100f,
                startLabel = "%${progress.percent} · ${MoneyFormatter.format(progress.savedAmount)} birikti",
                endLabel = "Kalan ${MoneyFormatter.format(progress.remainingAmount)}"
            )
            if (!progress.isCompleted) {
                val plan = when {
                    progress.monthsLeft != null && progress.requiredMonthly != null ->
                        "${progress.monthsLeft} ay → ayda ${MoneyFormatter.format(progress.requiredMonthly)} ayırmalısın"
                    else -> "Tarih yok · ortalama katkı ${MoneyFormatter.format(progress.averageMonthly)}/ay"
                }
                Text(plan, style = MaterialTheme.typography.bodySmall)
                progress.projectedFinish?.let { finish ->
                    Text(
                        "Şu anki hızınla bitiş: ${DateUtils.formatMonthYear(finish)}" +
                            when {
                                late -> " ⚠ ${progress.delayMonths} ay gecikme"
                                (progress.delayMonths ?: 0) < 0 -> " · ${-progress.delayMonths!!} ay erken"
                                else -> ""
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (goal.linkedAssetId == null && goal.isActive) {
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onContribute) { Text("Katkı ekle") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalDetailSheet(
    progress: GoalProgress,
    contributions: List<GoalContribution>,
    onEdit: () -> Unit,
    onContribute: () -> Unit,
    onDeleteContribution: (GoalContribution) -> Unit
) {
    var deleting by remember { mutableStateOf<GoalContribution?>(null) }
    // Scrolls so a long contribution list never pushes the sheet past the screen.
    Column(
        Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.goal.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Düzenle") }
        }
        Text("${MoneyFormatter.format(progress.savedAmount)} / ${MoneyFormatter.format(progress.goal.targetAmount)}", style = MaterialTheme.typography.headlineSmall)
        if (progress.goal.linkedAssetId != null) {
            Text("Bu hedef bir varlığa bağlı; birikim o varlığın güncel değeridir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Button(onClick = onContribute) { Text("Katkı / çekim ekle") }
        }
        Text("Katkılar", style = MaterialTheme.typography.titleSmall)
        if (contributions.isEmpty()) {
            Text("Henüz katkı yok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        contributions.forEach { contribution ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateUtils.formatDayMonth(contribution.date) + " ${contribution.date.year}", style = MaterialTheme.typography.bodyMedium)
                    contribution.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    (if (contribution.amount >= 0) "+" else "−") + MoneyFormatter.format(kotlin.math.abs(contribution.amount)),
                    color = if (contribution.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                IconButton(onClick = { deleting = contribution }) { Icon(Icons.Default.Delete, contentDescription = "Katkıyı sil") }
            }
        }
    }
    deleting?.let { contribution ->
        ConfirmDialog(
            title = "Katkıyı sil",
            message = "${MoneyFormatter.format(contribution.amount)} tutarındaki kayıt silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { onDeleteContribution(contribution); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    existing: SavingsGoal?,
    assets: List<Asset>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (SavingsGoal) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var amountText by remember { mutableStateOf(MoneyInput.format(existing?.targetAmount)) }
    var targetMonth by remember { mutableStateOf(existing?.targetMonth ?: YearMonth.now().plusMonths(12)) }
    var priority by remember { mutableIntStateOf(existing?.priority ?: 2) }
    var iconName by remember { mutableStateOf(existing?.iconName ?: "savings") }
    var linkedAsset by remember { mutableStateOf(existing?.linkedAssetId?.let { id -> assets.firstOrNull { it.id == id } }) }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }
    val amount = MoneyInput.parse(amountText)
    FormDialog(
        title = if (existing == null) "Yeni hedef" else "Hedefi düzenle",
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && amount != null && amount > 0L,
        onConfirm = {
            onSave(
                SavingsGoal(
                    id = existing?.id ?: 0L,
                    name = name.trim(),
                    targetAmount = amount!!,
                    targetMonth = targetMonth,
                    priority = priority,
                    iconName = iconName,
                    linkedAssetId = linkedAsset?.id,
                    isActive = active,
                    note = existing?.note
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad (ör. Tatil)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Hedef tutar")
        MonthField(label = "Hedef tarih", value = targetMonth, onChange = { targetMonth = it }, allowClear = true)
        Text("Öncelik", style = MaterialTheme.typography.labelMedium)
        SegmentedChoice(options = listOf(1, 2, 3), selected = priority, onSelect = { priority = it }, label = { when (it) { 1 -> "Yüksek"; 2 -> "Orta"; else -> "Düşük" } })
        Text("Simge", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            goalIcons.forEach { (key, icon) ->
                FilterChip(selected = iconName == key, onClick = { iconName = key }, label = { Icon(icon, contentDescription = key, modifier = Modifier.size(18.dp)) })
            }
        }
        DropdownField(
            label = "Bağlı varlık (isteğe bağlı)",
            options = listOf<Asset?>(null) + assets,
            selected = linkedAsset,
            onSelect = { linkedAsset = it },
            optionLabel = { it?.name ?: "Yok — katkılarla takip et" },
            placeholder = "Yok — katkılarla takip et"
        )
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktif", modifier = Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }
        }
    }
}

@Composable
private fun ContributionDialog(goal: SavingsGoal, onDismiss: () -> Unit, onSave: (Long, LocalDate, String?) -> Unit) {
    var isDeposit by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val amount = MoneyInput.parse(amountText)
    FormDialog(
        title = goal.name,
        onDismiss = onDismiss,
        confirmEnabled = amount != null && amount > 0L,
        onConfirm = { onSave(if (isDeposit) amount!! else -amount!!, date, note.ifBlank { null }) }
    ) {
        SegmentedChoice(options = listOf(true, false), selected = isDeposit, onSelect = { isDeposit = it }, label = { if (it) "Katkı" else "Çekim" })
        AmountField(value = amountText, onValueChange = { amountText = it }, label = "Tutar")
        DateField(label = "Tarih", value = date, onChange = { date = it })
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
