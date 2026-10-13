package com.umit.budgettracker.feature.networth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.umit.budgettracker.core.domain.model.NetWorthSnapshot
import com.umit.budgettracker.core.ui.charts.Sparkline
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.DateUtils
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import java.time.YearMonth

@Composable
fun NetWorthScreen(
    onBack: () -> Unit,
    onOpenAssets: () -> Unit = {},
    viewModel: NetWorthViewModel = hiltViewModel()
) {
    val snapshots by viewModel.snapshots.collectAsState()
    val live by viewModel.live.collectAsState()
    var editing by remember { mutableStateOf<NetWorthSnapshot?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NetWorthSnapshot?>(null) }
    val latest = snapshots.firstOrNull()

    DetailScaffold(
        title = "Net Varlık",
        subtitle = "Aylık anlık görüntüler",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Bu ayı kaydet") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = ScreenContentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard(
                    label = latest?.let { "Son kayıt · ${DateUtils.formatMonthYear(it.yearMonth)}" } ?: "Net varlık",
                    value = MoneyFormatter.format(latest?.netWorth ?: live.total),
                    supporting = if (snapshots.size >= 2) {
                        val diff = snapshots[0].netWorth - snapshots[1].netWorth
                        "Önceki kayda göre ${ValueFormats.signedMoney(diff)}"
                    } else "Her ay bir kayıt al; gelişimini burada izle",
                    footer = if (snapshots.size >= 2) {
                        {
                            Sparkline(
                                values = snapshots.take(12).reversed().map { it.netWorth },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                lineColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                markerColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else null
                )
            }
            item {
                ListRowCard(
                    title = "Varlıklardan canlı toplam",
                    subtitle = if (live.hasAssets) {
                        "Nakit ${MoneyFormatter.format(live.cash)} · mevduat ${MoneyFormatter.format(live.bank)} · yatırım ${MoneyFormatter.format(live.investment)} · kredi −${MoneyFormatter.format(live.loanDebt)}"
                    } else "Varlıklar ekranına altın, döviz, fon ve bakiyelerini eklersen yeni kayıtlar otomatik dolar.",
                    icon = Icons.Default.Diamond,
                    onClick = onOpenAssets,
                    trailing = { Text(MoneyFormatter.format(live.total), style = MaterialTheme.typography.titleSmall) }
                )
            }
            if (snapshots.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Henüz kayıt yok",
                        message = "\"Bu ayı kaydet\" ile canlı varlık değerlerinden bir anlık görüntü oluştur. Geçmiş kayıtlar sonradan değişmez.",
                        actionLabel = "Bu ayı kaydet",
                        onAction = { creating = true }
                    )
                }
            }
            itemsIndexed(snapshots, key = { _, it -> it.id }) { index, snapshot ->
                val previous = snapshots.getOrNull(index + 1)
                ListRowCard(
                    title = DateUtils.formatMonthYear(snapshot.yearMonth),
                    subtitle = "Nakit ${MoneyFormatter.format(snapshot.cashAmount)} · banka ${MoneyFormatter.format(snapshot.bankAmount)} · yatırım ${MoneyFormatter.format(snapshot.investmentAmount)}" +
                        if (snapshot.creditCardDebt + snapshot.loanDebt > 0L) " · borç ${MoneyFormatter.format(snapshot.creditCardDebt + snapshot.loanDebt)}" else "",
                    onClick = { editing = snapshot },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(MoneyFormatter.format(snapshot.netWorth), style = MaterialTheme.typography.titleSmall)
                            previous?.let {
                                val diff = snapshot.netWorth - it.netWorth
                                Text(
                                    ValueFormats.signedMoney(diff),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    if (creating || editing != null) {
        SnapshotDialog(
            existing = editing,
            live = live,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { snapshot -> { deleting = snapshot; editing = null } },
            onSave = {
                viewModel.saveSnapshot(it)
                creating = false
                editing = null
            }
        )
    }
    deleting?.let { snapshot ->
        ConfirmDialog(
            title = "Kaydı sil",
            message = "${DateUtils.formatMonthYear(snapshot.yearMonth)} net varlık kaydı silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { viewModel.deleteSnapshot(snapshot); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun SnapshotDialog(
    existing: NetWorthSnapshot?,
    live: LiveNetWorth,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (NetWorthSnapshot) -> Unit
) {
    var month by remember { mutableStateOf<YearMonth?>(existing?.yearMonth ?: YearMonth.now()) }
    var cash by remember { mutableStateOf(MoneyInput.format(existing?.cashAmount ?: live.cash)) }
    var bank by remember { mutableStateOf(MoneyInput.format(existing?.bankAmount ?: live.bank)) }
    var invest by remember { mutableStateOf(MoneyInput.format(existing?.investmentAmount ?: live.investment)) }
    var card by remember { mutableStateOf(MoneyInput.format(existing?.creditCardDebt ?: 0L)) }
    var loan by remember { mutableStateOf(MoneyInput.format(existing?.loanDebt ?: live.loanDebt)) }
    fun parse(text: String) = if (text.isBlank()) 0L else MoneyInput.parse(text)
    val values = listOf(cash, bank, invest, card, loan).map { parse(it) }
    FormDialog(
        title = if (existing == null) "Net varlık kaydı" else "Kaydı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = month != null && values.all { it != null },
        onConfirm = {
            onSave(
                NetWorthSnapshot(
                    id = existing?.id ?: 0L,
                    yearMonth = month!!,
                    cashAmount = values[0]!!,
                    bankAmount = values[1]!!,
                    investmentAmount = values[2]!!,
                    creditCardDebt = values[3]!!,
                    loanDebt = values[4]!!,
                    note = existing?.note
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        if (existing == null && live.hasAssets) {
            Text("Değerler Varlıklar ekranından dolduruldu; istersen düzelt.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MonthField(label = "Ay", value = month, onChange = { month = it })
        AmountField(value = cash, onValueChange = { cash = it }, label = "Nakit / vadesiz")
        AmountField(value = bank, onValueChange = { bank = it }, label = "Mevduat")
        AmountField(value = invest, onValueChange = { invest = it }, label = "Yatırım (altın, döviz, fon…)")
        AmountField(value = card, onValueChange = { card = it }, label = "Kart borcu")
        AmountField(value = loan, onValueChange = { loan = it }, label = "Kredi borcu")
        Text(
            "Net: ${MoneyFormatter.format((values[0] ?: 0) + (values[1] ?: 0) + (values[2] ?: 0) - (values[3] ?: 0) - (values[4] ?: 0))} · aynı ay ikinci kez kaydedilirse öncekinin yerini alır.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
