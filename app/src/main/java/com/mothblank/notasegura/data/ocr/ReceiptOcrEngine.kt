package com.mothblank.notasegura.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ReceiptOcrEngine(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var engine: PaddleOCR? = null

    suspend fun recognize(imageFile: File): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(imageFile.isFile && imageFile.length() > 0L) {
                "Imagem de OCR ausente ou vazia."
            }

            val bitmap = decodeForOcr(imageFile)
            try {
                val result = getOrCreateEngine().recognize(bitmap)
                result.results
                    .asSequence()
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun getOrCreateEngine(): PaddleOCR {
        engine?.let { return it }

        check(OpenCVUtils.init(appContext)) {
            "Não foi possível inicializar o mecanismo local de OCR."
        }

        return PaddleOCR.create(
            context = appContext,
            config = PaddleOCRConfig(
                detLimitSideLen = 64,
                detLimitType = "min",
                detMaxSideLimit = 2048,
                recScoreThresh = 0.0f,
                recBatchSize = 1
            ),
            engineConfig = EngineConfig(
                numThreads = Runtime.getRuntime()
                    .availableProcessors()
                    .coerceIn(2, 4)
            )
        ).also { engine = it }
    }

    private fun decodeForOcr(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Não foi possível decodificar a imagem para OCR."
        }

        var sampleSize = 1
        while (
            maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >=
            MAX_DECODE_SIDE
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: error("Não foi possível decodificar a imagem para OCR.")

        val scaled = scaleToMaximumSide(decoded, MAX_DECODE_SIDE)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        return applyExifRotation(file, scaled)
    }

    private fun scaleToMaximumSide(bitmap: Bitmap, maximumSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maximumSide) return bitmap

        val scale = maximumSide.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val rotation = runCatching {
            ExifInterface(file.absolutePath).rotationDegrees
        }.getOrDefault(0)

        if (rotation == 0) return bitmap

        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        )
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    companion object {
        private const val MAX_DECODE_SIDE = 2048
    }
}
