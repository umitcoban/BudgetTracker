package com.umit.budgettracker.core.network.sources

import com.umit.budgettracker.core.domain.model.AssetType

/** A price the app knows how to fetch (or accept manually) and value holdings with. */
data class PriceDefinition(val code: String, val name: String, val type: AssetType, val unitLabel: String)

object PriceCatalog {
    const val FUND_PREFIX = "FUND:"
    const val FX_PREFIX = "FX:"

    val gold = listOf(
        PriceDefinition("GOLD:GRAM", "Gram altın", AssetType.GOLD, "gr"),
        PriceDefinition("GOLD:HAS", "Has altın", AssetType.GOLD, "gr"),
        PriceDefinition("GOLD:CEYREK", "Çeyrek altın", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:YARIM", "Yarım altın", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:TAM", "Tam altın", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:CUMHURIYET", "Cumhuriyet altını", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:ATA", "Ata altın", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:RESAT", "Reşat altın", AssetType.GOLD, "adet"),
        PriceDefinition("GOLD:22AYAR", "22 ayar bilezik", AssetType.GOLD, "gr"),
        PriceDefinition("GOLD:18AYAR", "18 ayar altın", AssetType.GOLD, "gr"),
        PriceDefinition("GOLD:14AYAR", "14 ayar altın", AssetType.GOLD, "gr"),
        PriceDefinition("GOLD:ONS", "Ons altın (TL)", AssetType.GOLD, "ons"),
        PriceDefinition("SILVER:GRAM", "Gram gümüş", AssetType.GOLD, "gr")
    )

    val currencies = listOf("USD", "EUR", "GBP", "CHF", "JPY", "SAR", "AUD", "CAD", "DKK", "SEK", "NOK", "KWD", "RUB", "CNY", "AED")
        .map { PriceDefinition("$FX_PREFIX$it", it, AssetType.CURRENCY, it) }

    val crypto = listOf(
        PriceDefinition("CRYPTO:BTC", "Bitcoin", AssetType.CRYPTO, "BTC"),
        PriceDefinition("CRYPTO:ETH", "Ethereum", AssetType.CRYPTO, "ETH")
    )

    val all: List<PriceDefinition> = gold + currencies + crypto

    fun find(code: String): PriceDefinition? = all.firstOrNull { it.code == code }

    fun fundCode(fund: String): String = FUND_PREFIX + fund.uppercase().trim()

    fun displayName(code: String): String = find(code)?.name ?: code.substringAfter(':')

    /** Truncgil JSON key → our code. */
    val truncgilGoldKeys = mapOf(
        "gram-altin" to "GOLD:GRAM",
        "gram-has-altin" to "GOLD:HAS",
        "ceyrek-altin" to "GOLD:CEYREK",
        "yarim-altin" to "GOLD:YARIM",
        "tam-altin" to "GOLD:TAM",
        "cumhuriyet-altini" to "GOLD:CUMHURIYET",
        "ata-altin" to "GOLD:ATA",
        "resat-altin" to "GOLD:RESAT",
        "22-ayar-bilezik" to "GOLD:22AYAR",
        "18-ayar-altin" to "GOLD:18AYAR",
        "14-ayar-altin" to "GOLD:14AYAR",
        "gumus" to "SILVER:GRAM"
    )
}
