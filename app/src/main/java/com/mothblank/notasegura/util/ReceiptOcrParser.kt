package com.mothblank.notasegura.util

import java.time.DateTimeException
import java.time.LocalDate

enum class OcrConfidence { HIGH, MEDIUM, LOW }

data class OcrEvidence(
    val sourceLine: String,
    val lineNumber: Int,
    val heuristicScore: Int,
    val confidence: OcrConfidence,
    val recognitionConfidence: Float? = null
)

data class ReceiptOcrSuggestions(
    val merchant: String? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseValueCents: Long? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val rawText: String,
    val evidence: Map<String, OcrEvidence> = emptyMap()
)

object ReceiptOcrParser {
    private val dateRegex = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""")
    private val timeRegex = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val moneyRegex = Regex(
        """(?<!\d)(?:R\$\s*)?(\d+(?:[.,\s]\d{3})*[.,]\d{2}|\d+[.,]\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    private val cnpjRegex = Regex("""(?i)\bCNPJ\b""")
    private val serialRegex = Regex(
        """(?i)\b(?:S\s*/?\s*N|S\.N\.?|SERIAL|N[º°O]?\s*(?:DE\s+)?S[ÉE]RIE|N[ÚU]MERO\s+(?:DE\s+)?S[ÉE]RIE|S[ÉE]RIE)\s*[:#-]?\s*([A-Z0-9][A-Z0-9._/-]{3,})"""
    )
    private val modelRegex = Regex(
        """(?i)\b(?:MODELO|MODEL|MOD\.)\s*[:#-]?\s*([A-Z0-9][A-Z0-9._/-]{2,})"""
    )

    private val merchantRejectedTerms = Regex(
        """(?i)\b(?:CNPJ|CPF|NFC-?E|NF-?E|SAT|DANFE|CUPOM|NOTA\s+FISCAL|DOCUMENTO\s+AUXILIAR|CHAVE\s+DE\s+ACESSO|PROTOCOLO|AUTORIZA[CÇ][AÃ]O|CONSUMIDOR|ENDERE[CÇ]O|RUA|AVENIDA|AV\.|CEP|TELEFONE|FONE|OBRIGAD[OA]|VOLTE\s+SEMPRE|SEFAZ|SECRETARIA|MINIST[ÉE]RIO)\b"""
    )
    private val merchantPositiveTerms = Regex(
        """(?i)\b(?:LTDA|EIRELI|EPP|ME|S/?A|SUPERMERCADO|MERCADO|LOJA|FARM[ÁA]CIA|DROGARIA|COM[ÉE]RCIO|MAGAZINE|RESTAURANTE|PADARIA|POSTO)\b"""
    )
    private val dateStrongTerms = Regex(
        """(?i)\b(?:DATA\s+(?:DA\s+)?(?:COMPRA|VENDA|EMISS[AÃ]O)|EMISS[AÃ]O)\b"""
    )
    private val dateRejectedTerms = Regex(
        """(?i)\b(?:VENCIMENTO|VALIDADE|GARANTIA|ENTREGA|PREVIS[AÃ]O)\b"""
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptOcrSuggestions {
        val normalizedLines = text.lines()
            .mapIndexedNotNull { sourceIndex, rawLine ->
                val normalized = normalizeWhitespace(rawLine)
                normalized.takeIf { it.isNotBlank() }?.let { sourceIndex + 1 to it }
            }
        val sourceLineNumbers = normalizedLines.map { it.first }
        val lines = normalizedLines.map { it.second }

        val merchant = findMerchant(lines)
        val date = findPurchaseDate(lines, today)
        val totalResult = ReceiptPriceAnalyzer.findTotal(
            normalizedLines.map { (lineNumber, line) -> OcrTextRegion(line, lineNumber) }
        )
        val total = totalResult?.amountCents
        val model = modelRegex.find(text)?.groupValues?.getOrNull(1)?.cleanIdentifier()
        val serial = serialRegex.find(text)?.groupValues?.getOrNull(1)?.cleanIdentifier()
        val evidence = buildMap {
            merchant?.let { chosen ->
                val candidate = lines.take(10).mapIndexedNotNull { index, line ->
                    if (line != chosen || !isPlausibleMerchantLine(line)) {
                        return@mapIndexedNotNull null
                    }
                    val cnpjIndex = lines.indices.firstOrNull {
                        it > index && cnpjRegex.containsMatchIn(lines[it])
                    }
                    var score = (10 - index).coerceAtLeast(1)
                    if (cnpjIndex == index + 1) score += 8
                    else if (cnpjIndex == index + 2) score += 4
                    if (merchantPositiveTerms.containsMatchIn(line)) score += 4
                    if (line == line.uppercase() && line.any(Char::isLetter)) score++
                    if (line.count(Char::isDigit) > line.length / 3) score -= 4
                    Triple(index, score, line)
                }.maxWithOrNull(
                    compareBy<Triple<Int, Int, String>> { it.second }
                        .thenBy { -it.first }
                )
                candidate?.let { (index, score) ->
                    put("merchant", evidenceFor(lines, sourceLineNumbers, index, score, 15, 9))
                }
            }
            date?.let { chosen ->
                val candidate = lines.mapIndexedNotNull { index, line ->
                    val hasChosenDate = dateRegex.findAll(line).any { parseDate(it) == chosen }
                    if (!hasChosenDate) return@mapIndexedNotNull null
                    val score = (if (dateStrongTerms.containsMatchIn(line)) 8 else 0) +
                        (if (timeRegex.containsMatchIn(line)) 3 else 0) -
                        (if (dateRejectedTerms.containsMatchIn(line)) 10 else 0)
                    index to score
                }.filter { it.second >= 0 }
                    .maxWithOrNull(
                        compareBy<Pair<Int, Int>> { it.second }
                            .thenBy { -it.first }
                    )
                candidate?.let { (index, score) ->
                    put("purchaseDate", evidenceFor(lines, sourceLineNumbers, index, score, 8, 3))
                }
            }
            totalResult?.let { result ->
                put("purchaseValueCents", result.evidence)
            }
            model?.let { chosen ->
                val index = lines.indexOfFirst { it.contains(chosen, ignoreCase = true) }
                if (index >= 0) put("modelNumber", evidenceFor(lines, sourceLineNumbers, index, 5, 8, 5))
            }
            serial?.let { chosen ->
                val index = lines.indexOfFirst { it.contains(chosen, ignoreCase = true) }
                if (index >= 0) put("serialNumber", evidenceFor(lines, sourceLineNumbers, index, 5, 8, 5))
            }
        }
        return ReceiptOcrSuggestions(
            merchant = merchant,
            purchaseDate = date,
            purchaseValueCents = total,
            modelNumber = model,
            serialNumber = serial,
            rawText = text,
            evidence = evidence
        )
    }

    /**
     * OCR-specific overload: preserve confidence and geometry while sharing
     * merchant/date/identifier extraction with PDF text and existing callers.
     */
    fun parse(
        regions: List<OcrTextRegion>,
        today: LocalDate = LocalDate.now()
    ): ReceiptOcrSuggestions {
        val text = regions.joinToString("\n") { it.text }
        val base = parse(text, today)
        val price = ReceiptPriceAnalyzer.findTotal(regions)
        return base.copy(
            purchaseValueCents = price?.amountCents,
            evidence = base.evidence.toMutableMap().apply {
                remove("purchaseValueCents")
                price?.let { put("purchaseValueCents", it.evidence) }
            }
        )
    }

    private fun evidenceFor(
        lines: List<String>,
        sourceLineNumbers: List<Int>,
        index: Int,
        score: Int,
        highThreshold: Int,
        mediumThreshold: Int
    ): OcrEvidence = OcrEvidence(
        sourceLine = lines[index],
        lineNumber = sourceLineNumbers[index],
        heuristicScore = score,
        confidence = when {
            score >= highThreshold -> OcrConfidence.HIGH
            score >= mediumThreshold -> OcrConfidence.MEDIUM
            else -> OcrConfidence.LOW
        }
    )

    private fun findMerchant(lines: List<String>): String? {
        if (lines.isEmpty()) return null
        val cnpjIndexes = lines.indices.filter { cnpjRegex.containsMatchIn(lines[it]) }

        return lines
            .take(10)
            .mapIndexedNotNull { index, line ->
                if (!isPlausibleMerchantLine(line)) return@mapIndexedNotNull null

                var score = (10 - index).coerceAtLeast(1)
                val distanceToCnpj = cnpjIndexes
                    .filter { it > index }
                    .minOfOrNull { it - index }
                if (distanceToCnpj == 1) score += 8
                else if (distanceToCnpj == 2) score += 4
                if (merchantPositiveTerms.containsMatchIn(line)) score += 4
                if (line == line.uppercase() && line.any(Char::isLetter)) score += 1
                if (line.count(Char::isDigit) > line.length / 3) score -= 4

                line to score
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= 7 }
            ?.first
    }

    private fun isPlausibleMerchantLine(line: String): Boolean {
        if (line.length !in 3..70) return false
        if (!line.any(Char::isLetter)) return false
        if (merchantRejectedTerms.containsMatchIn(line)) return false
        if (dateRegex.containsMatchIn(line) || moneyRegex.containsMatchIn(line)) return false
        if (line.count(Char::isDigit) > line.length / 2) return false
        if (line.startsWith("http", ignoreCase = true) || line.contains("www.", ignoreCase = true)) {
            return false
        }
        return true
    }

    private fun findPurchaseDate(lines: List<String>, today: LocalDate): LocalDate? {
        data class Candidate(val date: LocalDate, val score: Int, val lineIndex: Int)

        val candidates = lines.flatMapIndexed { index, line ->
            val rejectedContext = dateRejectedTerms.containsMatchIn(line)
            dateRegex.findAll(line).mapNotNull { match ->
                val date = parseDate(match) ?: return@mapNotNull null
                if (date.isAfter(today.plusDays(1))) return@mapNotNull null

                var score = 0
                if (dateStrongTerms.containsMatchIn(line)) score += 8
                if (timeRegex.containsMatchIn(line)) score += 3
                if (rejectedContext) score -= 10
                Candidate(date, score, index)
            }.toList()
        }

        if (candidates.isEmpty()) return null

        candidates
            .filter { it.score > 0 }
            .maxWithOrNull(
                compareBy<Candidate> { it.score }
                    .thenBy { -it.lineIndex }
            )
            ?.let { return it.date }

        val uniqueDates = candidates
            .filter { it.score >= 0 }
            .map { it.date }
            .distinct()

        return uniqueDates.singleOrNull()
    }

    private fun parseDate(match: MatchResult): LocalDate? {
        val day = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val rawYear = match.groupValues[3].toIntOrNull() ?: return null
        val year = if (match.groupValues[3].length == 2) 2000 + rawYear else rawYear

        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun normalizeWhitespace(value: String): String =
        value.trim().replace(Regex("""\s+"""), " ")

    private fun String.cleanIdentifier(): String? =
        trim().trim('.', ',', ':', ';').takeIf { it.length >= 3 }
}
