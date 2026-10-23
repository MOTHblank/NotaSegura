package com.mothblank.notasegura.util

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.AttachmentType
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.format.DateTimeFormatter

enum class PdfExportFailure {
    GENERATION, STORAGE, DESTINATION
}

sealed interface PdfExportResult {
    data class Success(val file: File) : PdfExportResult
    data class Failure(val reason: PdfExportFailure, val cause: Exception) : PdfExportResult
}

sealed interface PdfShareResult {
    data object Success : PdfShareResult
    data class Failure(val reason: PdfExportFailure, val cause: Exception) : PdfShareResult
}

object ExportManager {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val BOTTOM = 802f

    fun createPdf(
        context: Context,
        purchases: List<PurchaseWithAttachments>,
        payments: List<Payment>,
        includeAttachmentIndex: Boolean = false
    ): PdfExportResult {
        var document: PdfDocument? = null
        var outputFile: File? = null
        var phase = PdfExportFailure.GENERATION
        return try {
            val pdf = PdfDocument()
            document = pdf
            val writer = PdfWriter(pdf)
            val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            writer.drawHeading("Relatório NotaSegura")

            writer.drawSection("Compras e documentos")
            if (purchases.isEmpty()) {
                writer.drawBody("Nenhuma compra cadastrada.")
            } else {
                purchases.forEach { item ->
                    val purchase = item.purchase
                    val details = buildList {
                        add(purchase.productName)
                        purchase.merchant?.let { add(it) }
                        purchase.purchaseValueCents?.let { add(CurrencyUtils.formatCents(it)) }
                        add("compra ${purchase.purchaseDate.format(dateFormatter)}")
                        purchase.warrantyEndDate?.let {
                            add("garantia até ${it.format(dateFormatter)}")
                        }
                        purchase.modelNumber?.let { add("modelo $it") }
                        purchase.serialNumber?.let { add("série $it") }
                        if (item.attachments.isNotEmpty()) {
                            add(
                                if (item.attachments.size == 1) "1 documento"
                                else "${item.attachments.size} documentos"
                            )
                        }
                    }
                    writer.drawBody(details.joinToString(" • "))
                }
            }

            if (includeAttachmentIndex) {
                writer.addSpacing(14f)
                writer.drawSection("Índice de documentos")
                val entries = purchases.flatMap { purchase ->
                    purchase.attachments.map { attachment -> purchase.purchase to attachment }
                }
                if (entries.isEmpty()) {
                    writer.drawBody("Nenhum documento anexado.")
                } else {
                    entries.forEach { (purchase, attachment) ->
                        val type = when (AttachmentType.normalize(attachment.type)) {
                            AttachmentType.RECEIPT -> "Comprovante"
                            AttachmentType.INVOICE -> "Nota fiscal"
                            AttachmentType.WARRANTY -> "Garantia"
                            AttachmentType.MANUAL -> "Manual"
                            else -> "Outro"
                        }
                        val name = attachment.displayName?.takeIf { it.isNotBlank() }
                            ?: File(attachment.filePath).name
                        writer.drawBody(
                            "Arquivo: ${name} • Tipo: ${type} • Compra: " +
                                "${purchase.productName} • ID compra: ${purchase.id} • ID documento: " +
                                "${attachment.id}"
                        )
                    }
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

            phase = PdfExportFailure.DESTINATION
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.isDirectory && !exportDir.mkdirs()) {
                throw IOException("A pasta de exportação não está disponível.")
            }
            phase = PdfExportFailure.STORAGE
            val file = File(exportDir, "Relatorio_NotaSegura_${System.currentTimeMillis()}.pdf")
            outputFile = file
            FileOutputStream(file).use { output -> pdf.writeTo(output) }
            PdfExportResult.Success(file)
        } catch (error: Exception) {
            outputFile?.delete()
            PdfExportResult.Failure(phase, error)
        } finally {
            document?.close()
        }
    }

    fun sharePdf(context: Context, file: File): PdfShareResult {
        return try {
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
            PdfShareResult.Success
        } catch (error: ActivityNotFoundException) {
            PdfShareResult.Failure(PdfExportFailure.DESTINATION, error)
        } catch (error: Exception) {
            PdfShareResult.Failure(PdfExportFailure.STORAGE, error)
        }
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
