package com.umit.budgettracker.core.network.sources

import com.umit.budgettracker.core.domain.calculator.AssetValuationRules.PRICE_SCALE
import com.umit.budgettracker.core.domain.model.InflationRate
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.model.PublicHoliday
import com.umit.budgettracker.core.parsing.TurkishNumbers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure parsers for the public data sources documented in docs/DATA_SOURCES.md. Each returns an
 * empty result (never throws) when the format changes, so a broken source only means "no update".
 * Fixtures under app/src/test/resources/datasources/ pin the formats seen on 2026-10-13.
 */
object SourceParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val gramsPerOunce = BigDecimal("31.1034768")

    /** TCMB today.xml → FX:<code> at ForexBuying (BanknoteBuying fallback) per one unit. */
    fun parseTcmb(xml: String, fetchedAt: Long): List<MarketPrice> = runCatching {
        val block = Regex("""<Currency[^>]*Kod="([A-Z]{3})"[^>]*>(.*?)</Currency>""", RegexOption.DOT_MATCHES_ALL)
        block.findAll(xml).mapNotNull { match ->
            val code = match.groupValues[1]
            val body = match.groupValues[2]
            fun tag(name: String) = Regex("<$name>([^<]*)</$name>").find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            val unit = tag("Unit")?.toBigDecimalOrNull() ?: BigDecimal.ONE
            val rate = (tag("ForexBuying") ?: tag("BanknoteBuying"))?.toBigDecimalOrNull() ?: return@mapNotNull null
            if (rate.signum() <= 0 || unit.signum() <= 0) return@mapNotNull null
            MarketPrice(
                code = PriceCatalog.FX_PREFIX + code,
                name = code,
                price = rate.divide(unit, 10, RoundingMode.HALF_UP).toScaled(),
                previousPrice = null,
                source = "TCMB",
                updatedAt = fetchedAt
            )
        }.toList()
    }.getOrDefault(emptyList())

    /** Bulletin date of today.xml ("13.10.2026"), shown next to the rates. */
    fun tcmbBulletinDate(xml: String): String? = Regex("""Tarih="([0-9.]+)"""").find(xml)?.groupValues?.get(1)

    /**
     * Truncgil today.json → gold coins/grams in TRY at the buying ("Alış") price plus FX fallback.
     * "ons" is quoted in USD and converted with the file's own USD rate.
     */
    fun parseTruncgil(body: String, fetchedAt: Long): List<MarketPrice> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        fun buying(key: String): BigDecimal? {
            val item = root[key] as? JsonObject ?: return null
            val raw = (item["Alış"] ?: item["Buying"])?.jsonPrimitive?.content ?: return null
            return TurkishNumbers.parseDecimal(raw)?.takeIf { it.signum() > 0 }
        }
        val prices = mutableListOf<MarketPrice>()
        PriceCatalog.truncgilGoldKeys.forEach { (key, code) ->
            buying(key)?.let { prices += MarketPrice(code, PriceCatalog.displayName(code), it.toScaled(), null, "Truncgil", fetchedAt) }
        }
        val usd = buying("USD")
        val ounceUsd = buying("ons")
        if (usd != null && ounceUsd != null) {
            prices += MarketPrice("GOLD:ONS", PriceCatalog.displayName("GOLD:ONS"), ounceUsd.multiply(usd).toScaled(), null, "Truncgil", fetchedAt)
        }
        PriceCatalog.currencies.forEach { definition ->
            buying(definition.name)?.let {
                prices += MarketPrice(definition.code, definition.name, it.toScaled(), null, "Truncgil", fetchedAt)
            }
        }
        prices
    }.getOrDefault(emptyList())

    /** gold-api.com XAU price in USD per troy ounce. */
    fun parseGoldApiOunceUsd(body: String): BigDecimal? = runCatching {
        json.parseToJsonElement(body).jsonObject["price"]?.jsonPrimitive?.content?.toBigDecimalOrNull()
    }.getOrNull()?.takeIf { it.signum() > 0 }

    /** Fallback gram price: ounce(USD) × USDTRY / 31,1034768. Coin premiums can't be derived. */
    fun gramGoldFromOunce(ounceUsd: BigDecimal, usdTry: BigDecimal, fetchedAt: Long): MarketPrice =
        MarketPrice(
            code = "GOLD:GRAM",
            name = PriceCatalog.displayName("GOLD:GRAM"),
            price = ounceUsd.multiply(usdTry).divide(gramsPerOunce, 10, RoundingMode.HALF_UP).toScaled(),
            previousPrice = null,
            source = "gold-api (ons × kur)",
            updatedAt = fetchedAt
        )

    /** date.nager.at PublicHolidays response. */
    fun parseNager(body: String): List<PublicHoliday> = runCatching {
        json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val item = element.jsonObject
            val date = item["date"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val name = item["localName"]?.jsonPrimitive?.content ?: item["name"]?.jsonPrimitive?.content ?: "Resmi tatil"
            PublicHoliday(date, name)
        }
    }.getOrDefault(emptyList())

    /**
     * TCMB "Consumer Prices" HTML table: rows "MM-YYYY | annual % | monthly %". HTML scraping,
     * so it is only used to suggest missing months; the user confirms before anything is saved.
     */
    fun parseTcmbCpi(html: String): List<InflationRate> = runCatching {
        val text = html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
        Regex("""\b(\d{2})-(\d{4})\s+(-?\d+[.,]\d+)\s+(-?\d+[.,]\d+)""").findAll(text).mapNotNull { match ->
            val (month, year, annual, monthly) = match.destructured
            val ym = runCatching { YearMonth.of(year.toInt(), month.toInt()) }.getOrNull() ?: return@mapNotNull null
            val monthlyBp = com.umit.budgettracker.core.domain.calculator.InflationRules.parseBp(monthly) ?: return@mapNotNull null
            InflationRate(
                month = ym,
                monthlyRateBp = monthlyBp,
                annualRateBp = com.umit.budgettracker.core.domain.calculator.InflationRules.parseBp(annual),
                note = null,
                source = "TCMB"
            )
        }.distinctBy { it.month }.toList()
    }.getOrDefault(emptyList())

    /** CoinGecko simple price {"bitcoin":{"try":4108685}}. */
    fun parseCoinGecko(body: String, fetchedAt: Long): List<MarketPrice> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        listOf("bitcoin" to "CRYPTO:BTC", "ethereum" to "CRYPTO:ETH").mapNotNull { (id, code) ->
            val value = (root[id] as? JsonObject)?.get("try")?.jsonPrimitive?.content?.toBigDecimalOrNull() ?: return@mapNotNull null
            MarketPrice(code, PriceCatalog.displayName(code), value.toScaled(), null, "CoinGecko", fetchedAt)
        }
    }.getOrDefault(emptyList())

    private fun BigDecimal.toScaled(): Long = multiply(BigDecimal.valueOf(PRICE_SCALE)).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
