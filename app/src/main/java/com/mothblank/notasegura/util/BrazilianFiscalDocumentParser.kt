package com.mothblank.notasegura.util

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Conservative Brazilian fiscal-document hints for NFC-e and DANFE.
 *
 * CNPJ is used as evidence of the issuer, never as a substitute for the merchant
 * name. Legacy numeric and new alphanumeric CNPJs share the modulo-11 check,
 * with ASCII code minus 48 for the first twelve positions.
 */
internal object BrazilianFiscalDocumentParser {
    enum class Kind { NFCE, DANFE, OTHER }

    data class Findings(
        val kind: Kind,
        val issuerCnpj: String? = null,
        val issuerName: String? = null,
        val issuerEvidence: OcrEvidence? = null,
        val issueDate: LocalDate? = null,
        val dateEvidence: OcrEvidence? = null,
        val ambiguousIssueDate: Boolean = false
    )

    private val nfce = Regex("""(?i)\b(?:NFC[\s-]?E|DANFE[\s-]+NFC[\s-]?E)\b""")
    private val danfe = Regex("""(?i)\b(?:DANFE|NF[\s-]?E)\b""")
    private val cnpjLabel = Regex("""(?i)\bCNPJ\b""")
    private val cnpj = Regex(
        """(?i)(?<![A-Z0-9])([A-Z0-9]{2}\.?[A-Z0-9]{3}\.?[A-Z0-9]{3}/?[A-Z0-9]{4}-?\d{2})(?![A-Z0-9])"""
    )
    private val stopIssuer = Regex(
        """(?i)\b(?:DESTINAT[AÁ]RIO|REMETENTE|CONSUMIDOR|DADOS\s+DO\s+CLIENTE)\b"""
    )
    private val merchantLabel = Regex(
        """(?i)^\s*(?:NOME\s*/\s*RAZ[AÃ]O\s+SOCIAL|RAZ[AÃ]O\s+SOCIAL|NOME\s+DO\s+EMITENTE|EMITENTE)\s*[:\-]\s*"""
    )
    private val merchantReject = Regex(
        """(?i)\b(?:DANFE|NFC[\s-]?E|NOTA\s+FISCAL|DOCUMENTO\s+AUXILIAR|CNPJ|CPF|IE|INSCRI[CÇ][AÃ]O|LOGRADOURO|ENDERE[CÇ]O|AVENIDA|RUA|CEP|BAIRRO|MUNIC[IÍ]PIO|PROTOCOLO|AUTORIZA[CÇ][AÃ]O|SEFAZ|RECEITA|CHAVE\s+DE\s+ACESSO|CONSULTA|DATA|HORA|VALOR|TOTAL|FONE|TELEFONE|S[EÉ]RIE|N[UÚ]MERO|DESTINAT[AÁ]RIO|CONSUMIDOR)\b"""
    )
    private val legalSuffix = Regex(
        """(?i)\b(?:LTDA|S\s*/?\s*A|EIRELI|ME|EPP|SUPERMERCADO|MERCADO|FARM[AÁ]CIA|DROGARIA|LOJA|RESTAURANTE|PADARIA)\b"""
    )
    private val issueLabel = Regex(
        """(?i)\b(?:DATA\s*(?:E\s*HORA\s*)?(?:DA?\s*)?EMISS[AÃ]O|EMISS[AÃ]O|EMITID[OA]\s+EM|DATA\s+DA\s+COMPRA)\b"""
    )
    private val dateIgnore = Regex(
        """(?i)\b(?:AUTORIZA[CÇ][AÃ]O|PROTOCOLO|CONSULTA|VENCIMENTO|VALIDADE|GARANTIA|ENTREGA|RECEBIMENTO|SA[IÍ]DA|ENTRADA|CANCELAMENTO|CANCELADA|CHAVE\s+DE\s+ACESSO)\b"""
    )
    private val brazilDate = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})(?!\d)""")
    private val isoDate = Regex("""(?<!\d)(20\d{2})-(\d{2})-(\d{2})(?!\d)""")
    private val time = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")

    fun extract(
        numberedLines: List<Pair<Int, String>>,
        today: LocalDate
    ): Findings {
        val first = numberedLines.take(100).map { it.second }
        val kind = when {
            first.any { nfce.containsMatchIn(it) } -> Kind.NFCE
            first.any { danfe.containsMatchIn(it) } -> Kind.DANFE
            else -> Kind.OTHER
        }

        // Once the recipient section begins, its CNPJ must never be mistaken
        // for the merchant/issuer CNPJ.
        val header = numberedLines.take(30).takeWhile { !stopIssuer.containsMatchIn(it.second) }
        val issuerCandidates = header.asSequence().mapIndexedNotNull { index, (_, line) ->
            if (!cnpjLabel.containsMatchIn(line)) return@mapIndexedNotNull null
            sequenceOf(line, header.getOrNull(index + 1)?.second.orEmpty())
                .flatMap { cnpj.findAll(it).map { match -> match.groupValues[1] } }
                .mapNotNull { candidate ->
                    val normalized = normalizeCnpj(candidate)
                    normalized.takeIf(::validCnpj)?.let { index to it }
                }
                .firstOrNull()
        }.toList()
        val issuer = issuerCandidates
            .takeIf { candidates -> candidates.map { it.second }.distinct().size == 1 }
            ?.firstOrNull()

        val issuerName = issuer?.let { (index, _) ->
            val preceding = header.subList((index - 4).coerceAtLeast(0), index)
                .mapIndexedNotNull { offset, (number, raw) ->
                    val match = merchantLabel.find(raw)
                    val cleaned = if (match != null) raw.removeRange(match.range).trim() else raw.trim()
                    if (!plausibleMerchant(cleaned)) return@mapIndexedNotNull null
                    val distance = index - ((index - 4).coerceAtLeast(0) + offset)
                    val score = (12 - distance * 2) +
                        (if (match != null) 8 else 0) +
                        (if (legalSuffix.containsMatchIn(cleaned)) 5 else 0)
                    Triple(cleaned, number, score)
                }
            // Some NFC-e receipts print "LOJA LTDA CNPJ 11...81" together.
            // Only consider the prefix if the CNPJ itself passes its check digits.
            val (number, fullLine) = header[index]
            val beforeCnpj = cnpjLabel.find(fullLine)?.let { marker ->
                fullLine.substring(0, marker.range.first)
                    .replace(merchantLabel, "")
                    .trim().trim('-', ':', '|', ' ')
            }.orEmpty()
            val sameLine = if (plausibleMerchant(beforeCnpj)) {
                listOf(Triple(
                    beforeCnpj, number,
                    17 + if (legalSuffix.containsMatchIn(beforeCnpj)) 5 else 0
                ))
            } else emptyList()
            (sameLine + preceding).maxByOrNull { it.third }
        }

        data class DateCandidate(val value: LocalDate, val evidence: OcrEvidence, val score: Int)
        val dates = numberedLines.take(100).flatMapIndexed { index, (number, line) ->
            if (!issueLabel.containsMatchIn(line) || dateIgnore.containsMatchIn(line)) {
                return@flatMapIndexed emptyList()
            }
            listOfNotNull(
                line to number,
                numberedLines.getOrNull(index + 1)?.takeIf {
                    // Do not attach a date from an unrelated labelled field.
                    !dateIgnore.containsMatchIn(it.second)
                }?.let { it.second to it.first }
            ).mapIndexedNotNull { offset, (source, sourceNumber) ->
                val found = findDates(source, today).distinct()
                if (found.size != 1) return@mapIndexedNotNull null
                val score = if (offset == 0) 15 else 10
                val finalScore = score + if (time.containsMatchIn(source)) 2 else 0
                DateCandidate(
                    found.single(),
                    OcrEvidence(
                        sourceLine = if (offset == 0) source else line + " → " + source,
                        lineNumber = sourceNumber,
                        heuristicScore = finalScore,
                        confidence = if (finalScore >= 15) OcrConfidence.HIGH else OcrConfidence.MEDIUM
                    ),
                    finalScore
                )
            }
        }
        val topScore = dates.maxOfOrNull { it.score }
        val bestDates = dates.filter { it.score == topScore }.map { it.value }.distinct()
        val selectedDate = if (bestDates.size == 1) {
            dates.first { it.score == topScore && it.value == bestDates.single() }
        } else null

        return Findings(
            kind = kind,
            issuerCnpj = issuer?.second?.let(::formatCnpj),
            issuerName = issuerName?.first,
            issuerEvidence = issuerName?.let { (name, number, score) ->
                OcrEvidence(name, number, score, if (score >= 15) OcrConfidence.HIGH else OcrConfidence.MEDIUM)
            },
            issueDate = selectedDate?.value,
            dateEvidence = selectedDate?.evidence,
            ambiguousIssueDate = bestDates.size > 1
        )
    }

    fun validCnpj(raw: String): Boolean {
        val value = normalizeCnpj(raw)
        if (value.length != 14 || !value.take(12).all { it in '0'..'9' || it in 'A'..'Z' }) {
            return false
        }
        if (!value.takeLast(2).all(Char::isDigit) || value.toSet().size == 1) return false

        val firstWeights = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val secondWeights = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        fun digit(position: Int, weights: IntArray): Int {
            val sum = (0 until position).sumOf {
                (value[it].code - '0'.code) * weights[it]
            }
            val remainder = sum % 11
            return if (remainder < 2) 0 else 11 - remainder
        }
        return digit(12, firstWeights) == value[12].digitToInt() &&
            digit(13, secondWeights) == value[13].digitToInt()
    }

    private fun normalizeCnpj(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    private fun formatCnpj(value: String): String =
        value.substring(0, 2) + "." + value.substring(2, 5) + "." +
            value.substring(5, 8) + "/" + value.substring(8, 12) +
            "-" + value.substring(12, 14)

    private fun plausibleMerchant(value: String): Boolean =
        value.length in 4..75 &&
            value.count(Char::isLetter) >= 4 &&
            !merchantReject.containsMatchIn(value) &&
            !cnpj.containsMatchIn(value) &&
            !brazilDate.containsMatchIn(value) &&
            !value.startsWith("HTTP", ignoreCase = true)

    private fun findDates(value: String, today: LocalDate): List<LocalDate> {
        val brazil = brazilDate.findAll(value).mapNotNull {
            val day = it.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val month = it.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val yearRaw = it.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val year = if (it.groupValues[3].length == 2) 2000 + yearRaw else yearRaw
            safeDate(year, month, day, today)
        }
        val iso = isoDate.findAll(value).mapNotNull {
            safeDate(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                today
            )
        }
        return (brazil + iso).toList()
    }

    private fun safeDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? =
        try {
            LocalDate.of(year, month, day).takeUnless { it.isAfter(today.plusDays(1)) }
        } catch (_: DateTimeException) {
            null
        }
}
