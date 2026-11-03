package com.mothblank.notasegura.data.ocr

import com.mothblank.notasegura.util.OcrTextRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptRecoveryPolicyTest {
    @Test
    fun cleanReceiptSkipsAdditionalImagePass() {
        val regions = listOf(
            box("TOTAL A PAGAR", 1, 10f, 100f, 130f, 120f, 0.99f),
            box("R$ 83,40", 2, 170f, 100f, 260f, 120f, 0.98f)
        )
        assertFalse(ReceiptRecoveryPolicy.shouldEnhance(regions))
        assertNull(ReceiptRecoveryPolicy.proposeCrop(regions, 400, 700))
    }

    @Test
    fun lowConfidenceTotalCanReceiveAConstrainedCrop() {
        val regions = listOf(
            box("TOTAL A PAGAR", 1, 10f, 100f, 130f, 120f, 0.95f),
            box("R$ 83,40", 2, 170f, 100f, 260f, 120f, 0.60f)
        )
        assertTrue(ReceiptRecoveryPolicy.shouldEnhance(regions))
        val crop = ReceiptRecoveryPolicy.proposeCrop(regions, 400, 700)
        assertNotNull(crop)
        assertTrue(crop!!.left >= 0f)
        assertTrue(crop.right <= 400f)
        assertTrue(crop.top < 100f)
        assertTrue(crop.bottom > 120f)
    }

    @Test
    fun missingValueCanRetryOnlyWhenStrongTotalLabelExists() {
        val missingPrice = listOf(
            box("TOTAL A PAGAR", 1, 10f, 100f, 130f, 120f, 0.96f)
        )
        assertNotNull(ReceiptRecoveryPolicy.proposeCrop(missingPrice, 400, 700))
        val untrusted = listOf(
            box("TOTAL DE TRIBUTOS", 1, 10f, 100f, 130f, 120f, 0.96f)
        )
        assertNull(ReceiptRecoveryPolicy.proposeCrop(untrusted, 400, 700))
    }

    @Test
    fun enhancementCannotSilentlyResolveConflictingTotals() {
        val first = listOf(box("VALOR TOTAL R$ 28,40", 1, 10f, 100f, 250f, 120f, 0.91f))
        val other = listOf(box("VALOR TOTAL R$ 23,40", 1, 10f, 100f, 250f, 120f, 0.96f))
        assertTrue(ReceiptRecoveryPolicy.contradict(first, other))
    }

    @Test
    fun isolatedPriceNeedsExactlyOneRecognizedNumber() {
        val single = listOf(box("R$ 28,40", 1, 0f, 0f, 100f, 20f, 0.97f))
        assertEquals(2840L, ReceiptRecoveryPolicy.isolatedPrice(single)?.amountCents)
        val ambiguous = single + box("R$ 23,40", 2, 0f, 25f, 100f, 45f, 0.96f)
        assertNull(ReceiptRecoveryPolicy.isolatedPrice(ambiguous))
    }

    private fun box(
        text: String,
        number: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        confidence: Float
    ) = OcrTextRegion(text, number, confidence, left, top, right, bottom)
}
