package com.umit.budgettracker.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class ExchangeRateResult(
    val baseCurrency: String,
    val quoteCurrency: String,
    val rateToTry: Long,
    val rateScale: Int,
    val date: String,
    val source: String
)

open class ExchangeRateService @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun fetchRateToTry(currency: String): Result<ExchangeRateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCurrency = currency.uppercase()
            if (normalizedCurrency == "TRY") {
                return@runCatching ExchangeRateResult(
                    baseCurrency = "TRY",
                    quoteCurrency = "TRY",
                    rateToTry = RATE_SCALE.toLong(),
                    rateScale = RATE_SCALE,
                    date = "",
                    source = "MANUAL"
                )
            }

            val url = URL("https://api.frankfurter.dev/v2/rate/$normalizedCurrency/TRY")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("Kur bilgisi alınamadı.")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val response = json.parseToJsonElement(body).jsonObject
                val rate = response["rate"]?.jsonPrimitive?.content?.toBigDecimalOrNull()
                    ?: error("Kur bilgisi geçersiz.")
                val date = response["date"]?.jsonPrimitive?.content.orEmpty()

                ExchangeRateResult(
                    baseCurrency = normalizedCurrency,
                    quoteCurrency = "TRY",
                    rateToTry = rate
                        .multiply(BigDecimal(RATE_SCALE))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact(),
                    rateScale = RATE_SCALE,
                    date = date,
                    source = "Frankfurter"
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val RATE_SCALE = 10_000
    }
}
