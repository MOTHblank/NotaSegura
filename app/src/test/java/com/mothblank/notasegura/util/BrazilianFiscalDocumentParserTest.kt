package com.mothblank.notasegura.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrazilianFiscalDocumentParserTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun acceptsNumericAndNewAlphanumericCnpjCheckDigits() {
        assertTrue(BrazilianFiscalDocumentParser.validCnpj("11.222.333/0001-81"))
        // Official alphanumeric example: ASCII-48 modulo-11 check digits.
        assertTrue(BrazilianFiscalDocumentParser.validCnpj("12.ABC.345/01DE-35"))
        assertFalse(BrazilianFiscalDocumentParser.validCnpj("11.222.333/0001-82"))
        assertFalse(BrazilianFiscalDocumentParser.validCnpj("12.ABC.345/01DE-36"))
        assertFalse(BrazilianFiscalDocumentParser.validCnpj("00.000.000/0000-00"))
        assertFalse(BrazilianFiscalDocumentParser.validCnpj("12.345.678/0001-99"))
    }

    @Test
    fun locatesIssuerBeforeCustomerEvenWithTwoCnpjs() {
        val text = """
            DANFE NFC-e
            LOJA BOM PRECO LTDA
            CNPJ 11.222.333/0001-81
            ENDERECO DO ESTABELECIMENTO
            DATA E HORA DE EMISSAO: 29/10/2026 15:42
            VALOR TOTAL R$ 100,00
            DESCONTO R$ 10,00
            VALOR A PAGAR R$ 90,00
            CONSUMIDOR
            CNPJ 12.ABC.345/01DE-35
            PROTOCOLO DE AUTORIZACAO: 30/10/2026
        """.trimIndent()

        val result = ReceiptOcrParser.parse(text, today)
        assertEquals("LOJA BOM PRECO LTDA", result.merchant)
        assertEquals("11.222.333/0001-81", result.issuerCnpj)
        assertEquals(LocalDate.of(2026, 10, 29), result.purchaseDate)
        assertEquals(9000L, result.purchaseValueCents)
        assertEquals(OcrConfidence.HIGH, result.evidence["purchaseValueCents"]?.confidence)
    }

    @Test
    fun acceptsIssuerWithAlphanumericCnpjAndExplicitTradeName() {
        val text = """
            DANFE
            RAZAO SOCIAL: DISTRIBUIDORA NOVA LTDA
            CNPJ 12.ABC.345/01DE-35
            EMISSAO 2026-11-01
            VALOR TOTAL DA NOTA R$ 1.024,50
        """.trimIndent()

        val result = ReceiptOcrParser.parse(text, today)
        assertEquals("DISTRIBUIDORA NOVA LTDA", result.merchant)
        assertEquals("12.ABC.345/01DE-35", result.issuerCnpj)
        assertEquals(LocalDate.of(2026, 11, 1), result.purchaseDate)
        assertEquals(102450L, result.purchaseValueCents)
    }

    @Test
    fun findsIssuanceDateOnFollowingLineRatherThanAuthorizationDate() {
        val text = """
            NFC-e
            SUPERMERCADO EXEMPLO
            CNPJ 11.222.333/0001-81
            DATA/HORA DE EMISSAO:
            01/11/2026 17:45
            PROTOCOLO DE AUTORIZACAO: 02/11/2026 10:22
            TOTAL A PAGAR R$ 48,60
        """.trimIndent()
        val result = ReceiptOcrParser.parse(text, today)
        assertEquals(LocalDate.of(2026, 11, 1), result.purchaseDate)
    }

    @Test
    fun explicitExpiryAndDepartureDatesCannotOverrideIssuance() {
        val text = """
            DANFE
            DATA DE SAIDA: 02/11/2026
            DATA DE EMISSAO: 30/10/2026
            VENCIMENTO: 30/11/2026
            VALOR TOTAL DA NF-E 250,00
        """.trimIndent()
        val result = ReceiptOcrParser.parse(text, today)
        assertEquals(LocalDate.of(2026, 10, 30), result.purchaseDate)
        assertEquals(25000L, result.purchaseValueCents)
    }

    @Test
    fun twoConflictingIssueDatesDoNotAutofillEither() {
        val text = """
            DANFE
            DATA DE EMISSAO: 29/10/2026
            DATA DE EMISSAO: 30/10/2026
            VALOR TOTAL DA NOTA R$ 50,00
        """.trimIndent()
        val result = ReceiptOcrParser.parse(text, today)
        assertNull(result.purchaseDate)
        assertNull(result.evidence["purchaseDate"])
    }

    @Test
    fun findsIssuerWhenMerchantAndCnpjArePrintedOnSameLine() {
        val text = """
            NFC-e
            PADARIA NOVA LTDA - CNPJ: 11.222.333/0001-81
            EMITIDA EM 01/11/2026 09:10
            VALOR A PAGAR R$ 18,90
        """.trimIndent()
        val result = ReceiptOcrParser.parse(text, today)
        assertEquals("PADARIA NOVA LTDA", result.merchant)
        assertEquals("11.222.333/0001-81", result.issuerCnpj)
        assertEquals(LocalDate.of(2026, 11, 1), result.purchaseDate)
    }

    @Test
    fun merchantIdentifierIsNotInferredFromRecipientOnly() {
        val text = """
            DANFE
            DADOS DO DESTINATARIO
            RAZAO SOCIAL: EMPRESA COMPRADORA LTDA
            CNPJ 11.222.333/0001-81
            TOTAL DA NOTA R$ 100,00
        """.trimIndent()
        val result = BrazilianFiscalDocumentParser.extract(
            text.lines().mapIndexed { i, value -> i + 1 to value.trim() }, today
        )
        assertNull(result.issuerCnpj)
        assertNull(result.issuerName)
    }

    @Test
    fun invalidOrDuplicatedIssuerCnpjIsNotConsideredVerified() {
        val invalid = """
            NFC-e
            MERCADO A LTDA
            CNPJ 11.222.333/0001-82
            VALOR A PAGAR R$ 10,00
        """.trimIndent()
        assertNull(ReceiptOcrParser.parse(invalid, today).issuerCnpj)

        val duplicates = """
            DANFE
            LOJA A LTDA
            CNPJ 11.222.333/0001-81
            LOJA B LTDA
            CNPJ 12.ABC.345/01DE-35
        """.trimIndent()
        assertNull(ReceiptOcrParser.parse(duplicates, today).issuerCnpj)
    }
}
