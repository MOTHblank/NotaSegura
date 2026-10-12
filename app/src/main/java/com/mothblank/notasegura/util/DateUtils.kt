package com.mothblank.notasegura.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

object DateUtils {
    private val editableDateFormatter = DateTimeFormatter
        .ofPattern("dd/MM/uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val editableDatePattern = Regex("""\d{2}/\d{2}/\d{4}""")

    fun datePickerMillisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun localDateToDatePickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // Numeric keyboards do not always expose '/', so insert the separators as users type.
    // Also accepts dates pasted with separators in the expected dd/MM/yyyy format.
    fun formatEditableDateInput(value: String): String {
        val digits = value.filter { it in '0'..'9' }.take(8)
        return buildString {
            append(digits.take(2))
            if (digits.length > 2) {
                append('/')
                append(digits.substring(2, minOf(4, digits.length)))
            }
            if (digits.length > 4) {
                append('/')
                append(digits.substring(4))
            }
        }
    }

    fun parseEditableDate(value: String): LocalDate? {
        if (!editableDatePattern.matches(value)) return null
        return try {
            LocalDate.parse(value, editableDateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun formatEditableDate(value: LocalDate?): String =
        value?.format(editableDateFormatter).orEmpty()
}
