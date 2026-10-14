package com.umit.budgettracker.core.util

import java.text.Normalizer
import java.util.Locale

/** Turkish-aware normalisation and fuzzy comparison for merchant / expense titles. */
object TextMatching {
    private val turkish = Locale.forLanguageTag("tr-TR")
    private val noise = Regex("[^a-z0-9 ]")
    /** Stand-alone numbers (card endings, terminal ids, dates); "A101" keeps its digits. */
    private val digits = Regex("(?<![a-z0-9])[0-9]+(?![a-z0-9])")
    private val spaces = Regex("\\s+")
    private val combiningMarks = Regex("\\p{M}+")
    private val stopWords = setOf(
        "ve", "ile", "a", "s", "as", "ltd", "sti", "tic", "san", "pazarlama", "magazacilik",
        "magaza", "market", "ticaret", "tr", "istanbul", "ankara", "izmir", "pos", "www", "com"
    )

    /** "MİGROS TİC. A.Ş. 1234" → "migros tic a s" (see [tokens]). Accents folded, stand-alone numbers removed. */
    fun normalize(text: String): String {
        // Dotless ı has no decomposition; every other accent (ç ş ğ ö ü, OCR's í/ì …) is stripped via NFD.
        val lower = Normalizer.normalize(text.lowercase(turkish).replace('ı', 'i'), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
        return lower
            .replace(noise, " ")
            .replace(digits, " ")
            .replace(spaces, " ")
            .trim()
    }

    fun tokens(text: String): Set<String> =
        normalize(text).split(' ').filter { it.length >= 2 && it !in stopWords }.toSet()

    /** Key used to group the "same" recurring expense / merchant. */
    fun key(text: String): String = tokens(text).sorted().joinToString(" ").ifEmpty { normalize(text) }

    /**
     * Similarity in 0..100: token Jaccard, boosted when one side's tokens are contained in the
     * other (bank statements often append city / terminal codes to the merchant name).
     */
    fun similarity(a: String, b: String): Int {
        val left = tokens(a)
        val right = tokens(b)
        if (left.isEmpty() || right.isEmpty()) {
            return if (normalize(a).isNotEmpty() && normalize(a) == normalize(b)) 100 else 0
        }
        val intersection = left.intersect(right).size
        if (intersection == 0) {
            val prefixHit = left.any { l -> right.any { r -> l.length >= 4 && r.length >= 4 && (l.startsWith(r) || r.startsWith(l)) } }
            return if (prefixHit) 60 else 0
        }
        if (left.containsAll(right) || right.containsAll(left)) return 90
        val union = left.union(right).size
        return intersection * 100 / union
    }

    fun isSimilar(a: String, b: String, threshold: Int = 50): Boolean = similarity(a, b) >= threshold
}
