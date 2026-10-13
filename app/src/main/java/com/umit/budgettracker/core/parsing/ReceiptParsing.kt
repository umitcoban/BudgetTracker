package com.umit.budgettracker.core.parsing

import java.time.LocalDate
import java.util.Locale

data class ReceiptDraft(
    val merchant: String?,
    val date: LocalDate?,
    /** Kuruş. */
    val total: Long?
)

/**
 * Reads the useful fields from OCR text of a Turkish till receipt (fiş / e-arşiv). Every field
 * is a suggestion; the form shows them for the user to confirm.
 */
object ReceiptParser {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val totalKeywords = listOf("genel toplam", "toplam tutar", "ödenecek", "odenecek", "toplam", "tutar")
    private val notTotal = listOf("ara toplam", "kdv", "topkdv", "top kdv", "indirim", "para üstü", "para ustu", "nakit", "kart no")
    private val notMerchant = listOf("fiş", "fis", "tarih", "saat", "no:", "vkn", "tckn", "vergi", "e-arşiv", "e-arsiv", "fatura", "tel", "adres", "mersis")

    fun parse(text: String): ReceiptDraft {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val date = lines.firstNotNullOfOrNull { TurkishNumbers.findDate(it)?.date }
            ?.takeIf { !it.isAfter(LocalDate.now().plusDays(1)) && it.year >= 2000 }
        return ReceiptDraft(merchant = findMerchant(lines), date = date, total = findTotal(lines))
    }

    private fun findMerchant(lines: List<String>): String? =
        lines.take(6).firstOrNull { line ->
            val lower = line.lowercase(tr)
            line.count { it.isLetter() } >= 3 &&
                notMerchant.none { lower.contains(it) } &&
                TurkishNumbers.findDate(line) == null &&
                line.count { it.isDigit() } < line.length / 2
        }?.let { cleanMerchant(it) }

    private fun cleanMerchant(line: String): String =
        // OCR often reads the Turkish dotted capital İ as Í / Ì.
        line.replace('Í', 'İ').replace('Ì', 'İ').replace('í', 'i').replace('ì', 'i').replace(Regex("""(?<![\p{L}])(A\.?\s?Ş\.?|LTD\.?|ŞTİ\.?|TİC\.?|SAN\.?|STI\.?)(?![\p{L}])""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', '-')
            .lowercase(tr)
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(tr) } }
            .take(40)

    /** Amount on the best "TOPLAM" line; falls back to the largest "*"-prefixed amount. */
    private fun findTotal(lines: List<String>): Long? {
        val candidates = lines.withIndex().mapNotNull { (index, line) ->
            val lower = line.lowercase(tr)
            val keywordRank = totalKeywords.indexOfFirst { lower.contains(it) }
            if (keywordRank < 0 || notTotal.any { lower.contains(it) }) return@mapNotNull null
            val amount = amountsIn(line).lastOrNull()
                ?: lines.getOrNull(index + 1)?.let { amountsIn(it).lastOrNull() }
                ?: return@mapNotNull null
            Triple(keywordRank, index, amount)
        }
        candidates.minWithOrNull(compareBy<Triple<Int, Int, Long>> { it.first }.thenByDescending { it.second })?.let { return it.third }
        return lines.flatMap { line -> Regex("""\*\s*([\d.]+,\d{2})""").findAll(line).mapNotNull { TurkishNumbers.parseMinor(it.groupValues[1]) }.toList() }
            .maxOrNull()
    }

    private fun amountsIn(line: String): List<Long> =
        TurkishNumbers.tokens(line).filter { !it.isPercent && it.decimals == 2 && it.value.signum() > 0 }
            .mapNotNull { TurkishNumbers.parseMinor(it.text) }
}

data class NotificationExpense(val merchant: String, val amount: Long)

/**
 * Pulls a spending amount and merchant out of a bank / wallet notification. Only spending
 * messages qualify; one-time passwords, incoming transfers and marketing are ignored.
 */
object NotificationExpenseParser {
    private val tr = Locale.forLanguageTag("tr-TR")
    private val spendWords = listOf("harcama", "alışveriş", "alisveris", "ödeme yapıldı", "odeme yapildi", "çekildi", "cekildi", "işlem yapıldı", "islem yapildi", "provizyon", "kartınızdan", "kartinizdan")
    private val rejectWords = listOf("şifre", "sifre", "doğrulama", "dogrulama", "kod", "otp", "gelen", "yatırıldı", "yatirildi", "kampanya", "fırsat", "firsat", "puan kazandınız")
    private val amountRegex = Regex("""(?:(?:TL|TRY|₺)\s*([\d.]+,\d{2}))|(?:([\d.]+,\d{2})\s*(?:TL|TRY|₺))""", RegexOption.IGNORE_CASE)
    private val workplace = Regex("""(?:işyeri|isyeri|üye işyeri|uye isyeri|mağaza|magaza)\s*:?\s*([^,.\n]{3,40})""", RegexOption.IGNORE_CASE)
    /** Banks print the merchant in capitals: "... kartınızdan MIGROS KADIKOY'de 450,75 TL ...". */
    private val capsRun = Regex("""(?<![\p{L}\d])[A-ZÇĞİÖŞÜ0-9&.]{2,}(?:\s+[A-ZÇĞİÖŞÜ0-9&.]{2,})*""")
    private val notMerchant = setOf("TL", "TRY", "TR", "KART", "BANKA", "POS", "ATM")

    private fun merchantFrom(full: String): String? {
        workplace.find(full)?.groupValues?.get(1)?.trim()?.takeIf { it.any(Char::isLetter) }?.let { return it }
        return capsRun.findAll(full)
            .map { it.value.trim() }
            .firstOrNull { candidate -> candidate.count(Char::isLetter) >= 3 && candidate !in notMerchant && !candidate.all { it.isDigit() || it == '.' } }
    }

    fun parse(title: String, text: String): NotificationExpense? {
        val full = "$title $text"
        val lower = full.lowercase(tr)
        if (rejectWords.any { lower.contains(it) }) return null
        if (spendWords.none { lower.contains(it) }) return null
        val match = amountRegex.find(full) ?: return null
        val amount = TurkishNumbers.parseMinor(match.groupValues[1].ifEmpty { match.groupValues[2] }) ?: return null
        if (amount <= 0L) return null
        val merchant = merchantFrom(full) ?: title.takeIf { it.isNotBlank() } ?: "Kart harcaması"
        return NotificationExpense(merchant.take(40), amount)
    }
}
