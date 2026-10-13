package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.MerchantRule
import com.umit.budgettracker.core.util.TextMatching
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One bank statement line. [amount] is positive kuruş; [isCredit] marks refunds / payments.
 * [cardHint] is the card's last four digits when the statement covers several cards,
 * [bankCategory] the bank's own label (Garanti "Etiket", Yapı Kredi "Sektör"),
 * [installment] "3/9" style, [foreignAmount] e.g. "8,18 EUR".
 */
data class StatementTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Long,
    val isCredit: Boolean,
    val cardHint: String? = null,
    val bankCategory: String? = null,
    val installment: String? = null,
    val foreignAmount: String? = null
)

data class StatementParseResult(val transactions: List<StatementTransaction>, val unreadLines: List<String>) {
    val cardHints: List<String> get() = transactions.mapNotNull { it.cardHint }.distinct()
}

/**
 * Reads Turkish credit-card statements. Two modes:
 *
 * - **Table** (XLS / XLSX / CSV, cells separated by tabs or semicolons): a header row names the
 *   date, description and amount columns. Cells may sit one column off their header (QNB merges
 *   header cells). The sign convention is learned from the file: whichever sign most rows carry
 *   is spending (Garanti exports spending as negative, QNB and Yapı Kredi as positive).
 * - **Text** (PDF via OCR, pasted text): a line needs a date and an amount; the last amount on
 *   the line is the transaction amount. Credits are lines marked with a trailing "+", a leading
 *   "-" / "+" against an unsigned majority, or payment / refund wording.
 *
 * In both modes: summary lines (previous balance, totals, due dates) and bonus / point campaign
 * lines are skipped; masked card numbers start a card section; instalment and foreign-currency
 * notes are moved out of the description.
 */
object BankStatementParser {
    private val tr = java.util.Locale.forLanguageTag("tr-TR")
    private val creditWords = listOf("ödeme", "odeme", "iade", "iptal", "tahsilat", "virman", "alacak", "teşekkür", "tesekkur", "payment received", "refund")
    private val skipWords = listOf(
        "önceki dönem", "onceki donem", "dönem borcu", "donem borcu", "ekstre borcu", "asgari", "hesap kesim", "son ödeme",
        "son odeme", "minimum ödeme", "toplam", "kart limiti", "müşteri limiti", "kullanılabilir", "bakiye", "devreden",
        "devir edilen", "faiz oranı", "ödenmesi gereken", "hesap özeti tutarı", "kartınıza ait"
    )
    /** Loyalty-point movements, not money: "GİYİM BONUS KAMPANYASI", "BONUS BEDAVA ALIŞVERİŞ", ParaPuan / Worldpuan. */
    private val pointWords = Regex("""(bonus\b.{0,24}\bkampanya|bonus bedava|bonus program|parapuan|worldpuan|chip-para|maxipuan)""", RegexOption.IGNORE_CASE)
    private val maskedCard = Regex("""\d{4}\s?\d{0,2}\*{2,}[\s*]*(\d{4})\b|\*{4}\s*\*{4}\s*(\d{4})\b|\d{6}\*{6}(\d{4})\b""")
    private val installmentInText = Regex("""\((\d{1,2})/(\d{1,2})\)""")
    private val installmentPdf = Regex("""[\d.,]+x(\d{1,2})=[\d.,]+\s*(\d{1,2})\.\s*Taksit""", RegexOption.IGNORE_CASE)
    private val installmentCell = Regex("""^\s*(\d{1,2})\s*/\s*(\d{1,2})\s*$""")
    private val foreign = Regex("""\(?\s*(\d[\d.,]*)\s*(EUR|USD|GBP|CHF)\s*\)?""")

    fun parse(text: String): StatementParseResult {
        val lines = text.lines()
        for (separator in listOf('\t', ';')) {
            if (lines.count { it.contains(separator) } >= 3) {
                parseTable(lines.map { it.split(separator) })?.let { return it }
            }
        }
        return parseText(lines)
    }

    // region table mode

    private data class Columns(val date: Int, val description: Int, val amount: Int, val category: Int?, val installment: Int?, val headers: List<String>)

    private fun detectHeader(cells: List<String>): Columns? {
        fun find(predicate: (String) -> Boolean) = cells.indexOfFirst { predicate(it.trim().lowercase(tr)) }.takeIf { it >= 0 }
        val date = find { it.contains("tarih") } ?: return null
        val amount = find { it.startsWith("tutar") && !it.contains("taksit") } ?: return null
        val description = find { (it.startsWith("işlem") || it.startsWith("açıklama") || it.contains("işlemler") || it.contains("aciklama")) && !it.contains("tarih") }
            ?: return null
        return Columns(
            date = date,
            description = description,
            amount = amount,
            category = find { it == "etiket" || it.startsWith("sektör") || it == "kategori" },
            installment = find { it.contains("taksit") && !it.startsWith("tutar") },
            headers = cells
        )
    }

    private data class RawRow(val date: LocalDate, val description: String, val amountText: String, val value: java.math.BigDecimal, val card: String?, val category: String?, val installment: String?)

    private fun parseTable(rows: List<List<String>>): StatementParseResult? {
        var columns: Columns? = null
        var card: String? = null
        val raw = mutableListOf<RawRow>()
        val unread = mutableListOf<String>()
        var sawHeader = false
        rows.forEach { cells ->
            if (cells.all { it.isBlank() }) return@forEach
            detectHeader(cells)?.let {
                columns = it
                sawHeader = true
                return@forEach
            }
            val joined = cells.filter { it.isNotBlank() }.joinToString(" ")
            val cols = columns
            val rowCard = maskedCard.find(joined)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
            if (cols == null) {
                if (rowCard != null) card = rowCard
                return@forEach
            }
            val dateCell = cellNear(cells, cols.headers, cols.date) { TurkishNumbers.findDate(it) != null }
            val date = dateCell?.let { TurkishNumbers.findDate(it)?.date }
            if (date == null) {
                // Card section headers ("ASIL KART - 5193 **** **** 3333") switch the current card.
                if (rowCard != null) card = rowCard
                return@forEach
            }
            val description = cellNear(cells, cols.headers, cols.description) { it.any(Char::isLetter) && TurkishNumbers.findDate(it) == null }?.trim().orEmpty()
            val lower = description.lowercase(tr)
            if (skipWords.any { lower.contains(it) } || pointWords.containsMatchIn(description)) return@forEach
            val amountText = cellNear(cells, cols.headers, cols.amount) { TurkishNumbers.parseDecimal(it.replace("TL", "").trim()) != null }
            val value = amountText?.let { TurkishNumbers.parseDecimal(it.replace("TL", "").trim()) }
            if (amountText == null || value == null || value.signum() == 0) {
                // Bonus-only rows have no amount; anything else with a date is worth showing.
                if (amountText == null) return@forEach
                unread += joined
                return@forEach
            }
            val installment = cols.installment?.let { col -> cellNear(cells, cols.headers, col) { installmentCell.matches(it) } }
                ?.let { installmentCell.find(it)?.let { m -> "${m.groupValues[1]}/${m.groupValues[2]}" } }
            raw += RawRow(
                date = date,
                description = description,
                amountText = amountText.trim(),
                value = value,
                card = rowCard ?: card,
                category = cols.category?.let { col -> cellNear(cells, cols.headers, col) { it.any(Char::isLetter) }?.trim() },
                installment = installment
            )
        }
        if (!sawHeader || raw.isEmpty()) return null

        // The sign most rows carry is spending.
        val negatives = raw.count { it.value.signum() < 0 }
        val spendingIsNegative = negatives * 2 > raw.size
        val transactions = raw.map { row ->
            val explicitPlus = row.amountText.startsWith("+") || row.amountText.endsWith("+")
            val signCredit = if (spendingIsNegative) row.value.signum() > 0 else row.value.signum() < 0 || explicitPlus
            val cleaned = cleanDescription(row.description)
            StatementTransaction(
                date = row.date,
                description = cleaned.text,
                amount = row.value.abs().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact(),
                isCredit = signCredit || isCreditWording(row.description),
                cardHint = row.card,
                bankCategory = row.category,
                installment = row.installment ?: cleaned.installment,
                foreignAmount = cleaned.foreign
            )
        }
        return StatementParseResult(transactions, unread)
    }

    /**
     * The cell at [col], or a neighbour when header and data cells are offset by merged cells
     * (QNB). A neighbour is only used when it has no header of its own, so Garanti's "Bonus"
     * column can never be read as the amount.
     */
    private fun cellNear(cells: List<String>, headers: List<String>, col: Int, accept: (String) -> Boolean): String? =
        listOf(col, col - 1, col + 1)
            .filter { index -> index == col || headers.getOrNull(index).isNullOrBlank() }
            .mapNotNull { index -> cells.getOrNull(index)?.takeIf { it.isNotBlank() } }
            .firstOrNull(accept)

    // endregion

    // region text mode

    private fun parseText(lines: List<String>): StatementParseResult {
        val transactions = mutableListOf<StatementTransaction>()
        val unread = mutableListOf<String>()
        var card: String? = null
        lines.map { it.replace('\t', ' ').replace(';', ' ').replace('"', ' ').trim() }.filter { it.isNotBlank() }.forEach { line ->
            val lower = line.lowercase(tr)
            val rowCard = maskedCard.find(line)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
            val dateMatch = TurkishNumbers.findDate(line)
            if (dateMatch == null) {
                if (rowCard != null) card = rowCard
                return@forEach
            }
            if (skipWords.any { lower.contains(it) } || pointWords.containsMatchIn(line)) return@forEach
            val withoutCard = maskedCard.replace(line.removeRange(dateMatch.range), " ")
            // A second date (value date) may follow; drop it before reading amounts.
            val rest = TurkishNumbers.findDate(withoutCard)?.let { withoutCard.removeRange(it.range) } ?: withoutCard
            val amounts = TurkishNumbers.tokens(rest).filter { !it.isPercent && it.text.contains(',') }
            val amountToken = amounts.lastOrNull()
            if (amountToken == null) {
                unread += line
                return@forEach
            }
            val after = rest.substring((amountToken.start + amountToken.text.length).coerceAtMost(rest.length))
            val trailingMark = after.trimStart().firstOrNull()
            val rawDescription = rest.substring(0, amountToken.start)
            val cleaned = cleanDescription(rawDescription)
            val minor = amountToken.value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            transactions += StatementTransaction(
                date = dateMatch.date,
                description = cleaned.text,
                amount = kotlin.math.abs(minor),
                isCredit = minor < 0L || trailingMark == '+' || amountToken.text.startsWith("+") || isCreditWording(rawDescription),
                cardHint = rowCard ?: card,
                installment = cleaned.installment,
                foreignAmount = cleaned.foreign
            )
        }
        return StatementParseResult(transactions, unread)
    }

    // endregion

    private data class CleanText(val text: String, val installment: String?, val foreign: String?)

    private fun cleanDescription(raw: String): CleanText {
        var text = raw
        var installment: String? = null
        installmentPdf.find(text)?.let { m ->
            installment = "${m.groupValues[2]}/${m.groupValues[1]}"
            text = text.removeRange(m.range)
        }
        installmentInText.find(text)?.let { m ->
            installment = installment ?: "${m.groupValues[1]}/${m.groupValues[2]}"
            text = text.removeRange(m.range)
        }
        var foreignAmount: String? = null
        foreign.find(text)?.let { m ->
            foreignAmount = "${m.groupValues[1]} ${m.groupValues[2]}"
            text = text.removeRange(m.range)
        }
        text = text
            .replace(Regex("""\b\d+(?:[.,]\d+)?\s*TL\b""", RegexOption.IGNORE_CASE), " ") // "649,99 TL" original-amount column
            .replace(Regex("""(\s+[-+]?\d+,\d{2}[-+]?)+\s*$"""), " ")                    // bonus / point columns before the amount
            .replace(Regex("""\b\d{6,}\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '*', ':', '|')
            .ifBlank { "Ekstre işlemi" }
        return CleanText(text.take(80), installment, foreignAmount)
    }

    private fun isCreditWording(description: String): Boolean {
        val lower = description.lowercase(tr)
        return creditWords.any { lower.contains(it) }
    }
}

/** Maps a bank's own category label onto the user's categories (by name). */
object BankCategoryMap {
    private val tr = java.util.Locale.forLanguageTag("tr-TR")
    private val keywords: List<Pair<List<String>, List<String>>> = listOf(
        listOf("market", "süpermarket", "supermarket", "gıda", "gida") to listOf("Market"),
        listOf("yeme", "içme", "yemek", "restoran", "fast food", "kafe") to listOf("Yemek", "Kahve"),
        listOf("giyim", "aksesuar", "ayakkabı") to listOf("Giyim"),
        listOf("sağlık", "saglik", "eczane", "kozmetik", "bakım", "hastane") to listOf("Sağlık"),
        listOf("elektronik", "teknoloji", "bilgisayar") to listOf("Teknoloji"),
        listOf("eğlence", "eglence", "hobi", "sinema") to listOf("Eğlence"),
        listOf("kurum ödemesi", "kurum odemesi", "fatura", "telekom") to listOf("Fatura"),
        listOf("akaryakıt", "akaryakit", "yakıt", "benzin") to listOf("Yakıt"),
        listOf("ulaşım", "ulasim", "taksi", "otopark") to listOf("Ulaşım"),
        listOf("eğitim", "egitim", "okul", "kırtasiye") to listOf("Eğitim"),
        listOf("mobilya", "dekorasyon", "yapı market", "ev ") to listOf("Ev"),
        listOf("seyahat", "turizm", "otel", "havayolu", "tatil") to listOf("Tatil")
    )

    /** Lower-case without accents and with ı → i, so "SAGLIK", "Sağlık" and "SAĞLIK" compare equal. */
    private fun fold(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(tr).replace('ı', 'i'), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    fun suggest(label: String?, categories: List<Pair<Long, String>>): Long? {
        val lower = label?.let { fold(it).trim() }?.takeIf { it.isNotEmpty() } ?: return null
        categories.firstOrNull { fold(it.second) == lower }?.let { return it.first }
        keywords.forEach { (words, targets) ->
            if (words.any { lower.contains(fold(it)) }) {
                targets.forEach { target -> categories.firstOrNull { it.second.equals(target, ignoreCase = true) }?.let { return it.first } }
            }
        }
        return null
    }
}

sealed interface StatementMatch {
    val transaction: StatementTransaction

    /** Same amount, date within tolerance; best title match wins. */
    data class Matched(override val transaction: StatementTransaction, val expense: Expense) : StatementMatch

    /** Not in the app; [suggestedCategoryId] comes from learned merchant rules. */
    data class New(override val transaction: StatementTransaction, val suggestedCategoryId: Long?) : StatementMatch

    /** Similar title and date but a different amount, or a credit line (payment / refund). */
    data class Mismatch(override val transaction: StatementTransaction, val expense: Expense?, val reason: String) : StatementMatch
}

data class StatementComparison(
    val matches: List<StatementMatch>,
    val statementTotal: Long,
    val appTotal: Long,
    /** App expenses in the statement period that the statement does not contain. */
    val missingFromStatement: List<Expense>
) {
    val matchedCount: Int get() = matches.count { it is StatementMatch.Matched }
    val newCount: Int get() = matches.count { it is StatementMatch.New }
    val mismatchCount: Int get() = matches.count { it is StatementMatch.Mismatch }
    val difference: Long get() = statementTotal - appTotal
}

object StatementMatcher {
    const val DATE_TOLERANCE_DAYS = 2L

    /**
     * [expenses] should be the account's expenses for the statement period (± tolerance). Each
     * app expense is matched at most once.
     */
    fun compare(
        transactions: List<StatementTransaction>,
        expenses: List<Expense>,
        merchantRules: List<MerchantRule>
    ): StatementComparison {
        val available = expenses.toMutableList()
        val matches = transactions.map { tx ->
            if (tx.isCredit) {
                return@map StatementMatch.Mismatch(tx, null, "Ödeme / iade satırı")
            }
            val sameAmount = available
                .filter { it.amount == tx.amount && withinTolerance(it.expenseDate, tx.date) }
                .maxByOrNull { TextMatching.similarity(it.title, tx.description) * 10 - dayDistance(it.expenseDate, tx.date).toInt() }
            if (sameAmount != null) {
                available.remove(sameAmount)
                return@map StatementMatch.Matched(tx, sameAmount)
            }
            val similar = available.firstOrNull {
                withinTolerance(it.expenseDate, tx.date) && TextMatching.isSimilar(it.title, tx.description, 60)
            }
            if (similar != null) {
                available.remove(similar)
                return@map StatementMatch.Mismatch(tx, similar, "Tutar farklı")
            }
            StatementMatch.New(tx, suggestCategory(tx.description, merchantRules))
        }
        val period = transactions.map { it.date }
        val missing = if (period.isEmpty()) emptyList() else {
            val start = period.min()
            val end = period.max()
            available.filter { !it.expenseDate.isBefore(start) && !it.expenseDate.isAfter(end) }
        }
        return StatementComparison(
            matches = matches,
            statementTotal = transactions.filter { !it.isCredit }.sumOf { it.amount },
            appTotal = matches.filterIsInstance<StatementMatch.Matched>().sumOf { it.expense.amount } + missing.sumOf { it.amount } +
                matches.filterIsInstance<StatementMatch.Mismatch>().mapNotNull { it.expense }.sumOf { it.amount },
            missingFromStatement = missing
        )
    }

    /** Learned rule with the best similarity (≥ 60), most used first on ties. */
    fun suggestCategory(text: String, rules: List<MerchantRule>): Long? {
        val key = TextMatching.key(text)
        rules.firstOrNull { it.pattern == key }?.let { return it.categoryId }
        return rules
            .map { it to TextMatching.similarity(it.pattern, text) }
            .filter { it.second >= 60 }
            .maxWithOrNull(compareBy<Pair<MerchantRule, Int>> { it.second }.thenBy { it.first.hitCount })
            ?.first?.categoryId
    }

    /** Whether a new expense would duplicate an existing one (same day, amount and similar title). */
    fun findDuplicate(date: LocalDate, amount: Long, title: String, expenses: List<Expense>): Expense? =
        expenses.firstOrNull { it.expenseDate == date && it.amount == amount && TextMatching.isSimilar(it.title, title, 50) }

    private fun withinTolerance(a: LocalDate, b: LocalDate) = dayDistance(a, b) <= DATE_TOLERANCE_DAYS
    private fun dayDistance(a: LocalDate, b: LocalDate) = kotlin.math.abs(ChronoUnit.DAYS.between(a, b))
}
