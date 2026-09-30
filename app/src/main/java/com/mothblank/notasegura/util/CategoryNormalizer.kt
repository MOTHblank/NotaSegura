package com.mothblank.notasegura.util

import java.text.Normalizer
import java.util.Locale

object CategoryNormalizer {
    fun key(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    fun canonicalDisplay(input: String, existing: Iterable<String>): String {
        val trimmed = input.trim().replace(Regex("""\s+"""), " ")
        if (trimmed.isBlank()) return ""
        val key = key(trimmed)
        return existing.firstOrNull { key(it) == key } ?: trimmed
    }

    fun distinctDisplay(values: Iterable<String>): List<String> =
        values.asSequence()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .groupBy(::key)
            .values
            .mapNotNull { variants ->
                variants.maxWithOrNull(
                    compareBy<String> { hasDiacritics(it) }
                        .thenBy { it.count(Char::isUpperCase) in 1 until it.count(Char::isLetter).coerceAtLeast(1) }
                        .thenBy { -it.length }
                )
            }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    private fun hasDiacritics(value: String): Boolean =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .any { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
}
