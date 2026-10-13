package com.umit.budgettracker.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Turns a user-picked file into plain text, entirely on device:
 * images → ML Kit OCR (bundled Latin model, no network), PDF → text layer is not available on
 * Android so pages are rendered and OCR'd, XLSX → first sheet as tab-separated rows,
 * CSV/TXT → read as is. Nothing is written to storage.
 */
@Singleton
class DocumentTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extract(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = uri.lastPathSegment.orEmpty().lowercase()
        when {
            mime.startsWith("image/") -> recognize(loadBitmap(uri))
            mime == "application/pdf" || name.endsWith(".pdf") -> extractPdf(uri)
            mime.contains("spreadsheetml") || name.endsWith(".xlsx") -> extractXlsx(uri)
            else -> context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                // Turkish bank CSVs are often Windows-1254; fall back when UTF-8 shows replacement chars.
                val utf8 = String(bytes, Charsets.UTF_8)
                if (utf8.contains('�')) String(bytes, charset("windows-1254")) else utf8
            }.orEmpty()
        }
    }

    suspend fun recognizeImage(uri: Uri): String = withContext(Dispatchers.IO) { recognize(loadBitmap(uri)) }

    private fun loadBitmap(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }

    /** OCR lines in reading order: blocks sorted top-to-bottom, then elements joined per line. */
    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / ROW_BUCKET_PX }, { it.boundingBox?.left ?: 0 }))
                // Lines that share a row (table columns) are joined so a row reads as one line.
                val rows = lines.groupBy { (it.boundingBox?.centerY() ?: 0) / ROW_BUCKET_PX }
                    .toSortedMap()
                    .values
                    .joinToString("\n") { row -> row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString("  ") { it.text } }
                continuation.resume(rows)
            }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private suspend fun extractPdf(uri: Uri): String {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                buildString {
                    for (index in 0 until pages) {
                        val bitmap = renderer.openPage(index).use { page ->
                            val scale = PDF_RENDER_WIDTH.toFloat() / page.width
                            Bitmap.createBitmap(PDF_RENDER_WIDTH, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        appendLine(recognize(bitmap))
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /** Minimal XLSX reader: shared strings + first worksheet, cells joined with tabs. */
    private fun extractXlsx(uri: Uri): String {
        var sharedStrings: List<String> = emptyList()
        var sheetXml: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(zip.readBytes())
                        entry.name == "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val sheet = sheetXml ?: return ""
        val parser = Xml.newPullParser().apply { setInput(sheet.inputStream(), "UTF-8") }
        val rows = mutableListOf<String>()
        var row = mutableListOf<String>()
        var cellType: String? = null
        var inValue = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableListOf()
                    "c" -> cellType = parser.getAttributeValue(null, "t")
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) {
                    val text = parser.text
                    row += if (cellType == "s") sharedStrings.getOrElse(text.trim().toIntOrNull() ?: -1) { text } else text
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "row" -> rows += row.joinToString("\t")
                }
            }
        }
        return rows.joinToString("\n")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
        val strings = mutableListOf<String>()
        var current = StringBuilder()
        var inText = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inText = false
                    "si" -> strings += current.toString()
                }
            }
        }
        return strings
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 2400
        const val MAX_PDF_PAGES = 8
        const val PDF_RENDER_WIDTH = 1700
        const val ROW_BUCKET_PX = 18
    }
}
