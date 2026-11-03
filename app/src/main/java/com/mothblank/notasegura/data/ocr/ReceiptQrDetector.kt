package com.mothblank.notasegura.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.mothblank.notasegura.util.ReceiptQrBounds
import com.paddle.ocr.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector
import kotlin.math.max
import kotlin.math.min

/**
 * Detects the QR square only; it does not decode, visit, or transmit its URL.
 * Runs on a bounded preview and reports coordinates in the original bitmap.
 * The OCR engine invokes it selectively when the scan suggests a fiscal QR.
 */
internal class ReceiptQrDetector(context: Context) {
    private val appContext = context.applicationContext
    private val detector by lazy { QRCodeDetector() }

    fun findBounds(bitmap: Bitmap): ReceiptQrBounds? {
        if (!OpenCVUtils.init(appContext)) return null
        val rgba = Mat()
        val gray = Mat()
        val preview = Mat()
        val corners = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val factor = min(1.0, PREVIEW_SIDE.toDouble() / max(bitmap.width, bitmap.height))
            val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
            val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
            Imgproc.resize(
                gray, preview, Size(width.toDouble(), height.toDouble()),
                0.0, 0.0, if (factor < 1.0) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            )
            if (!detector.detect(preview, corners) || corners.total() < 4) return null
            val points = (0 until 4).map { i ->
                val values = corners.get(i / corners.cols(), i % corners.cols())
                    ?: return null
                if (values.size < 2) return null
                (values[0] / factor).toFloat() to (values[1] / factor).toFloat()
            }
            val left = points.minOf { it.first }
            val top = points.minOf { it.second }
            val right = points.maxOf { it.first }
            val bottom = points.maxOf { it.second }
            val qrWidth = right - left
            val qrHeight = bottom - top
            if (qrWidth < 32f || qrHeight < 32f) return null
            val aspect = qrWidth / qrHeight
            if (aspect !in 0.65f..1.55f) return null
            if (qrWidth * qrHeight > bitmap.width * bitmap.height * 0.60f) return null
            val pad = max(qrWidth, qrHeight) * 0.025f
            ReceiptQrBounds(
                (left - pad).coerceAtLeast(0f),
                (top - pad).coerceAtLeast(0f),
                (right + pad).coerceAtMost(bitmap.width.toFloat()),
                (bottom + pad).coerceAtMost(bitmap.height.toFloat())
            )
        } catch (_: Exception) {
            // A damaged/rotated QR cannot prevent regular receipt recognition.
            null
        } finally {
            corners.release()
            preview.release()
            gray.release()
            rgba.release()
        }
    }

    private companion object {
        const val PREVIEW_SIDE = 960
    }
}
