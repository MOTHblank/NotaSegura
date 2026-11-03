package com.mothblank.notasegura.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Offline, bounded text extraction. Only a single PDF page is processed at a
 * time and raster OCR is opt-in; the original document is never modified.
 */
internal class PdfAttachmentTextExtractor(
    private val context: Context,
    private val ocrEngine: ReceiptOcrEngine
) {
    suspend fun extract(file: File, scanImagePages: Boolean): String = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0L) { "PDF ausente ou vazio." }
        PDFBoxResourceLoader.init(context.applicationContext)
        val searchableText = StringBuilder()
        var scannedPages = 0
        var pdfPages: PdfPageImages? = null
        try {
            val memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            PDDocument.load(file, memory).use { pdf ->
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                for (index in 0 until pdf.numberOfPages.coerceAtMost(MAX_PDF_PAGES)) {
                    currentCoroutineContext().ensureActive()
                    if (searchableText.length >= MAX_INDEXED_CHARACTERS) break

                    stripper.startPage = index + 1
                    stripper.endPage = index + 1
                    val embedded = stripper.getText(pdf).trim()
                    appendBounded(searchableText, embedded)

                    if (
                        scanImagePages &&
                        embedded.count(Char::isLetterOrDigit) < MIN_USEFUL_TEXT_CHARS &&
                        scannedPages < MAX_OCR_PAGES &&
                        searchableText.length < MAX_INDEXED_CHARACTERS
                    ) {
                        val pages = pdfPages ?: PdfPageImages(file).also { pdfPages = it }
                        val scannedText = try {
                            pages.recognizePage(index, ocrEngine)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // An unrenderable scanned page should not discard selectable text.
                            ""
                        }
                        appendBounded(searchableText, scannedText.trim())
                        scannedPages++
                    }
                }
            }
        } finally {
            pdfPages?.close()
        }
        searchableText.toString()
    }

    private fun appendBounded(destination: StringBuilder, text: String) {
        if (text.isBlank()) return
        if (destination.length >= MAX_INDEXED_CHARACTERS) return
        if (destination.isNotEmpty()) destination.append('\n')
        val remaining = MAX_INDEXED_CHARACTERS - destination.length
        destination.append(text, 0, text.length.coerceAtMost(remaining))
    }

    private class PdfPageImages(file: File) : Closeable {
        private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        private val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }

        suspend fun recognizePage(index: Int, ocrEngine: ReceiptOcrEngine): String {
            if (index >= renderer.pageCount) return ""
            renderer.openPage(index).use { page ->
                require(page.width > 0 && page.height > 0)
                val scale = minOf(
                    2f,
                    MAX_RENDER_SIDE.toFloat() / page.width,
                    MAX_RENDER_SIDE.toFloat() / page.height
                )
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return ocrEngine.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }

        override fun close() {
            try {
                renderer.close()
            } finally {
                descriptor.close()
            }
        }
    }

    private companion object {
        const val MAX_PDF_PAGES = 500
        const val MAX_OCR_PAGES = 24
        const val MAX_INDEXED_CHARACTERS = 96_000
        const val MAX_RENDER_SIDE = 2048
        const val MIN_USEFUL_TEXT_CHARS = 12
    }
}
