package com.mothblank.notasegura.util

import java.time.DateTimeException
import java.time.LocalDate

enum class OcrConfidence { HIGH, MEDIUM, LOW }

data class OcrEvidence(
    val sourceLine: String,
    val lineNumber: Int,
    val heuristicScore: Int,
    val confidence: OcrConfidence
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
    private val totalStrongTerms = Regex(
        """(?i)\b(?:TOTAL\s+A\s+PAGAR|VALOR\s+TOTAL|TOTAL\s+GERAL|TOTAL\s+DA\s+(?:COMPRA|VENDA)|TOTAL\s+R\$)\b"""
    )
    private val totalAnyTerm = Regex("""(?i)\bTOTAL\b""")
    private val totalRejectedTerms = Regex(
        """(?i)\b(?:SUBTOTAL|DESCONTO|TROCO|ACR[ÉE]SCIMO|VALOR\s+PAGO|DINHEIRO|CART[AÃ]O|PIX|IMPOSTO|TRIBUTO)\b"""
    )
    private val dateStrongTerms = Regex(
        """(?i)\b(?:DATA\s+(?:DA\s+)?(?:COMPRA|VENDA|EMISS[AÃ]O)|EMISS[AÃ]O)\b"""
    )
    private val dateRejectedTerms = Regex(
        """(?i)\b(?:VENCIMENTO|VALIDADE|GARANTIA|ENTREGA|PREVIS[AÃ]O)\b"""
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptOcrSuggestions {
        val lines = text.lines()
            .map { normalizeWhitespace(it) }
            .filter { it.isNotBlank() }

        val merchant = findMerchant(lines)
        val date = findPurchaseDate(lines, today)
        val total = findPurchaseTotal(lines)
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
                    put("merchant", evidenceFor(lines, index, score, 15, 9))
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
                    put("purchaseDate", evidenceFor(lines, index, score, 8, 3))
                }
            }
            total?.let { chosen ->
                data class TotalEvidenceCandidate(
                    val lineIndex: Int,
                    val matchIndex: Int,
                    val score: Int
                )
                val candidate = lines.flatMapIndexed { lineIndex, line ->
                    moneyRegex.findAll(line).mapIndexedNotNull { matchIndex, match ->
                        if (moneyToCents(match.groupValues[1]) != chosen) {
                            return@mapIndexedNotNull null
                        }
                        val score =
                            (if (totalStrongTerms.containsMatchIn(line)) 10
                            else if (totalAnyTerm.containsMatchIn(line)) 6 else 0) +
                                (if (Regex("""\bA\s+PAGAR\b""", RegexOption.IGNORE_CASE)
                                        .containsMatchIn(line)) 3 else 0) -
                                (if (totalRejectedTerms.containsMatchIn(line)) 10 else 0)
                        TotalEvidenceCandidate(lineIndex, matchIndex, score)
                    }.toList()
                }.filter { it.score >= 0 }
                    .maxWithOrNull(
                        compareBy<TotalEvidenceCandidate> { it.score }
                            .thenBy { it.lineIndex }
                            .thenBy { it.matchIndex }
                    )
                candidate?.let {
                    put(
                        "purchaseValueCents",
                        evidenceFor(lines, it.lineIndex, it.score, 10, 6)
                    )
                }
            }
            model?.let { chosen ->
                val index = lines.indexOfFirst { it.contains(chosen, ignoreCase = true) }
                if (index >= 0) put("modelNumber", evidenceFor(lines, index, 5, 8, 5))
            }
            serial?.let { chosen ->
                val index = lines.indexOfFirst { it.contains(chosen, ignoreCase = true) }
                if (index >= 0) put("serialNumber", evidenceFor(lines, index, 5, 8, 5))
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

    private fun evidenceFor(
        lines: List<String>,
        index: Int,
        score: Int,
        highThreshold: Int,
        mediumThreshold: Int
    ): OcrEvidence = OcrEvidence(
        sourceLine = lines[index],
        lineNumber = index + 1,
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

    private fun findPurchaseTotal(lines: List<String>): Long? {
        data class Candidate(val cents: Long, val score: Int, val lineIndex: Int, val matchIndex: Int)

        val candidates = lines.flatMapIndexed { lineIndex, line ->
            moneyRegex.findAll(line).mapIndexedNotNull { matchIndex, match ->
                val cents = moneyToCents(match.groupValues[1]) ?: return@mapIndexedNotNull null
                if (cents <= 0L) return@mapIndexedNotNull null

                var score = 0
                if (totalStrongTerms.containsMatchIn(line)) score += 10
                else if (totalAnyTerm.containsMatchIn(line)) score += 6
                if (Regex("""(?i)\bA\s+PAGAR\b""").containsMatchIn(line)) score += 3
                if (totalRejectedTerms.containsMatchIn(line)) score -= 10

                Candidate(cents, score, lineIndex, matchIndex)
            }.toList()
        }

        if (candidates.isEmpty()) return null

        candidates
            .filter { it.score > 0 }
            .maxWithOrNull(
                compareBy<Candidate> { it.score }
                    .thenBy { it.lineIndex }
                    .thenBy { it.matchIndex }
            )
            ?.let { return it.cents }

        val uniqueValues = candidates
            .filter { it.score >= 0 }
            .map { it.cents }
            .distinct()
        return uniqueValues.singleOrNull()
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

    private fun moneyToCents(raw: String): Long? {
        val normalized = raw.replace(" ", "")
        val separatorIndex = maxOf(normalized.lastIndexOf(','), normalized.lastIndexOf('.'))
        if (separatorIndex <= 0 || normalized.length - separatorIndex != 3) return null

        val integerPart = normalized
            .substring(0, separatorIndex)
            .filter(Char::isDigit)
        val decimals = normalized
            .substring(separatorIndex + 1)
            .filter(Char::isDigit)

        if (integerPart.isEmpty() || decimals.length != 2) return null
        return (integerPart + decimals).toLongOrNull()
    }

    private fun normalizeWhitespace(value: String): String =
        value.trim().replace(Regex("""\s+"""), " ")

    private fun String.cleanIdentifier(): String? =
        trim().trim('.', ',', ':', ';').takeIf { it.length >= 3 }
}
