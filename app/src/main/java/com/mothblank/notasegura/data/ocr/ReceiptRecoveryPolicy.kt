package com.mothblank.notasegura.data.ocr

import com.mothblank.notasegura.util.OcrConfidence
import com.mothblank.notasegura.util.OcrTextRegion
import com.mothblank.notasegura.util.ReceiptQrNoiseFilter
import com.mothblank.notasegura.util.ReceiptOcrParser
import com.mothblank.notasegura.util.ReceiptPriceAnalyzer
import com.mothblank.notasegura.util.ReceiptPriceCandidate
import kotlin.math.max
import kotlin.math.min

internal data class ReceiptCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Conservative selection rules: avoid extra OCR on clear photos, prefer strong
 * evidence over text volume, and never silently trust contradictory readings.
 */
internal object ReceiptRecoveryPolicy {
    private val strongTotalLabel = Regex(
        """(?i)\b(?:TOTAL\s+A\s+PAGAR|VALOR\s+TOTAL|TOTAL\s+GERAL|TOTAL\s+DA\s+COMPRA|VALOR\s+A\s+PAGAR)\b"""
    )
    private val forbiddenLabel = Regex(
        """(?i)\b(?:SUB\s*TOTAL|TOTAL\s+(?:DE|DOS|DAS)\s+(?:ITENS?|PRODUTOS?|TRIBUTOS?|IMPOSTOS?)|TROCO|DESCONTO|VALOR\s+PAGO|PIX|IMPOSTOS?)\b"""
    )

    fun shouldEnhance(regions: List<OcrTextRegion>): Boolean {
        val price = ReceiptPriceAnalyzer.findTotal(regions)
        if (price?.evidence?.confidence != OcrConfidence.HIGH) return true
        val readable = ReceiptQrNoiseFilter.eligible(regions).mapNotNull { it.recognitionConfidence }
        return readable.isNotEmpty() && readable.average() < 0.80
    }

    fun chooseScan(
        original: List<OcrTextRegion>,
        enhanced: List<OcrTextRegion>
    ): List<OcrTextRegion> {
        if (enhanced.isEmpty()) return original
        if (original.isEmpty()) return enhanced
        return if (score(enhanced) > score(original) + 3.0) enhanced else original
    }

    fun contradict(
        original: List<OcrTextRegion>,
        enhanced: List<OcrTextRegion>
    ): Boolean {
        val first = ReceiptPriceAnalyzer.findTotal(original)?.amountCents
        val second = ReceiptPriceAnalyzer.findTotal(enhanced)?.amountCents
        return first != null && second != null && first != second
    }

    private fun score(regions: List<OcrTextRegion>): Double {
        val parsed = ReceiptOcrParser.parse(regions)
        val priceWeight = when (parsed.evidence["purchaseValueCents"]?.confidence) {
            OcrConfidence.HIGH -> 45.0
            OcrConfidence.MEDIUM -> 20.0
            OcrConfidence.LOW -> 6.0
            null -> 0.0
        }
        val clean = ReceiptQrNoiseFilter.eligible(regions)
        val confidence = clean.mapNotNull { it.recognitionConfidence }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        return priceWeight + confidence * 30.0 +
            min(clean.count { it.text.isNotBlank() }, 25) * 0.3
    }

    /**
     * A tightly bounded crop around an uncertain amount, or around a strong
     * total label whose amount was not read. Coordinates refer to the unchanged
     * full-frame image, so they can also be scaled to a detail decode.
     */
    fun proposeCrop(
        regions: List<OcrTextRegion>,
        width: Int,
        height: Int
    ): ReceiptCrop? {
        val price = ReceiptPriceAnalyzer.findTotal(regions)
        if (price?.evidence?.confidence == OcrConfidence.HIGH) return null
        val clean = ReceiptQrNoiseFilter.eligible(regions)

        val amountRegion = price?.let { candidate ->
            clean.firstOrNull {
                it.lineNumber == candidate.evidence.lineNumber &&
                    it.left != null && it.right != null &&
                    it.top != null && it.bottom != null
            }
        }
        val label = clean.firstOrNull { region ->
            strongTotalLabel.containsMatchIn(region.text) &&
                !forbiddenLabel.containsMatchIn(region.text) &&
                region.left != null && region.right != null &&
                region.top != null && region.bottom != null
        } ?: return null

        val focus = amountRegion ?: label
        val top = focus.top ?: return null
        val bottom = focus.bottom ?: return null
        val left = focus.left ?: return null
        val right = focus.right ?: return null
        val lineHeight = max(bottom - top, 14f)

        val cropLeft = if (amountRegion != null) left - lineHeight * 2.5f else left - lineHeight
        val cropRight = if (amountRegion != null) right + lineHeight * 2.5f else width.toFloat()
        val cropTop = top - lineHeight * 1.5f
        val cropBottom = bottom + lineHeight * 2.0f

        val result = ReceiptCrop(
            cropLeft.coerceIn(0f, width.toFloat()),
            cropTop.coerceIn(0f, height.toFloat()),
            cropRight.coerceIn(0f, width.toFloat()),
            cropBottom.coerceIn(0f, height.toFloat())
        )
        return result.takeIf {
            it.right - it.left >= 24f && it.bottom - it.top >= 12f
        }
    }

    /**
     * Read at most one distinct value from a cropped region. An isolated amount
     * is not automatically trustworthy: its surrounding label must have been
     * established in the initial full-frame read.
     */
    fun isolatedPrice(regions: List<OcrTextRegion>): ReceiptPriceCandidate? {
        val options = ReceiptQrNoiseFilter.eligible(regions).mapNotNull { region ->
            val fauxLabel = OcrTextRegion("TOTAL A PAGAR", 0)
            ReceiptPriceAnalyzer.findTotal(
                listOf(fauxLabel, region.copy(
                    lineNumber = 1,
                    left = null,
                    top = null,
                    right = null,
                    bottom = null
                ))
            )
        }.distinctBy { it.amountCents }
        return options.singleOrNull()
    }
}
