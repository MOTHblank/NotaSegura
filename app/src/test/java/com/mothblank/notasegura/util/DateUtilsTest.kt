package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {
    @Test
    fun typingDigitsAndPastingFormattedDateYieldSameValue() {
        assertEquals("12/10/2026", DateUtils.formatEditableDateInput("12102026"))
        assertEquals("12/10/2026", DateUtils.formatEditableDateInput("12/10/2026"))
        assertEquals("12/1", DateUtils.formatEditableDateInput("121"))
        assertEquals(LocalDate.of(2026, 10, 12), DateUtils.parseEditableDate("12/10/2026"))
    }

    @Test
    fun invalidOrPartialDatesCannotBeParsed() {
        assertNull(DateUtils.parseEditableDate("31/02/2026"))
        assertNull(DateUtils.parseEditableDate("29/02/2025"))
        assertNull(DateUtils.parseEditableDate("12/10/"))
        assertNull(DateUtils.parseEditableDate("00/10/2026"))
        assertEquals(LocalDate.of(2024, 2, 29), DateUtils.parseEditableDate("29/02/2024"))
    }

    @Test
    fun pickerAndTextDatesRoundTrip() {
        val date = LocalDate.of(2020, 1, 3)
        assertEquals(date, DateUtils.datePickerMillisToLocalDate(DateUtils.localDateToDatePickerMillis(date)))
        assertEquals("03/01/2020", DateUtils.formatEditableDate(date))
        assertEquals("", DateUtils.formatEditableDate(null))
    }
}
