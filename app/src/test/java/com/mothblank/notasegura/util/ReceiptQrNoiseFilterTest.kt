package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptQrNoiseFilterTest {
    @Test
    fun qrPayloadAnd44DigitAccessKeyAreNeverMonetaryEvidence() {
        assertTrue(ReceiptQrNoiseFilter.isMetadataLine("QR Code"))
        assertTrue(ReceiptQrNoiseFilter.isMetadataLine("https://nfce.fazenda.gov.br/consulta?p=123"))
        assertTrue(ReceiptQrNoiseFilter.isMetadataLine("CHAVE DE ACESSO 123"))
        assertTrue(
            ReceiptQrNoiseFilter.isMetadataLine(
                "35261012345678000190650010000319911234567890"
            )
        )
        assertFalse(ReceiptQrNoiseFilter.isMetadataLine("TOTAL A PAGAR R$ 99,90"))
        assertFalse(ReceiptQrNoiseFilter.isMetadataLine("CNPJ 11.222.333/0001-81"))
    }

    @Test
    fun aQrcodeCannotCreateAFalseTotalEvenWhenOcrReadsAPrice() {
        val regions = listOf(
            box("QR CODE", 1, 70f, 100f, 150f, 120f),
            box("TOTAL A PAGAR R$ 9,90", 2, 75f, 138f, 190f, 162f),
            box("TOTAL A PAGAR R$ 59,90", 3, 15f, 360f, 260f, 388f)
        )
        val qr = ReceiptQrBounds(60f, 80f, 220f, 280f)
        val marked = ReceiptQrNoiseFilter.mark(regions, qr)
        assertTrue(marked[0].isQrNoise)
        assertTrue(marked[1].isQrNoise)
        assertFalse(marked[2].isQrNoise)

        val parsed = ReceiptOcrParser.parse(marked)
        assertEquals(5990L, parsed.purchaseValueCents)
        assertEquals(regions.joinToString("\n") { it.text }, parsed.rawText)
        assertEquals(3, parsed.evidence["purchaseValueCents"]?.lineNumber)
    }

    @Test
    fun excludesSpuriousQrPayloadWithoutImageGeometry() {
        val regions = listOf(
            OcrTextRegion("QR CODE R$ 9,99", 1, 0.98f),
            OcrTextRegion("TOTAL A PAGAR R$ 28,40", 2, 0.97f)
        )
        val marked = ReceiptQrNoiseFilter.mark(regions, null)
        assertTrue(marked.first().isQrNoise)
        assertEquals(2840L, ReceiptOcrParser.parse(marked).purchaseValueCents)
    }

    @Test
    fun noTotalIsInferredFromQrOnly() {
        val fragments = listOf(
            box("QR CODE", 1, 10f, 20f, 120f, 40f),
            box("TOTAL A PAGAR R$ 1,00", 2, 100f, 110f, 220f, 142f)
        )
        assertNull(
            ReceiptOcrParser.parse(
                ReceiptQrNoiseFilter.mark(
                    fragments,
                    ReceiptQrBounds(80f, 80f, 260f, 260f)
                )
            ).purchaseValueCents
        )
    }

    @Test
    fun qrDetectorIsOnlyRequestedForLikelyFiscalQr() {
        assertFalse(
            ReceiptQrNoiseFilter.shouldDetectQr(
                listOf(OcrTextRegion("PADARIA LTDA", 1))
            )
        )
        assertTrue(
            ReceiptQrNoiseFilter.shouldDetectQr(
                listOf(OcrTextRegion("Consulte pela chave de acesso", 1))
            )
        )
    }

    @Test
    fun qrOverlapDoesNotExcludeNearbyButSeparatePrice() {
        val qr = ReceiptQrBounds(80f, 100f, 230f, 260f)
        val regions = listOf(
            box("TOTAL A PAGAR R$ 92,10", 1, 5f, 275f, 205f, 303f),
            box("000100110101010", 2, 100f, 110f, 180f, 132f)
        )
        val flagged = ReceiptQrNoiseFilter.mark(regions, qr)
        assertFalse(flagged[0].isQrNoise)
        assertTrue(flagged[1].isQrNoise)
    }

    @Test
    fun preservesSourceLineAndConfidenceWhenQrNoisePrecedesMerchant() {
        val marked = ReceiptQrNoiseFilter.mark(
            listOf(
                OcrTextRegion("QR CODE", 1, 0.99f),
                OcrTextRegion("LOJA EXEMPLAR LTDA", 2, 0.55f),
                OcrTextRegion("CNPJ 11.222.333/0001-81", 3, 0.99f),
                OcrTextRegion("EMISSAO 01/11/2026", 4, 0.96f),
                OcrTextRegion("TOTAL A PAGAR R$ 30,40", 5, 0.99f)
            ),
            null
        )
        val parsed = ReceiptOcrParser.parse(
            marked, java.time.LocalDate.of(2026, 11, 2)
        )
        assertEquals("LOJA EXEMPLAR LTDA", parsed.merchant)
        assertEquals(2, parsed.evidence["merchant"]?.lineNumber)
        assertEquals(OcrConfidence.LOW, parsed.evidence["merchant"]?.confidence)
        assertEquals(3040L, parsed.purchaseValueCents)
    }

    private fun box(
        text: String,
        line: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) = OcrTextRegion(
        text = text,
        lineNumber = line,
        recognitionConfidence = 0.98f,
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}
