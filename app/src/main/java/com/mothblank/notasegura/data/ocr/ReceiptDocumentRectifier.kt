package com.mothblank.notasegura.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Conservative document-edge detection and perspective correction for receipts.
 *
 * Works on a downscaled preview, requires four visible convex corners, then
 * transforms the already bounded OCR bitmap. Always returns a separate bitmap;
 * the caller retains ownership of both it and the original.
 */
internal class ReceiptDocumentRectifier(context: Context) {
    private val appContext = context.applicationContext

    fun rectifyIfConfident(original: Bitmap): Bitmap? {
        if (!OpenCVUtils.init(appContext)) return null
        if (original.width < MIN_IMAGE_SIDE || original.height < MIN_IMAGE_SIDE) return null

        val rgba = Mat()
        val preview = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        val kernel = Mat()
        val contours = mutableListOf<MatOfPoint>()
        var transformed: Bitmap? = null

        return try {
            Utils.bitmapToMat(original, rgba)

            val scale = min(
                1.0,
                PREVIEW_MAX_SIDE.toDouble() / max(original.width, original.height)
            )
            val previewWidth = (original.width * scale).roundToInt().coerceAtLeast(1)
            val previewHeight = (original.height * scale).roundToInt().coerceAtLeast(1)
            Imgproc.resize(
                rgba, preview, Size(previewWidth.toDouble(), previewHeight.toDouble()),
                0.0, 0.0, if (scale < 1.0) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            )
            Imgproc.cvtColor(preview, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 65.0, 170.0)

            val closing = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, Size(5.0, 5.0)
            )
            try {
                closing.copyTo(kernel)
            } finally {
                closing.release()
            }
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(
                closed, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val previewArea = previewWidth.toDouble() * previewHeight
            var best: ReceiptPaperQuad? = null
            for (contour in contours) {
                // Short-circuit small edges and text components before polygon fitting.
                val area = Imgproc.contourArea(contour)
                if (area < previewArea * MIN_CONTOUR_AREA ||
                    area > previewArea * MAX_CONTOUR_AREA
                ) continue

                val floating = MatOfPoint2f(*contour.toArray())
                val polygon = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(floating, true)
                    Imgproc.approxPolyDP(floating, polygon, perimeter * 0.025, true)
                    if (polygon.total() != 4L) continue

                    val points = polygon.toArray().map { PaperCorner(it.x, it.y) }
                    val evaluated = ReceiptDocumentGeometry.assess(
                        points, previewWidth, previewHeight
                    ) ?: continue
                    if (best == null ||
                        evaluated.coverage * evaluated.rectangularity >
                        best.coverage * best.rectangularity
                    ) {
                        best = evaluated
                    }
                } finally {
                    polygon.release()
                    floating.release()
                }
            }

            val selected = best ?: return null
            val sourceCorners = selected.corners.map {
                Point(it.x * original.width / previewWidth,
                      it.y * original.height / previewHeight)
            }
            val rawWidth = selected.width * original.width / previewWidth
            val rawHeight = selected.height * original.height / previewHeight
            val outputScale = min(1.0, MAX_OUTPUT_SIDE / max(rawWidth, rawHeight))
            val outputWidth = (rawWidth * outputScale).roundToInt().coerceAtLeast(1)
            val outputHeight = (rawHeight * outputScale).roundToInt().coerceAtLeast(1)
            if (outputWidth < MIN_OUTPUT_SIDE || outputHeight < MIN_OUTPUT_SIDE) return null

            val source = MatOfPoint2f(*sourceCorners.toTypedArray())
            val destination = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((outputWidth - 1).toDouble(), 0.0),
                Point((outputWidth - 1).toDouble(), (outputHeight - 1).toDouble()),
                Point(0.0, (outputHeight - 1).toDouble())
            )
            val transform = Mat()
            val corrected = Mat()
            try {
                val matrix = Imgproc.getPerspectiveTransform(source, destination)
                try {
                    matrix.copyTo(transform)
                } finally {
                    matrix.release()
                }
                Imgproc.warpPerspective(
                    rgba,
                    corrected,
                    transform,
                    Size(outputWidth.toDouble(), outputHeight.toDouble()),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    Scalar(255.0, 255.0, 255.0, 255.0)
                )
                transformed = Bitmap.createBitmap(
                    outputWidth, outputHeight, Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(corrected, checkNotNull(transformed))
                transformed
            } finally {
                corrected.release()
                transform.release()
                destination.release()
                source.release()
            }
        } catch (_: Exception) {
            transformed?.recycle()
            null
        } finally {
            contours.forEach { it.release() }
            kernel.release()
            hierarchy.release()
            closed.release()
            edges.release()
            blurred.release()
            gray.release()
            preview.release()
            rgba.release()
        }
    }

    private companion object {
        const val PREVIEW_MAX_SIDE = 960
        const val MAX_OUTPUT_SIDE = 2048.0
        const val MIN_IMAGE_SIDE = 300
        const val MIN_OUTPUT_SIDE = 180
        const val MIN_CONTOUR_AREA = 0.20
        const val MAX_CONTOUR_AREA = 0.90
    }
}
