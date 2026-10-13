package com.umit.budgettracker.core.network.sources

import com.umit.budgettracker.core.datastore.SettingsDataStore
import com.umit.budgettracker.core.domain.model.InflationRate
import com.umit.budgettracker.core.domain.model.MarketPrice
import com.umit.budgettracker.core.domain.repository.AssetRepository
import com.umit.budgettracker.core.domain.repository.HolidayRepository
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Plain GET with short timeouts. Only public price/index URLs ever pass through here. */
open class HttpFetcher @Inject constructor() {
    open suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            // ASCII only: TCMB's firewall answers a non-ASCII User-Agent with a "Page Not Found" page.
            setRequestProperty("User-Agent", "BudgetTracker-Android/1.0")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

data class RefreshReport(
    val savedCount: Int,
    val succeeded: List<String>,
    val failed: List<String>,
    /** True when every attempted source failed with a server / network error → worth retrying. */
    val retryable: Boolean
) {
    val message: String
        get() = when {
            succeeded.isEmpty() && failed.isEmpty() -> "Tüm veri kaynakları kapalı."
            failed.isEmpty() -> "$savedCount fiyat güncellendi (${succeeded.joinToString()})."
            succeeded.isEmpty() -> "Güncellenemedi: ${failed.joinToString()}. Son bilinen değerler kullanılıyor."
            else -> "$savedCount fiyat güncellendi (${succeeded.joinToString()}); alınamayan: ${failed.joinToString()}."
        }
}

/**
 * Pulls public prices (TCMB FX, Truncgil gold, gold-api fallback, optional CoinGecko) and holidays.
 * Each source is optional (Ayarlar → Veri kaynakları); a failure keeps the last stored value.
 */
@Singleton
class MarketDataService @Inject constructor(
    private val http: HttpFetcher,
    private val assetRepository: AssetRepository,
    private val holidayRepository: HolidayRepository,
    private val settings: SettingsDataStore
) {
    suspend fun refreshPrices(): RefreshReport {
        val prefs = settings.current()
        val now = System.currentTimeMillis()
        val collected = linkedMapOf<String, MarketPrice>()
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var retryable = true

        suspend fun attempt(name: String, block: suspend () -> List<MarketPrice>) {
            try {
                val prices = block()
                if (prices.isEmpty()) {
                    failed += name
                    retryable = false
                } else {
                    prices.forEach { price -> collected.putIfAbsent(price.code, price) }
                    succeeded += name
                }
            } catch (e: HttpStatusException) {
                Log.w(TAG, "$name: HTTP ${e.code}")
                failed += name
                if (e.code in 400..499) retryable = false
            } catch (e: Exception) {
                Log.w(TAG, "$name failed", e)
                failed += name
            }
        }

        if (prefs.sourceTcmb) attempt("TCMB") {
            val body = http.get(TCMB_URL)
            SourceParsers.parseTcmb(body, now).also { if (it.isEmpty()) Log.w(TAG, "TCMB unreadable: ${body.take(160)}") }
        }
        if (prefs.sourceTruncgil) attempt("Truncgil") { SourceParsers.parseTruncgil(http.get(TRUNCGIL_URL), now) }
        if (prefs.sourceGoldApi && "GOLD:GRAM" !in collected) {
            attempt("gold-api") {
                val ounce = SourceParsers.parseGoldApiOunceUsd(http.get(GOLD_API_URL)) ?: return@attempt emptyList()
                val usd = collected["${PriceCatalog.FX_PREFIX}USD"]?.price ?: return@attempt emptyList()
                val usdTry = BigDecimal.valueOf(usd).movePointLeft(6)
                listOf(SourceParsers.gramGoldFromOunce(ounce, usdTry, now))
            }
        }
        if (prefs.sourceCrypto) attempt("CoinGecko") { SourceParsers.parseCoinGecko(http.get(COINGECKO_URL), now) }

        assetRepository.savePrices(collected.values.toList())
        val report = RefreshReport(collected.size, succeeded, failed, retryable && succeeded.isEmpty() && failed.isNotEmpty())
        settings.update { it.copy(lastRefreshAt = now, lastRefreshMessage = report.message) }
        return report
    }

    /** Caches this year's and next year's holidays; returns how many were stored. */
    suspend fun refreshHolidays(today: LocalDate = LocalDate.now()): Int {
        if (!settings.current().sourceHolidays) return 0
        var stored = 0
        listOf(today.year, today.year + 1).forEach { year ->
            val holidays = runCatching { SourceParsers.parseNager(http.get("$NAGER_URL/$year/TR")) }.getOrDefault(emptyList())
            if (holidays.isNotEmpty()) {
                holidayRepository.replaceYear(year, holidays)
                stored += holidays.size
            }
        }
        if (stored > 0) settings.update { it.copy(holidaysSyncedAt = System.currentTimeMillis()) }
        return stored
    }

    /** Reads the TCMB CPI table. Returns the rows; the caller decides what (if anything) to save. */
    suspend fun fetchCpi(): Result<List<InflationRate>> = runCatching {
        SourceParsers.parseTcmbCpi(http.get(TCMB_CPI_URL)).ifEmpty { error("Tablo okunamadı") }
    }

    companion object {
        private const val TAG = "MarketData"
        const val TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml"
        const val TRUNCGIL_URL = "https://finans.truncgil.com/today.json"
        const val GOLD_API_URL = "https://api.gold-api.com/price/XAU"
        const val COINGECKO_URL = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=try"
        const val NAGER_URL = "https://date.nager.at/api/v3/PublicHolidays"
        const val TCMB_CPI_URL =
            "https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB+EN/Main+Menu/Statistics/Inflation+Data/Consumer+Prices"
    }
}
