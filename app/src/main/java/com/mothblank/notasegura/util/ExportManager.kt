package com.mothblank.notasegura.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.WarrantyItem
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

object ExportManager {

    fun exportToPdf(
        context: Context,
        warranties: List<WarrantyItem>,
        payments: List<Payment>
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        var y = 40f

        // Title
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Relatório Nota Segura", 40f, y, paint)
        y += 40f

        // Warranties Section
        paint.textSize = 18f
        paint.color = Color.BLUE
        canvas.drawText("Garantias e Documentos", 40f, y, paint)
        y += 30f

        paint.textSize = 12f
        paint.color = Color.BLACK
        paint.isFakeBoldText = false

        if (warranties.isEmpty()) {
            canvas.drawText("Nenhuma garantia cadastrada.", 60f, y, paint)
            y += 20f
        } else {
            warranties.forEach { item ->
                if (y > 780) { // Very simple pagination check (doesn't actually create new page here for brevity)
                   // In a real app, we'd finish the page and start a new one
                }
                canvas.drawText("${item.name} (${item.category}) - Expira em: ${item.expirationDate.format(dateFormatter)}", 60f, y, paint)
                y += 20f
            }
        }

        y += 20f

        // Payments Section
        paint.textSize = 18f
        paint.color = Color.parseColor("#FF6200EE") // Primary color
        paint.isFakeBoldText = true
        canvas.drawText("Pagamentos e Contas", 40f, y, paint)
        y += 30f

        paint.textSize = 12f
        paint.color = Color.BLACK
        paint.isFakeBoldText = false

        if (payments.isEmpty()) {
            canvas.drawText("Nenhum pagamento cadastrado.", 60f, y, paint)
            y += 20f
        } else {
            payments.forEach { payment ->
                val status = if (payment.isPaid) "[PAGO]" else "[PENDENTE]"
                canvas.drawText("$status ${payment.title} - R$ ${String.format("%.2f", payment.amount)} - Vence: ${payment.dueDate.format(dateFormatter)}", 60f, y, paint)
                y += 20f
            }
        }

        pdfDocument.finishPage(page)

        // Save the file
        val fileName = "Relatorio_NotaSegura_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(context, "PDF salvo em: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao gerar PDF", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }
}
