package com.mothblank.notasegura.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    private val ptBr = Locale("pt", "BR")

    fun isValidEditableAmount(value: String): Boolean =
        value.isEmpty() || value.matches(Regex("""^\d*(?:[,.]\d{0,2})?$"""))

    fun parseToCents(value: String): Long? {
        val normalized = value.trim().replace(',', '.')
        if (normalized.isBlank()) return null
        return try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it >= 0L }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun centsToEditable(cents: Long): String =
        BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

    fun formatCents(cents: Long): String =
        NumberFormat.getCurrencyInstance(ptBr).format(BigDecimal.valueOf(cents, 2))
}
