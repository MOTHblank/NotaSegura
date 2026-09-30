package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun prefersMerchantNearCnpjOverFiscalHeaders() {
        val text = """
            DOCUMENTO AUXILIAR DA NOTA FISCAL
            SUPERMERCADO BOM PRECO LTDA
            CNPJ 12.345.678/0001-99
            NFC-e
            EMISSAO 29/09/2026 18:42:11
            TOTAL A PAGAR R$ 87,40
        """.trimIndent()

        val result = ReceiptOcrParser.parse(
            text = text,
            today = LocalDate.of(2026, 9, 30)
        )

        assertEquals("SUPERMERCADO BOM PRECO LTDA", result.merchant)
        assertEquals(LocalDate.of(2026, 9, 29), result.purchaseDate)
        assertEquals(8740L, result.purchaseValueCents)
    }

    @Test
    fun totalDoesNotUseCashTenderedOrChange() {
        val text = """
            MERCADO TESTE
            CNPJ 12.345.678/0001-99
            ITEM A 12,00
            ITEM B 8,00
            TOTAL R$ 20,00
            DINHEIRO R$ 50,00
            TROCO R$ 30,00
        """.trimIndent()

        val result = ReceiptOcrParser.parse(text)

        assertEquals(2000L, result.purchaseValueCents)
    }

    @Test
    fun ambiguousUnlabelledMoneyIsNotGuessed() {
        val text = """
            LOJA TESTE LTDA
            ITEM A 12,00
            ITEM B 8,00
            PIX 20,00
        """.trimIndent()

        val result = ReceiptOcrParser.parse(text)

        assertNull(result.purchaseValueCents)
    }

    @Test
    fun ambiguousDatesAreNotGuessedWithoutPurchaseContext() {
        val text = """
            LOJA TESTE LTDA
            18/09/2026
            VALIDADE 18/09/2027
            TOTAL R$ 10,00
        """.trimIndent()

        val result = ReceiptOcrParser.parse(
            text = text,
            today = LocalDate.of(2026, 9, 30)
        )

        assertEquals(LocalDate.of(2026, 9, 18), result.purchaseDate)
    }

    @Test
    fun futureDateIsIgnored() {
        val text = """
            LOJA TESTE LTDA
            DATA DA COMPRA 10/10/2026
            TOTAL R$ 10,00
        """.trimIndent()

        val result = ReceiptOcrParser.parse(
            text = text,
            today = LocalDate.of(2026, 9, 30)
        )

        assertNull(result.purchaseDate)
    }

    @Test
    fun acceptsCommonModelAndSerialLabels() {
        val text = """
            LOJA TESTE LTDA
            MOD. AB-1200
            Nº SÉRIE: ZX9-4455
            TOTAL R$ 199,90
        """.trimIndent()

        val result = ReceiptOcrParser.parse(text)

        assertEquals("AB-1200", result.modelNumber)
        assertEquals("ZX9-4455", result.serialNumber)
    }
}
