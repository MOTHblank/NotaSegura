package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReceiptOcrParserTest {

    @Test
    fun parsesStructuredReceiptSuggestionsWithoutInventingWarranty() {
        val text = """
            LOJA EXEMPLO LTDA
            CNPJ 12.345.678/0001-99
            MODELO: XR500
            S/N: ABC123456
            18/09/2026
            VALOR TOTAL R$ 1.234,56
        """.trimIndent()

        val result = ReceiptOcrParser.parse(
            text = text,
            today = LocalDate.of(2026, 9, 26)
        )

        assertEquals("LOJA EXEMPLO LTDA", result.merchant)
        assertEquals(LocalDate.of(2026, 9, 18), result.purchaseDate)
        assertEquals(123456L, result.purchaseValueCents)
        assertEquals("XR500", result.modelNumber)
        assertEquals("ABC123456", result.serialNumber)
    }
}
