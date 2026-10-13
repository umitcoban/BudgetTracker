package com.umit.budgettracker.core.parsing

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for legacy Excel 97-2003 `.xls` files (BIFF8 inside an OLE2 compound file), the
 * format Turkish banks still use for statement downloads. Reads the first worksheet's text and
 * number cells; formatting, formulas' expressions and other sheets are ignored.
 *
 * Returns rows as tab-separated lines with empty cells kept, so column positions survive.
 */
object XlsReader {
    fun isXls(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(OLE_SIGNATURE)

    fun readFirstSheet(bytes: ByteArray): List<List<String>> {
        val workbook = CompoundFile(bytes).stream("Workbook") ?: CompoundFile(bytes).stream("Book")
            ?: error("Workbook akışı bulunamadı")
        return parseBiff(workbook)
    }

    fun toText(rows: List<List<String>>): String = rows.joinToString("\n") { it.joinToString("\t") }

    // region BIFF8

    private fun parseBiff(data: ByteArray): List<List<String>> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val sst = mutableListOf<String>()
        val cells = sortedMapOf<Int, MutableMap<Int, String>>()
        var substream = 0 // 0 = before globals, 1 = globals, 2 = first worksheet, 3 = done
        var pendingFormulaCell: Pair<Int, Int>? = null
        val sstSegments = mutableListOf<ByteArray>()
        var collectingSst = false

        fun put(row: Int, col: Int, value: String) {
            if (value.isNotEmpty()) cells.getOrPut(row) { mutableMapOf() }[col] = value
        }

        while (buffer.remaining() >= 4 && substream < 3) {
            val type = buffer.short.toInt() and 0xFFFF
            val length = buffer.short.toInt() and 0xFFFF
            if (length > buffer.remaining()) break
            val record = ByteArray(length).also { buffer.get(it) }
            val r = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)

            if (collectingSst && type != CONTINUE) {
                sst += readSst(sstSegments)
                collectingSst = false
            }
            when (type) {
                BOF -> {
                    val docType = if (length >= 4) r.getShort(2).toInt() and 0xFFFF else 0
                    substream = when {
                        substream == 0 -> 1
                        substream == 1 && docType == 0x10 -> 2
                        else -> substream
                    }
                }
                EOF -> if (substream == 2) substream = 3
                SST -> if (substream == 1) {
                    sstSegments.clear()
                    sstSegments += record
                    collectingSst = true
                }
                CONTINUE -> if (collectingSst) sstSegments += record
                LABELSST -> if (substream == 2) {
                    val index = r.getInt(6)
                    put(r.getShort(0).u16(), r.getShort(2).u16(), sst.getOrElse(index) { "" })
                }
                LABEL -> if (substream == 2) {
                    put(r.getShort(0).u16(), r.getShort(2).u16(), readShortString(record, 6))
                }
                NUMBER -> if (substream == 2) {
                    put(r.getShort(0).u16(), r.getShort(2).u16(), formatNumber(r.getDouble(6)))
                }
                RK -> if (substream == 2) {
                    put(r.getShort(0).u16(), r.getShort(2).u16(), formatNumber(decodeRk(r.getInt(6))))
                }
                MULRK -> if (substream == 2) {
                    val row = r.getShort(0).u16()
                    val first = r.getShort(2).u16()
                    val count = (length - 6) / 6
                    for (i in 0 until count) {
                        put(row, first + i, formatNumber(decodeRk(r.getInt(4 + i * 6 + 2))))
                    }
                }
                FORMULA -> if (substream == 2) {
                    val row = r.getShort(0).u16()
                    val col = r.getShort(2).u16()
                    val isNumber = (r.getShort(12).toInt() and 0xFFFF) != 0xFFFF
                    if (isNumber) put(row, col, formatNumber(r.getDouble(6)))
                    else if (record[6].toInt() == 0) pendingFormulaCell = row to col
                }
                STRING -> pendingFormulaCell?.let { (row, col) ->
                    put(row, col, readShortString(record, 0))
                    pendingFormulaCell = null
                }
            }
        }
        if (collectingSst) sst += readSst(sstSegments)

        val maxCol = cells.values.flatMap { it.keys }.maxOrNull() ?: return emptyList()
        val lastRow = cells.lastKey()
        return (0..lastRow).map { row ->
            val values = cells[row]
            (0..maxCol).map { col -> values?.get(col).orEmpty() }
        }
    }

    /** Shared strings may be split across CONTINUE records; each continuation restarts with a flags byte. */
    private fun readSst(segments: List<ByteArray>): List<String> {
        val reader = SegmentReader(segments)
        reader.skip(4) // total count
        val unique = reader.int()
        val result = ArrayList<String>(unique)
        repeat(unique) {
            if (!reader.hasMore()) return result
            val charCount = reader.u16()
            var flags = reader.u8()
            val richRuns = if (flags and 0x08 != 0) reader.u16() else 0
            val extSize = if (flags and 0x04 != 0) reader.int() else 0
            val text = StringBuilder(charCount)
            var remaining = charCount
            while (remaining > 0) {
                if (reader.atSegmentEnd()) {
                    reader.nextSegment()
                    flags = reader.u8()
                }
                val wide = flags and 0x01 != 0
                val available = reader.segmentRemaining() / (if (wide) 2 else 1)
                val take = minOf(remaining, available.coerceAtLeast(1))
                repeat(take) { text.append(if (wide) reader.u16().toChar() else (reader.u8()).toChar()) }
                remaining -= take
            }
            reader.skip(richRuns * 4)
            reader.skip(extSize)
            result += text.toString()
        }
        return result
    }

    private fun readShortString(record: ByteArray, offset: Int): String {
        val r = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        if (record.size < offset + 3) return ""
        val count = r.getShort(offset).u16()
        val flags = record[offset + 2].toInt() and 0xFF
        val start = offset + 3
        return if (flags and 0x01 != 0) {
            String(record, start, minOf(count * 2, record.size - start), Charsets.UTF_16LE)
        } else {
            String(record, start, minOf(count, record.size - start), Charsets.ISO_8859_1)
        }
    }

    private fun decodeRk(rk: Int): Double {
        val divide = rk and 0x01 != 0
        val value = if (rk and 0x02 != 0) {
            (rk shr 2).toDouble()
        } else {
            java.lang.Double.longBitsToDouble((rk.toLong() and 0xFFFFFFFCL) shl 32)
        }
        return if (divide) value / 100.0 else value
    }

    /** Numeric cells become Turkish-style text ("2005,74") so the statement parser treats them like text cells. */
    private fun formatNumber(value: Double): String =
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString().replace('.', ',')

    private fun Short.u16() = toInt() and 0xFFFF

    private class SegmentReader(private val segments: List<ByteArray>) {
        private var segment = 0
        private var position = 0
        fun hasMore() = segment < segments.size && (position < segments[segment].size || segment + 1 < segments.size)
        fun atSegmentEnd() = position >= segments[segment].size
        fun segmentRemaining() = segments[segment].size - position
        fun nextSegment() { segment++; position = 0 }
        private fun ensure() { while (segment < segments.size && position >= segments[segment].size) nextSegment() }
        fun u8(): Int { ensure(); return segments[segment][position++].toInt() and 0xFF }
        fun u16(): Int = u8() or (u8() shl 8)
        fun int(): Int = u16() or (u16() shl 16)
        fun skip(count: Int) { repeat(count) { if (segment < segments.size) { ensure(); if (segment < segments.size) position++ } } }
    }

    // endregion

    // region OLE2 compound file

    private class CompoundFile(private val bytes: ByteArray) {
        private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private val sectorSize = 1 shl buf.getShort(0x1E).toInt()
        private val miniSectorSize = 1 shl buf.getShort(0x20).toInt()
        private val miniCutoff = buf.getInt(0x38)
        private val fat: IntArray = buildFat()

        private fun sectorOffset(sector: Int) = (sector + 1) * sectorSize

        private fun buildFat(): IntArray {
            val fatSectors = mutableListOf<Int>()
            for (i in 0 until 109) {
                val s = buf.getInt(0x4C + i * 4)
                if (s >= 0) fatSectors += s
            }
            var difat = buf.getInt(0x44)
            var guard = 0
            while (difat >= 0 && guard++ < 10_000) {
                val off = sectorOffset(difat)
                val perSector = sectorSize / 4 - 1
                for (i in 0 until perSector) {
                    val s = buf.getInt(off + i * 4)
                    if (s >= 0) fatSectors += s
                }
                difat = buf.getInt(off + perSector * 4)
            }
            val entries = sectorSize / 4
            val table = IntArray(fatSectors.size * entries)
            fatSectors.forEachIndexed { index, sector ->
                val off = sectorOffset(sector)
                for (i in 0 until entries) {
                    if (off + i * 4 + 4 <= bytes.size) table[index * entries + i] = buf.getInt(off + i * 4)
                }
            }
            return table
        }

        private fun chain(start: Int, table: IntArray): List<Int> {
            val result = mutableListOf<Int>()
            var current = start
            while (current >= 0 && current < table.size && result.size < table.size) {
                result += current
                current = table[current]
            }
            return result
        }

        private fun readChain(start: Int): ByteArray {
            val sectors = chain(start, fat)
            val out = java.io.ByteArrayOutputStream(sectors.size * sectorSize)
            sectors.forEach { sector ->
                val off = sectorOffset(sector)
                out.write(bytes, off, minOf(sectorSize, bytes.size - off).coerceAtLeast(0))
            }
            return out.toByteArray()
        }

        fun stream(name: String): ByteArray? {
            val directory = ByteBuffer.wrap(readChain(buf.getInt(0x30))).order(ByteOrder.LITTLE_ENDIAN)
            val entryCount = directory.capacity() / 128
            var rootStart = -1
            var rootSize = 0L
            for (i in 0 until entryCount) {
                val base = i * 128
                val nameLength = directory.getShort(base + 0x40).toInt()
                if (nameLength <= 2) continue
                val entryName = String(directory.array(), base, nameLength - 2, Charsets.UTF_16LE)
                val type = directory.get(base + 0x42).toInt()
                val start = directory.getInt(base + 0x74)
                val size = directory.getInt(base + 0x78).toLong() and 0xFFFFFFFFL
                if (type == 5) {
                    rootStart = start
                    rootSize = size
                }
                if (type == 2 && entryName == name) {
                    return if (size < miniCutoff) readMini(start, size, rootStart, rootSize) else readChain(start).copyOf(size.toInt())
                }
            }
            return null
        }

        private fun readMini(start: Int, size: Long, rootStart: Int, rootSize: Long): ByteArray {
            val miniStream = readChain(rootStart).copyOf(rootSize.toInt())
            val miniFatStart = buf.getInt(0x3C)
            val miniFatBytes = if (miniFatStart >= 0) readChain(miniFatStart) else ByteArray(0)
            val miniFatBuf = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN)
            val miniFat = IntArray(miniFatBytes.size / 4) { miniFatBuf.getInt(it * 4) }
            val out = java.io.ByteArrayOutputStream(size.toInt())
            chain(start, miniFat).forEach { sector ->
                val off = sector * miniSectorSize
                out.write(miniStream, off, minOf(miniSectorSize, miniStream.size - off).coerceAtLeast(0))
            }
            return out.toByteArray().copyOf(size.toInt())
        }
    }

    // endregion

    private val OLE_SIGNATURE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
}
