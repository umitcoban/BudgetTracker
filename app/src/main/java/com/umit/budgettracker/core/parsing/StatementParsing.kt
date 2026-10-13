package com.umit.budgettracker.core.parsing

import com.umit.budgettracker.core.domain.model.Expense
import com.umit.budgettracker.core.domain.model.MerchantRule
import com.umit.budgettracker.core.util.TextMatching
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One bank statement line. [amount] is positive kuruş; [isCredit] marks refunds / payments. */
data class StatementTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Long,
    val isCredit: Boolean
)

data class StatementParseResult(val transactions: List<StatementTransaction>, val unreadLines: List<String>)

/**
 * Bank-agnostic statement reader for CSV / pasted text / OCR output. A transaction line must
 * contain a date and an amount; the description is what remains. Lines with a date but no
 * amount (or vice versa) that look like transactions are returned as unread.
 */
object BankStatementParser {
    private val creditWords = listOf("ödeme", "odeme", "iade", "iptal", "tahsilat", "virman", "alacak", "payment", "refund")
    private val skipWords = listOf(
        "önceki dönem", "onceki donem", "dönem borcu", "donem borcu", "asgari", "hesap kesim", "son ödeme",
        "son odeme", "toplam", "kart limiti", "kullanılabilir", "bakiye", "devreden"
    )

    fun parse(text: String): StatementParseResult {
        val transactions = mutableListOf<StatementTransaction>()
        val unread = mutableListOf<String>()
        text.lines().map { it.replace('\t', ' ').replace(';', ' ').replace('"', ' ').trim() }.filter { it.isNotBlank() }.forEach { line ->
            val lower = line.lowercase(java.util.Locale.forLanguageTag("tr-TR"))
            val dateMatch = TurkishNumbers.findDate(line)
            if (dateMatch == null) return@forEach
            if (skipWords.any { lower.contains(it) }) return@forEach
            val rest = line.removeRange(dateMatch.range)
            // A second date (value date) may follow; drop it before reading amounts.
            val cleaned = TurkishNumbers.findDate(rest)?.let { rest.removeRange(it.range) } ?: rest
            val amounts = TurkishNumbers.tokens(cleaned).filter { !it.isPercent && it.text.contains(',') }
            val amountToken = amounts.lastOrNull()
            if (amountToken == null) {
                unread += line
                return@forEach
            }
            val description = cleaned
                .substring(0, amountToken.start)
                .replace(Regex("""\s+"""), " ")
                .replace(Regex("""(\d+/\d+\s*taksit|\b\d{6,}\b)""", RegexOption.IGNORE_CASE), " ")
                .trim(' ', '-', '*', ':')
                .ifBlank { "Ekstre işlemi" }
            val minor = amountToken.value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
            val isCredit = minor < 0L || amountToken.text.trim().endsWith("+") ||
                creditWords.any { description.lowercase(java.util.Locale.forLanguageTag("tr-TR")).contains(it) }
            transactions += StatementTransaction(
                date = dateMatch.date,
                description = description.take(80),
                amount = kotlin.math.abs(minor),
                isCredit = isCredit
            )
        }
        return StatementParseResult(transactions, unread)
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
