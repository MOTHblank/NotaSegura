package com.mothblank.notasegura.util

import kotlin.math.max
import kotlin.math.min

/** QR bounds in the same bitmap coordinate space as OCR detection boxes. */
data class ReceiptQrBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right > left && bottom > top)
    }
}

/**
 * Keeps text useful for search while excluding QR / fiscal access-key noise
 * from purchasing suggestions. A verified QR region is an additional signal;
 * lexical guards still work when a code is blurred or cannot be detected.
 */
object ReceiptQrNoiseFilter {
    private val metadata = Regex(
        """(?i)(?:https?://|www\.|\bQR[\s-]*CODE\b|\bC[ÓO]DIGO\s+QR\b|\bCHAVE\s+DE\s+ACESSO\b|\bPIX\s+COPIA\s+E\s+COLA\b|\bCONSULTE\s+(?:PELA|O)\b|\bPORTAL\s+DA\s+NOTA\s+FISCAL\b)"""
    )
    private val qrClue = Regex(
        """(?i)\b(?:QR[\s-]*CODE|C[ÓO]DIGO\s+QR|CHAVE\s+DE\s+ACESSO|NFC[\s-]?E)\b"""
    )
    private val longDigits = Regex("""\d{30,}""")

    fun isMetadataLine(text: String): Boolean {
        if (metadata.containsMatchIn(text)) return true
        val compact = text.filterNot(Char::isWhitespace)
        // Do not allow long fiscal keys or random QR payloads to be mistaken
        // for money with misplaced decimal separators.
        if (longDigits.containsMatchIn(compact)) return true
        return compact.length >= 40 &&
            compact.count(Char::isDigit) >= 30 &&
            compact.count(Char::isLetter) <= 10
    }

    fun shouldDetectQr(regions: List<OcrTextRegion>): Boolean =
        regions.any { qrClue.containsMatchIn(it.text) || isMetadataLine(it.text) }

    fun eligible(regions: List<OcrTextRegion>): List<OcrTextRegion> =
        regions.filterNot { it.isQrNoise || isMetadataLine(it.text) }

    fun mark(regions: List<OcrTextRegion>, qr: ReceiptQrBounds?): List<OcrTextRegion> {
        if (qr == null) return regions.map {
            if (isMetadataLine(it.text)) it.copy(isQrNoise = true) else it
        }
        return regions.map { region ->
            if (isMetadataLine(region.text) || overlapsCode(region, qr)) {
                region.copy(isQrNoise = true)
            } else region
        }
    }

    private fun overlapsCode(region: OcrTextRegion, qr: ReceiptQrBounds): Boolean {
        val l = region.left ?: return false
        val t = region.top ?: return false
        val r = region.right ?: return false
        val b = region.bottom ?: return false
        if (r <= l || b <= t) return false
        val overlapWidth = max(0f, min(r, qr.right) - max(l, qr.left))
        val overlapHeight = max(0f, min(b, qr.bottom) - max(t, qr.top))
        return overlapWidth * overlapHeight / ((r - l) * (b - t)) >= 0.65f
    }
}
