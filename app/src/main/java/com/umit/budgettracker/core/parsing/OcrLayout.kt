package com.umit.budgettracker.core.parsing

/** One recognised text line with its bounding box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * Rebuilds table rows from OCR line boxes.
 *
 * Fixed-height row buckets break broker tables: a fund title wraps over two to four lines,
 * "satılabilir / toplam" columns stack two numbers, and the price sits vertically centred, so one
 * logical row lands on several text lines and the parser never sees code and numbers together.
 * [anchoredRows] instead starts a row at every line that begins with a fund code and gives it all
 * lines whose centre lies before the next code line, read left to right.
 */
object OcrLayout {
    // Title wraps such as "PIY (TL) FONU" must not open a row, so a code is followed by a dash,
    // nothing, or a number. Before a dash two to four letters count: OCR drops or doubles a
    // letter ("IIH-İSTANBUL" → "IH-İSTANBUL"), and the row must still end there, or the next
    // fund's numbers would be read into this one.
    private val fundCodeStart = Regex("""^([A-Z]{2,4}\s*[-–—]|[A-Z]{3}$|[A-Z]{3}\s+[-+−(]?\d)""")
    private val headerWords = setOf("FON", "KOD", "ADI", "TOP")

    /** Lines that open a fund row: "GTA-GPY ALTIN", "GTA" alone, or "GTA  2.415 …". */
    fun isFundAnchor(text: String): Boolean {
        val trimmed = text.trim()
        return fundCodeStart.containsMatchIn(trimmed) && trimmed.take(3) !in headerWords
    }

    /** Rows anchored on [isAnchor] lines; empty when no anchor is found. */
    fun anchoredRows(lines: List<OcrLine>, isAnchor: (String) -> Boolean = ::isFundAnchor): List<String> {
        val anchors = lines.filter { isAnchor(it.text) }.sortedBy { it.top }
        if (anchors.isEmpty()) return emptyList()
        // A row's numbers can sit slightly above its title (vertically centred cells), so each band
        // starts half a line above the anchor.
        val bandStarts = anchors.map { it.top - it.height / 2 }
        return anchors.indices.map { index ->
            val start = bandStarts[index]
            val end = bandStarts.getOrNull(index + 1) ?: Int.MAX_VALUE
            // The anchor goes first so the row's fund code is the first code the parser meets,
            // even when a wrapped title line starts a few pixels further left.
            val anchor = anchors[index]
            (listOf(anchor) + lines.filter { it !== anchor && it.centerY in start until end }.sortedWith(compareBy({ it.left }, { it.top })))
                .joinToString("  ") { it.text.trim() }
        }
    }

    /** Plain rows: lines whose vertical centres fall inside each other's boxes are joined left to right. */
    fun rows(lines: List<OcrLine>): List<String> {
        val result = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.centerY }.forEach { line ->
            val row = result.lastOrNull()
            val anchor = row?.first()
            if (anchor != null && line.centerY <= anchor.bottom && anchor.centerY >= line.top) row.add(line) else result += mutableListOf(line)
        }
        return result.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
    }
}
