package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptPriceAnalyzerTest {
    @Test
    fun separateSameRowAmountBoxesAreMatchedToTheirTotalLabel() {
        val boxes = listOf(
            region("SUBTOTAL", 1, 5f, 10f, 90f, 26f),
            region("R$ 30,40", 2, 170f, 10f, 240f, 26f),
            region("TOTAL A PAGAR", 3, 5f, 42f, 125f, 58f),
            region("R$ 28,40", 4, 170f, 42f, 240f, 58f),
            region("TROCO", 5, 5f, 76f, 70f, 92f),
            region("R$ 21,60", 6, 170f, 76f, 240f, 92f)
        )

        val result = ReceiptOcrParser.parse(boxes)
        assertEquals(2840L, result.purchaseValueCents)
        assertEquals(OcrConfidence.HIGH, result.evidence["purchaseValueCents"]?.confidence)
        assertEquals(4, result.evidence["purchaseValueCents"]?.lineNumber)
    }

    @Test
    fun textOnlyPdfCanFindAmountOnLineImmediatelyBelowStrongLabel() {
        val result = ReceiptOcrParser.parse(
            "SUBTOTAL\nR$ 52,60\nTOTAL A PAGAR\nR$ 49,90\nPIX\nR$ 49,90"
        )
        assertEquals(4990L, result.purchaseValueCents)
        // A nearby text label is less reliable than a matched physical row.
        assertEquals(OcrConfidence.MEDIUM, result.evidence["purchaseValueCents"]?.confidence)
    }

    @Test
    fun rejectsConflictingEquallyCredibleTotals() {
        val result = ReceiptOcrParser.parse(
            "TOTAL A PAGAR R$ 12,00\nTOTAL A PAGAR R$ 18,00"
        )
        assertNull(result.purchaseValueCents)
        assertNull(result.evidence["purchaseValueCents"])
    }

    @Test
    fun lowOcrConfidenceDoesNotProduceHighConfidencePrice() {
        val boxes = listOf(
            region("VALOR TOTAL", 1, 5f, 10f, 100f, 28f, 0.99f),
            region("R$ 127,80", 2, 170f, 10f, 240f, 28f, 0.56f)
        )
        val result = ReceiptOcrParser.parse(boxes)
        assertEquals(12780L, result.purchaseValueCents)
        assertEquals(OcrConfidence.LOW, result.evidence["purchaseValueCents"]?.confidence)
        assertEquals(0.56f, result.evidence["purchaseValueCents"]?.recognitionConfidence)
    }

    @Test
    fun rejectsFiscalTaxAndProductSubtotals() {
        val result = ReceiptOcrParser.parse(
            "TOTAL DOS PRODUTOS R$ 100,00\n" +
                "DESCONTO R$ 7,00\n" +
                "TOTAL A PAGAR R$ 93,00\n" +
                "TOTAL DE TRIBUTOS R$ 18,00"
        )
        assertEquals(9300L, result.purchaseValueCents)
    }

    @Test
    fun selectsAmountFollowingInlineLabelNotUnrelatedEarlierAmount() {
        val result = ReceiptOcrParser.parse("R$ 10,00  TOTAL R$ 12,00")
        assertEquals(1200L, result.purchaseValueCents)
    }

    @Test
    fun doesNotAssociateDistantTextBoxesJustBecauseTheyHaveRelevantWords() {
        val boxes = listOf(
            region("VALOR TOTAL", 1, 5f, 10f, 100f, 28f),
            region("R$ 89,90", 2, 170f, 210f, 240f, 228f)
        )
        assertNull(ReceiptOcrParser.parse(boxes).purchaseValueCents)
    }

    private fun region(
        text: String,
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        confidence: Float = 0.98f
    ) = OcrTextRegion(
        text = text,
        lineNumber = index,
        recognitionConfidence = confidence,
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}
