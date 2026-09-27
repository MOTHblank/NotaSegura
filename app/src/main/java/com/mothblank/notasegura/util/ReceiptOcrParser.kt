package com.mothblank.notasegura.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ReceiptOcrSuggestions(
    val merchant: String? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseValueCents: Long? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val rawText: String
)

object ReceiptOcrParser {
    private val dateRegex = Regex("""\b(\d{2}[/-]\d{2}[/-]\d{2,4})\b""")
    private val moneyRegex = Regex("""(?:R\$\s*)?(\d{1,3}(?:\.\d{3})*|\d+)[,.](\d{2})""")
    private val serialRegex = Regex(
        """(?i)\b(?:S/?N|SERIAL|N[ÚU]MERO\s+DE\s+S[ÉE]RIE)\s*[:#-]?\s*([A-Z0-9][A-Z0-9._/-]{3,})"""
    )
    private val modelRegex = Regex(
        """(?i)\b(?:MODELO|MODEL)\s*[:#-]?\s*([A-Z0-9][A-Z0-9._/-]{2,})"""
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptOcrSuggestions {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val merchant = lines
            .take(6)
            .firstOrNull { line ->
                line.length in 3..60 &&
                    line.any { it.isLetter() } &&
                    !line.contains(Regex("""\d{2}[/-]\d{2}""")) &&
                    !line.contains(Regex("""(?i)CNPJ|CPF|CUPOM|DANFE|NFC-E|SAT"""))
            }

        val purchaseDate = dateRegex.findAll(text)
            .mapNotNull { parseDate(it.groupValues[1]) }
            .filter { !it.isAfter(today.plusDays(1)) }
            .maxOrNull()

        val totalCandidates = lines
            .filter { it.contains(Regex("""(?i)\bTOTAL\b|VALOR\s+TOTAL|TOTAL\s+A\s+PAGAR""")) }
            .flatMap { line -> moneyRegex.findAll(line).mapNotNull(::moneyToCents).toList() }

        val fallbackValues = if (totalCandidates.isEmpty()) {
            moneyRegex.findAll(text).mapNotNull(::moneyToCents).toList()
        } else {
            emptyList()
        }

        return ReceiptOcrSuggestions(
            merchant = merchant,
            purchaseDate = purchaseDate,
            purchaseValueCents = totalCandidates.ifEmpty { fallbackValues }.maxOrNull(),
            modelNumber = modelRegex.find(text)?.groupValues?.getOrNull(1)?.trim(),
            serialNumber = serialRegex.find(text)?.groupValues?.getOrNull(1)?.trim(),
            rawText = text
        )
    }

    private fun parseDate(raw: String): LocalDate? {
        val normalized = raw.replace('-', '/')
        val pattern = if (normalized.substringAfterLast('/').length == 2) {
            "dd/MM/yy"
        } else {
            "dd/MM/yyyy"
        }

        return try {
            LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun moneyToCents(match: MatchResult): Long? {
        val integerPart = match.groupValues[1].replace(".", "")
        val decimals = match.groupValues[2]
        return (integerPart + decimals).toLongOrNull()
    }
}
