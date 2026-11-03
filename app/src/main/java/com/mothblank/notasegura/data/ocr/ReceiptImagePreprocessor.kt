package com.mothblank.notasegura.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.paddle.ocr.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * A gentle, optional local contrast pass for faded thermal paper and uneven lighting.
 *
 * The enhanced image retains the source dimensions, so OCR box coordinates remain
 * comparable to the unmodified scan. Never binarize destructively or overwrite the
 * original document. The caller decides whether the additional pass is worthwhile.
 */
internal class ReceiptImagePreprocessor(context: Context) {
    private val appContext = context.applicationContext

    fun enhanceContrast(original: Bitmap): Bitmap? {
        if (!OpenCVUtils.init(appContext)) return null

        val rgba = Mat()
        val gray = Mat()
        val enhanced = Mat()
        val outputRgba = Mat()
        var output: Bitmap? = null
        return try {
            Utils.bitmapToMat(original, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // CLAHE boosts small faded glyphs without aggressively thresholding
            // gradients, watermarks, or receipt backgrounds.
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(gray, enhanced)
            Imgproc.cvtColor(enhanced, outputRgba, Imgproc.COLOR_GRAY2RGBA)

            output = Bitmap.createBitmap(
                original.width, original.height, Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(outputRgba, checkNotNull(output))
            output
        } catch (_: Exception) {
            output?.recycle()
            null
        } finally {
            outputRgba.release()
            enhanced.release()
            gray.release()
            rgba.release()
        }
    }
}
