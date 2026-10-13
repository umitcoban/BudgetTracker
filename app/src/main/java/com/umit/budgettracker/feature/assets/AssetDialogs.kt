package com.umit.budgettracker.feature.assets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umit.budgettracker.core.domain.calculator.AssetValuationRules
import com.umit.budgettracker.core.domain.calculator.InflationRules
import com.umit.budgettracker.core.domain.model.*
import com.umit.budgettracker.core.network.sources.PriceCatalog
import com.umit.budgettracker.core.ui.components.*
import com.umit.budgettracker.core.util.MoneyFormatter
import com.umit.budgettracker.core.util.ValueFormats
import java.time.LocalDate

val ALLOCATION_TAGS = listOf("Para piyasası", "Altın", "Yabancı teknoloji", "Hisse senedi", "Değişken", "Katılım", "Borçlanma araçları", "Döviz", "Mevduat", "Nakit", "Diğer")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDialog(
    existing: Asset?,
    prices: List<MarketPrice>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Asset, Long?) -> Unit
) {
    var type by remember { mutableStateOf(existing?.type ?: AssetType.GOLD) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var priceCode by remember { mutableStateOf(existing?.priceCode) }
    var codeText by remember { mutableStateOf(existing?.priceCode?.substringAfter(':')?.takeIf { existing.type in setOf(AssetType.FUND, AssetType.STOCK) }.orEmpty()) }
    var quantityText by remember { mutableStateOf(existing?.quantity?.takeIf { it > 0 }?.let { ValueFormats.quantityInput(it) }.orEmpty()) }
    var valueText by remember { mutableStateOf(MoneyInput.format(existing?.manualValue)) }
    var costText by remember { mutableStateOf(MoneyInput.format(existing?.costAmount)) }
    var priceText by remember {
        mutableStateOf(existing?.priceCode?.let { code -> prices.firstOrNull { it.code == code }?.price?.let { ValueFormats.priceInput(it) } }.orEmpty())
    }
    var tag by remember { mutableStateOf(existing?.allocationTag ?: "") }
    var rateText by remember { mutableStateOf(existing?.interestRateBp?.let { InflationRules.formatBp(it).removePrefix("%") }.orEmpty()) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: LocalDate.now()) }
    var maturityDate by remember { mutableStateOf(existing?.maturityDate ?: LocalDate.now().plusDays(32)) }
    var emergency by remember { mutableStateOf(existing?.isEmergencyFund ?: false) }
    var emergencyTouched by remember { mutableStateOf(existing != null) }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }

    // Sensible emergency-fund default until the user flips it: cash, deposits, money-market funds.
    LaunchedEffect(type, tag) {
        if (!emergencyTouched) emergency = type == AssetType.CASH || type == AssetType.DEPOSIT || tag == "Para piyasası"
    }

    val catalog = when (type) {
        AssetType.GOLD -> PriceCatalog.gold
        AssetType.CURRENCY -> PriceCatalog.currencies
        AssetType.CRYPTO -> PriceCatalog.crypto
        else -> emptyList()
    }
    val resolvedCode = when (type) {
        AssetType.FUND -> codeText.trim().takeIf { it.isNotEmpty() }?.let { PriceCatalog.fundCode(it) }
        AssetType.STOCK -> codeText.trim().takeIf { it.isNotEmpty() }?.let { "STOCK:" + it.uppercase() }
        AssetType.GOLD, AssetType.CURRENCY, AssetType.CRYPTO -> priceCode?.takeIf { code -> catalog.any { it.code == code } }
        else -> null
    }
    val quantity = ValueFormats.parseQuantity(quantityText)
    val manualValue = MoneyInput.parse(valueText)
    val cost = costText.takeIf { it.isNotBlank() }?.let { MoneyInput.parse(it) }
    val manualPrice = priceText.takeIf { it.isNotBlank() }?.let { ValueFormats.parsePrice(it) }
    val rateBp = rateText.takeIf { it.isNotBlank() }?.let { InflationRules.parseBp(it) }
    val knownPrice = resolvedCode?.let { code -> prices.firstOrNull { it.code == code } }
    val valid = name.isNotBlank() && when {
        type.isPriced -> resolvedCode != null && quantity != null && quantity > 0L
        else -> manualValue != null && manualValue >= 0L
    } && (type != AssetType.DEPOSIT || !maturityDate.isBefore(startDate))

    FormDialog(
        title = if (existing == null) "Varlık ekle" else "Varlığı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = valid,
        onConfirm = {
            onSave(
                Asset(
                    id = existing?.id ?: 0L,
                    name = name.trim(),
                    type = type,
                    priceCode = resolvedCode,
                    quantity = if (type.isPriced) quantity ?: 0L else AssetValuationRules.QUANTITY_SCALE,
                    manualValue = if (type.isPriced) null else manualValue,
                    costAmount = if (type == AssetType.DEPOSIT) manualValue else cost,
                    allocationTag = tag.ifBlank { null },
                    interestRateBp = if (type == AssetType.DEPOSIT) rateBp else null,
                    startDate = if (type == AssetType.DEPOSIT) startDate else null,
                    maturityDate = if (type == AssetType.DEPOSIT) maturityDate else null,
                    isEmergencyFund = emergency,
                    isActive = active,
                    note = note.ifBlank { null }
                ),
                if (type in setOf(AssetType.FUND, AssetType.STOCK)) manualPrice else manualPrice.takeIf { knownPrice == null }
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        DropdownField(
            label = "Tür",
            options = AssetType.entries,
            selected = type,
            onSelect = {
                type = it
                if (priceCode != null && catalog.none { def -> def.code == priceCode }) priceCode = null
            },
            optionLabel = { it.label }
        )
        when (type) {
            AssetType.GOLD, AssetType.CURRENCY, AssetType.CRYPTO -> {
                val selectedDef = catalog.firstOrNull { it.code == priceCode }
                DropdownField(
                    label = "Fiyat kaynağı",
                    options = catalog,
                    selected = selectedDef,
                    onSelect = {
                        priceCode = it.code
                        if (name.isBlank()) name = it.name
                    },
                    optionLabel = { def -> def.name + (prices.firstOrNull { it.code == def.code }?.let { " · ${ValueFormats.price(it.price)}" } ?: "") }
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Miktar") },
                    suffix = { Text(selectedDef?.unitLabel.orEmpty()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (knownPrice == null && resolvedCode != null) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                        label = { Text("Birim fiyat (henüz çekilmedi, elle)") },
                        suffix = { Text("₺") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            AssetType.FUND, AssetType.STOCK -> {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                    label = { Text(if (type == AssetType.FUND) "Fon kodu (ör. GTL)" else "Hisse kodu") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Pay adedi") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Birim fiyat (6 haneye kadar)") },
                    suffix = { Text("₺") },
                    supportingText = knownPrice?.let { { Text("Kayıtlı: ${ValueFormats.price(it.price)} · ${it.source}") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AssetType.DEPOSIT -> {
                AmountField(value = valueText, onValueChange = { valueText = it }, label = "Anapara")
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Yıllık brüt faiz") },
                    prefix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(label = "Başlangıç", value = startDate, onChange = { startDate = it })
                DateField(label = "Vade sonu", value = maturityDate, onChange = { maturityDate = it })
                val principal = manualValue ?: 0L
                if (principal > 0L && rateBp != null) {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, maturityDate)
                    Text(
                        "Vade sonunda brüt faiz ≈ ${MoneyFormatter.format(AssetValuationRules.simpleInterest(principal, rateBp, days))} ($days gün, stopaj hariç)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AssetType.CASH, AssetType.OTHER -> {
                AmountField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = if (type == AssetType.CASH) "Güncel bakiye" else "Güncel değer",
                    supportingText = if (type == AssetType.CASH) "Akıllı nakit uyarısı bu bakiyeden başlar; ara sıra güncelle." else null
                )
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (type.isPriced) {
            AmountField(
                value = costText,
                onValueChange = { costText = it },
                label = "Toplam maliyet (isteğe bağlı)",
                supportingText = quantity?.let { q ->
                    val price = manualPrice ?: knownPrice?.price
                    price?.let { "Güncel değer ≈ ${MoneyFormatter.format(AssetValuationRules.valueOf(q, it))}" }
                }
            )
        }
        Text("Dağılım etiketi", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ALLOCATION_TAGS.forEach { option ->
                FilterChip(selected = tag == option, onClick = { tag = if (tag == option) "" else option }, label = { Text(option) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Acil durum fonuna say", style = MaterialTheme.typography.bodyMedium)
                Text("Kısa sürede nakde dönebilecek varlıklar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = emergency, onCheckedChange = { emergency = it; emergencyTouched = true })
        }
        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktif (toplama dahil)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = active, onCheckedChange = { active = it })
            }
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TransactionDialog(asset: Asset, onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var isBuy by remember { mutableStateOf(true) }
    var quantityText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val quantity = ValueFormats.parseQuantity(quantityText)
    val amount = MoneyInput.parse(amountText)
    val valid = quantity != null && quantity > 0L && (!isBuy || (amount != null && amount >= 0L)) &&
        (isBuy || quantity <= asset.quantity)
    FormDialog(
        title = asset.name,
        onDismiss = onDismiss,
        confirmEnabled = valid,
        onConfirm = { onConfirm(if (isBuy) quantity!! else -quantity!!, amount ?: 0L) }
    ) {
        SegmentedChoice(options = listOf(true, false), selected = isBuy, onSelect = { isBuy = it }, label = { if (it) "Alım" else "Satış" })
        OutlinedTextField(
            value = quantityText,
            onValueChange = { quantityText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Miktar") },
            supportingText = { Text("Eldeki: ${ValueFormats.quantity(asset.quantity)}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isBuy) {
            AmountField(value = amountText, onValueChange = { amountText = it }, label = "Ödenen toplam tutar", supportingText = "Maliyete eklenir")
        } else {
            Text(
                "Satışta maliyet, kalan miktara oranla düşürülür (ortalama maliyet korunur).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ManualPriceDialog(price: MarketPrice, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(ValueFormats.priceInput(price.price)) }
    val parsed = ValueFormats.parsePrice(text)
    FormDialog(title = price.name, onDismiss = onDismiss, confirmEnabled = parsed != null && parsed > 0L, onConfirm = { onSave(parsed!!) }) {
        Text("${price.source} · ${freshnessLabel(price.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Birim fiyat") },
            suffix = { Text("₺") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Elle girilen fiyat bir sonraki otomatik güncellemede (kaynak açıksa) yenilenir. Fonlar otomatik güncellenmez.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PriceAlertDialog(
    existing: PriceAlert?,
    codes: List<String>,
    currentPrices: Map<String, MarketPrice>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (PriceAlert) -> Unit
) {
    var code by remember { mutableStateOf(existing?.priceCode ?: codes.firstOrNull()) }
    var condition by remember { mutableStateOf(existing?.condition ?: AlertCondition.ABOVE) }
    var thresholdText by remember { mutableStateOf(existing?.threshold?.let { ValueFormats.priceInput(it) }.orEmpty()) }
    var active by remember { mutableStateOf(existing?.isActive ?: true) }
    val threshold = ValueFormats.parsePrice(thresholdText)
    FormDialog(
        title = if (existing == null) "Fiyat alarmı" else "Alarmı düzenle",
        onDismiss = onDismiss,
        confirmEnabled = code != null && threshold != null && threshold > 0L,
        onConfirm = {
            onSave(
                PriceAlert(
                    id = existing?.id ?: 0L,
                    priceCode = code!!,
                    condition = condition,
                    threshold = threshold!!,
                    isActive = active,
                    lastTriggeredAt = existing?.lastTriggeredAt,
                    note = null
                )
            )
        },
        extraAction = onDelete?.let { { TextButton(onClick = it) { Text("Sil", color = MaterialTheme.colorScheme.error) } } }
    ) {
        DropdownField(label = "Fiyat", options = codes, selected = code, onSelect = { code = it }, optionLabel = { PriceCatalog.displayName(it) })
        code?.let { selected ->
            currentPrices[selected]?.let {
                Text("Şu an: ${ValueFormats.price(it.price)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SegmentedChoice(options = AlertCondition.entries, selected = condition, onSelect = { condition = it }, label = { it.label })
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text("Hedef fiyat") },
            suffix = { Text("₺") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Açık", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
    }
}
