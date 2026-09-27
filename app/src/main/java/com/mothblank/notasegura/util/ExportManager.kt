package com.mothblank.notasegura.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.WarrantyItem
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

object ExportManager {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM = 802f

    fun createPdf(
        context: Context,
        warranties: List<WarrantyItem>,
        payments: List<Payment>
    ): File? {
        val document = PdfDocument()
        val writer = PdfWriter(document)
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        return try {
            writer.drawHeading("Relatório NotaSegura")

            writer.drawSection("Garantias e documentos")
            if (warranties.isEmpty()) {
                writer.drawBody("Nenhuma garantia cadastrada.")
            } else {
                warranties.forEach { item ->
                    val attachment = if (item.imagePath != null) " • documento anexado" else ""
                    writer.drawBody(
                        "${item.name} • ${item.category.ifBlank { "Sem categoria" }} • " +
                            "compra ${item.purchaseDate.format(dateFormatter)} • " +
                            "garantia até ${item.expirationDate.format(dateFormatter)}$attachment"
                    )
                }
            }

            writer.addSpacing(14f)
            writer.drawSection("Pagamentos")
            if (payments.isEmpty()) {
                writer.drawBody("Nenhum pagamento cadastrado.")
            } else {
                payments.forEach { payment ->
                    val status = when {
                        payment.isPaid && payment.paidAt != null ->
                            "Pago em ${payment.paidAt.format(dateFormatter)}"
                        payment.isPaid -> "Pago"
                        else -> "Pendente"
                    }
                    val recurrence = if (payment.recurrenceMonths != null) " • recorrente" else ""
                    writer.drawBody(
                        "$status • ${payment.title} • ${CurrencyUtils.formatCents(payment.amountCents)} • " +
                            "vence ${payment.dueDate.format(dateFormatter)}$recurrence"
                    )
                }
            }

            writer.finish()

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "Relatorio_NotaSegura_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { output -> document.writeTo(output) }
            file
        } catch (_: Exception) {
            null
        } finally {
            document.close()
        }
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
    }

    private class PdfWriter(
        private val document: PdfDocument
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = MARGIN

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }

        init {
            startPage()
        }

        fun drawHeading(text: String) {
            ensureSpace(42f)
            paint.textSize = 24f
            paint.isFakeBoldText = true
            paint.color = Color.BLACK
            canvas?.drawText(text, MARGIN, y, paint)
            y += 42f
        }

        fun drawSection(text: String) {
            ensureSpace(34f)
            paint.textSize = 18f
            paint.isFakeBoldText = true
            paint.color = Color.rgb(13, 71, 161)
            canvas?.drawText(text, MARGIN, y, paint)
            y += 30f
        }

        fun drawBody(text: String) {
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = Color.BLACK
            drawWrapped(text)
            y += 5f
        }

        fun addSpacing(space: Float) {
            ensureSpace(space)
            y += space
        }

        fun finish() {
            page?.let(document::finishPage)
            page = null
            canvas = null
        }

        private fun drawWrapped(text: String) {
            var remaining = text.trim()
            val maxWidth = PAGE_WIDTH - (MARGIN * 2)
            val lineHeight = 18f

            while (remaining.isNotEmpty()) {
                ensureSpace(lineHeight)
                val measured = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                var end = measured
                if (measured < remaining.length) {
                    val whitespace = remaining.lastIndexOf(' ', measured - 1)
                    if (whitespace > 0) end = whitespace + 1
                }

                val line = remaining.substring(0, end).trimEnd()
                canvas?.drawText(line, MARGIN, y, paint)
                y += lineHeight
                remaining = remaining.substring(end).trimStart()
            }
        }

        private fun ensureSpace(required: Float) {
            if (y + required <= BOTTOM) return
            page?.let(document::finishPage)
            startPage()
        }

        private fun startPage() {
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH,
                PAGE_HEIGHT,
                pageNumber
            ).create()
            page = document.startPage(pageInfo)
            canvas = page?.canvas
            y = MARGIN
        }
    }
}
