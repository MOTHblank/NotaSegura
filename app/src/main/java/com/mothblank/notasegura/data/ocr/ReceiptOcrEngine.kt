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
import com.mothblank.notasegura.util.OcrConfidence
import com.mothblank.notasegura.util.OcrTextRegion
import com.mothblank.notasegura.util.ReceiptOcrParser
import com.mothblank.notasegura.util.ReceiptOcrSuggestions
import com.mothblank.notasegura.util.ReceiptPriceAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ReceiptImageScan(
    val text: String,
    val suggestions: ReceiptOcrSuggestions
)

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
                extractBitmapText(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Kept for existing integrations that want OCR geometry without recovery.
     * Receipt capture uses recognizeReceipt() to enable bounded adaptive passes.
     */
    suspend fun recognizeLines(imageFile: File): List<OcrTextRegion> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(imageFile.isFile && imageFile.length() > 0L)
                val bitmap = decodeForOcr(imageFile)
                try {
                    recognizeBitmapLines(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }

    /**
     * Read the untouched photo first. Only uncertain scans can use a validated
     * document perspective correction, then gentle contrast enhancement and
     * at most one monetary detail crop. Conflicting readings cannot silently
     * increase price confidence.
     */
    suspend fun recognizeReceipt(imageFile: File): ReceiptImageScan =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                require(imageFile.isFile && imageFile.length() > 0L)
                val original = decodeForOcr(imageFile)
                try {
                    val baseline = recognizeBitmapLines(original)
                    var chosen = baseline
                    // At most one derived full-frame bitmap is retained. Its
                    // OCR boxes are measured in that bitmap's own coordinates.
                    var selectedVariant: Bitmap? = null
                    var conflicting = false

                    try {
                        if (ReceiptRecoveryPolicy.shouldEnhance(baseline)) {
                            val rectified = ReceiptDocumentRectifier(appContext)
                                .rectifyIfConfident(original)
                            if (rectified != null) {
                                var keepRectified = false
                                try {
                                    val alternative = recognizeBitmapLines(rectified)
                                    if (ReceiptRecoveryPolicy.contradict(chosen, alternative)) {
                                        conflicting = true
                                    } else if (
                                        ReceiptRecoveryPolicy.chooseScan(chosen, alternative) ===
                                        alternative
                                    ) {
                                        chosen = alternative
                                        selectedVariant = rectified
                                        keepRectified = true
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // Optional correction cannot block attachment import.
                                } finally {
                                    if (!keepRectified) rectified.recycle()
                                }
                            }

                            if (ReceiptRecoveryPolicy.shouldEnhance(chosen)) {
                                val source = selectedVariant ?: original
                                val enhanced = ReceiptImagePreprocessor(appContext)
                                    .enhanceContrast(source)
                                if (enhanced != null) {
                                    var keepEnhanced = false
                                    try {
                                        val alternative = recognizeBitmapLines(enhanced)
                                        if (ReceiptRecoveryPolicy.contradict(chosen, alternative)) {
                                            conflicting = true
                                        } else if (
                                            ReceiptRecoveryPolicy.chooseScan(chosen, alternative) ===
                                            alternative
                                        ) {
                                            chosen = alternative
                                            keepEnhanced = true
                                            selectedVariant?.recycle()
                                            selectedVariant = enhanced
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        // Prefer the existing OCR result on failure.
                                    } finally {
                                        if (!keepEnhanced) enhanced.recycle()
                                    }
                                }
                            }
                        }

                        var parsed = ReceiptOcrParser.parse(chosen)
                        val firstPrice = ReceiptPriceAnalyzer.findTotal(chosen)
                        val referenceImage = selectedVariant ?: original
                        val plan = ReceiptRecoveryPolicy.proposeCrop(
                            chosen, referenceImage.width, referenceImage.height
                        )

                        if (plan != null) {
                            try {
                                val details = recognizePriceCrop(
                                    imageFile,
                                    referenceImage,
                                    plan,
                                    useOriginalDetailDecode = selectedVariant == null
                                )
                                val reread = ReceiptRecoveryPolicy.isolatedPrice(details)
                                if (reread != null) {
                                    val originalAmount = firstPrice?.amountCents
                                    val rereadConfidence =
                                        reread.evidence.recognitionConfidence ?: 0f

                                    if (originalAmount != null &&
                                        originalAmount != reread.amountCents
                                    ) {
                                        conflicting = true
                                    } else if (originalAmount == reread.amountCents &&
                                        !conflicting &&
                                        firstPrice != null &&
                                        firstPrice.evidence.heuristicScore >= 12 &&
                                        rereadConfidence >= 0.92f
                                    ) {
                                        // Agreement between independent full and cropped
                                        // OCR passes can upgrade an uncertain digit.
                                        val confirmed = firstPrice.evidence.copy(
                                            confidence = OcrConfidence.HIGH,
                                            recognitionConfidence = rereadConfidence
                                        )
                                        parsed = parsed.copy(
                                            evidence = parsed.evidence +
                                                ("purchaseValueCents" to confirmed)
                                        )
                                    } else if (originalAmount == null &&
                                        rereadConfidence >= 0.85f
                                    ) {
                                        // A recovered but previously unread number
                                        // stays a manual suggestion, never autofill.
                                        val cautious = reread.evidence.copy(
                                            confidence = OcrConfidence.LOW,
                                            sourceLine = "Releitura: " +
                                                reread.evidence.sourceLine
                                        )
                                        parsed = parsed.copy(
                                            purchaseValueCents = reread.amountCents,
                                            evidence = parsed.evidence +
                                                ("purchaseValueCents" to cautious)
                                        )
                                    }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // High-resolution retries are optional; preserve the
                                // original detected fields if the crop fails.
                            }
                        }

                        if (conflicting) {
                            val evidence = parsed.evidence["purchaseValueCents"]
                            if (evidence != null) {
                                parsed = parsed.copy(
                                    evidence = parsed.evidence +
                                        ("purchaseValueCents" to evidence.copy(
                                            confidence = OcrConfidence.LOW
                                        ))
                                )
                            }
                        }
                        ReceiptImageScan(
                            text = chosen.joinToString("\n") { it.text },
                            suggestions = parsed
                        )
                    } finally {
                        selectedVariant?.recycle()
                    }
                } finally {
                    original.recycle()
                }
            }
        }

    private suspend fun recognizePriceCrop(
        imageFile: File,
        referenceImage: Bitmap,
        plan: ReceiptCrop,
        useOriginalDetailDecode: Boolean
    ): List<OcrTextRegion> {
        // For the original view we may decode additional camera detail.
        // Rectified or enhanced OCR boxes cannot be mapped onto that decode:
        // crop directly in the selected variant's coordinate space instead.
        val highResolution = if (useOriginalDetailDecode) {
            decodeForOcr(imageFile, MAX_DETAIL_DECODE_SIDE)
        } else {
            referenceImage
        }
        try {
            val scaleX = highResolution.width.toFloat() / referenceImage.width
            val scaleY = highResolution.height.toFloat() / referenceImage.height
            val left = (plan.left * scaleX).toInt().coerceIn(
                0, highResolution.width - 1
            )
            val top = (plan.top * scaleY).toInt().coerceIn(
                0, highResolution.height - 1
            )
            val right = (plan.right * scaleX).toInt().coerceIn(
                left + 1, highResolution.width
            )
            val bottom = (plan.bottom * scaleY).toInt().coerceIn(
                top + 1, highResolution.height
            )
            val crop = Bitmap.createBitmap(
                highResolution, left, top, right - left, bottom - top
            )
            val prepared = try {
                // Keep a cropped region within the detector's 2048px limit.
                // Small crops can be enlarged for recognition without loading
                // or rescanning the rest of the high-resolution photo.
                val longest = maxOf(crop.width, crop.height)
                when {
                    longest > MAX_DECODE_SIDE ->
                        scaleToMaximumSide(crop, MAX_DECODE_SIDE)
                    longest < MIN_DETAIL_SIDE -> {
                        val factor = MIN_DETAIL_SIDE.toFloat() / longest
                        Bitmap.createScaledBitmap(
                            crop,
                            (crop.width * factor).toInt().coerceAtLeast(1),
                            (crop.height * factor).toInt().coerceAtLeast(1),
                            true
                        )
                    }
                    else -> crop
                }
            } catch (error: Throwable) {
                crop.recycle()
                throw error
            }
            if (prepared !== crop) crop.recycle()
            try {
                return recognizeBitmapLines(prepared)
            } finally {
                prepared.recycle()
            }
        } finally {
            if (highResolution !== referenceImage) highResolution.recycle()
        }
    }

    private suspend fun recognizeBitmapLines(bitmap: Bitmap): List<OcrTextRegion> =
        getOrCreateEngine().recognize(bitmap).results.mapIndexedNotNull { index, item ->
            val text = item.text.trim()
            if (text.isBlank()) return@mapIndexedNotNull null
            val points = item.box.points
            OcrTextRegion(
                text = text,
                lineNumber = index + 1,
                recognitionConfidence = item.confidence
                    .takeIf { it.isFinite() }
                    ?.coerceIn(0f, 1f),
                left = points.minOf { it.x },
                top = points.minOf { it.y },
                right = points.maxOf { it.x },
                bottom = points.maxOf { it.y }
            )
        }

    /** Caller retains ownership of bitmaps rendered from PDF pages. */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        mutex.withLock { extractBitmapText(bitmap) }
    }

    suspend fun recognizePdf(pdfFile: File, scanImagePages: Boolean = false): String =
        PdfAttachmentTextExtractor(appContext, this).extract(pdfFile, scanImagePages)

    private suspend fun extractBitmapText(bitmap: Bitmap): String =
        getOrCreateEngine().recognize(bitmap).results
            .asSequence()
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

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

    private fun decodeForOcr(file: File, maximumSide: Int = MAX_DECODE_SIDE): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Não foi possível decodificar a imagem para OCR."
        }

        var sampleSize = 1
        val longestInput = maxOf(bounds.outWidth, bounds.outHeight)
        while (
            longestInput / (sampleSize * 2) >= maximumSide ||
            (maximumSide > MAX_DECODE_SIDE &&
                longestInput / sampleSize > maximumSide + 1024)
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

        val scaled = scaleToMaximumSide(decoded, maximumSide)
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
        private const val MAX_DETAIL_DECODE_SIDE = 3200
        private const val MIN_DETAIL_SIDE = 1500
    }
}
