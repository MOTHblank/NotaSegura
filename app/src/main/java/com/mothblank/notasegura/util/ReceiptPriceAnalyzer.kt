package com.mothblank.notasegura.util

import kotlin.math.abs
import kotlin.math.max

/**
 * One recognized text fragment, optionally carrying the layout/recognition data
 * provided by PaddleOCR. Plain PDF text uses null geometry and confidence.
 */
data class OcrTextRegion(
    val text: String,
    val lineNumber: Int,
    val recognitionConfidence: Float? = null,
    val left: Float? = null,
    val top: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    val isQrNoise: Boolean = false
)

data class ReceiptPriceCandidate(
    val amountCents: Long,
    val evidence: OcrEvidence
)

/**
 * Selects an amount only when its label provides meaningful evidence.
 * It deliberately abstains when similarly plausible, conflicting totals exist.
 */
object ReceiptPriceAnalyzer {
    private val amountRegex = Regex(
        """(?<!\d)(?:R\$\s*)?(\d+(?:[.,\s]\d{3})*[.,]\d{2}|\d+[.,]\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    // Fiscal receipts can show both a gross item total and a net payable
    // amount after discounts. Prefer the latter when explicitly labelled.
    private val payableLabels = Regex(
        """(?i)\b(?:TOTAL\s+A\s+PAGAR|VALOR\s+A\s+PAGAR|TOTAL\s+L[IÍ]QUIDO|VALOR\s+L[IÍ]QUIDO\s+A\s+PAGAR)\b"""
    )
    private val invoiceLabels = Regex(
        """(?i)\b(?:VALOR\s+TOTAL\s+DA\s+(?:NOTA(?:\s+FISCAL)?|NF[\s-]?E)|TOTAL\s+DA\s+(?:NOTA(?:\s+FISCAL)?|NF[\s-]?E)|VALOR\s+DA\s+NOTA(?:\s+FISCAL)?)\b"""
    )
    private val totalStrongTerms = Regex(
        """(?i)\b(?:VALOR\s+TOTAL|TOTAL\s+GERAL|TOTAL\s+DA\s+(?:COMPRA|VENDA)|TOTAL\s+R\$)\b"""
    )
    private val totalAnyTerm = Regex(
        """(?i)\b(?:TOTAL|VALOR\s+FINAL)\b"""
    )
    private val totalRejectedTerms = Regex(
        """(?i)\b(?:SUB\s*TOTAL|TOTAL\s+(?:DE|DOS|DAS)\s+(?:ITENS?|PRODUTOS?|SERVI[CÇ]OS?|TRIBUTOS?|IMPOSTOS?|DESCONTOS?)|VALOR\s+(?:TOTAL\s+)?(?:DOS?|DAS?)\s+(?:PRODUTOS?|SERVI[CÇ]OS?|ITENS?|TRIBUTOS?|IMPOSTOS?)|DESCONTO|TROCO|ACR[ÉE]SCIMO|VALOR\s+(?:TOTAL\s+)?PAGO|TOTAL\s+PAGO|VALOR\s+RECEBIDO|DINHEIRO|CART[AÃ]O|PIX|IMPOSTO|TRIBUTO|ICMS|ISSQN|PIS|COFINS|BASE\s+DE\s+C[AÁ]LCULO|VALOR\s+UNIT[AÁ]RIO|QUANTIDADE|QTD|PARCELA|TAXA|FRETE|FORMA\s+DE\s+PAGAMENTO)\b"""
    )

    private data class RankedAmount(
        val cents: Long,
        val score: Int,
        val region: OcrTextRegion,
        val source: String,
        val textOnlyAssociation: Boolean
    )

    fun findTotal(regions: List<OcrTextRegion>): ReceiptPriceCandidate? {
        val readable = ReceiptQrNoiseFilter.eligible(regions).filter { it.text.isNotBlank() }
        val candidates = buildList {
            readable.forEachIndexed { index, region ->
                val matches = amountRegex.findAll(region.text).toList()
                if (matches.isEmpty()) return@forEachIndexed

                matches.forEach { match ->
                    val cents = parseCents(match.groupValues[1]) ?: return@forEach
                    if (cents <= 0) return@forEach

                    var score = labelScore(region.text, match.range.first)
                    if (score < 0) return@forEach

                    var source = region.text
                    var textOnlyAssociation = false
                    if (score == 0) {
                        val label = readable.mapIndexedNotNull { labelIndex, other ->
                            if (index == labelIndex || amountRegex.containsMatchIn(other.text)) {
                                return@mapIndexedNotNull null
                            }
                            val strength = labelScore(other.text)
                            if (strength <= 0) return@mapIndexedNotNull null

                            val relationship = relationship(other, region, labelIndex, index)
                                ?: return@mapIndexedNotNull null
                            Triple(strength - relationship, other.text, labelIndex)
                        }.maxByOrNull { it.first }

                        if (label != null && label.first > score) {
                            score = label.first
                            source = label.second + " → " + region.text
                            textOnlyAssociation = region.left == null
                        }
                    }

                    if (score >= MIN_CANDIDATE_SCORE) {
                        add(RankedAmount(cents, score, region, source, textOnlyAssociation))
                    }
                }
            }
        }
        val best = candidates.maxWithOrNull(
            compareBy<RankedAmount> { it.score }
                .thenBy { it.region.recognitionConfidence ?: 1f }
        ) ?: return null

        // A document containing two equally convincing but different totals is
        // ambiguous. Never choose one just because it appears later in the OCR.
        val competitor = candidates.filter { it.cents != best.cents }.maxOfOrNull { it.score }
        if (competitor != null && best.score - competitor < MIN_SCORE_MARGIN) return null

        val confidence = best.region.recognitionConfidence?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
        val rating = when {
            best.score >= 13 && !best.textOnlyAssociation &&
                (confidence == null || confidence >= 0.90f) ->
                OcrConfidence.HIGH
            best.score >= 7 && (confidence == null || confidence >= 0.70f) ->
                OcrConfidence.MEDIUM
            else -> OcrConfidence.LOW
        }
        return ReceiptPriceCandidate(
            amountCents = best.cents,
            evidence = OcrEvidence(
                sourceLine = best.source,
                lineNumber = best.region.lineNumber,
                heuristicScore = best.score,
                confidence = rating,
                recognitionConfidence = confidence
            )
        )
    }

    private fun labelScore(text: String, amountOffset: Int = Int.MAX_VALUE): Int {
        if (totalRejectedTerms.containsMatchIn(text)) return -1
        fun beforeAmount(pattern: Regex): Boolean =
            pattern.find(text)?.range?.last?.let { it < amountOffset } == true

        return when {
            beforeAmount(payableLabels) -> 20
            beforeAmount(invoiceLabels) -> 17
            beforeAmount(totalStrongTerms) -> 15
            beforeAmount(totalAnyTerm) -> 9
            else -> 0
        }
    }

    /**
     * Cost to attach a standalone label to a neighboring amount.
     * OCR geometry takes precedence; text-only PDFs use the immediately next line.
     */
    private fun relationship(
        label: OcrTextRegion,
        value: OcrTextRegion,
        labelIndex: Int,
        valueIndex: Int
    ): Int? {
        val lx = label.left
        val ly = label.top
        val lr = label.right
        val lb = label.bottom
        val vx = value.left
        val vy = value.top
        val vr = value.right
        val vb = value.bottom

        if (lx != null && ly != null && lr != null && lb != null &&
            vx != null && vy != null && vr != null && vb != null
        ) {
            val height = max(max(lb - ly, vb - vy), 1f)
            val labelCenter = (ly + lb) / 2f
            val amountCenter = (vy + vb) / 2f
            if (abs(labelCenter - amountCenter) <= height * 0.8f &&
                vx >= lx && vx >= lr - height
            ) return 1

            // The price may be printed directly below the total label.
            if (vy >= ly && vy - lb <= height * 1.5f &&
                vy - lb >= -height * 0.25f && vx >= lx - height
            ) return 4

            return null
        }

        return if (valueIndex == labelIndex + 1) 4 else null
    }

    private fun parseCents(raw: String): Long? {
        val normalized = raw.filterNot(Char::isWhitespace)
        val separator = max(normalized.lastIndexOf('.'), normalized.lastIndexOf(','))
        if (separator <= 0 || normalized.length - separator != 3) return null
        val integer = normalized.substring(0, separator).filter(Char::isDigit)
        val fraction = normalized.substring(separator + 1)
        if (integer.isEmpty() || fraction.length != 2 || !fraction.all(Char::isDigit)) return null
        return (integer + fraction).toLongOrNull()
    }

    private const val MIN_CANDIDATE_SCORE = 6
    private const val MIN_SCORE_MARGIN = 4
}
