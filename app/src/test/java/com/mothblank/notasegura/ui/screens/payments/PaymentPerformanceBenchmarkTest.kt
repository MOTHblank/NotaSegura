package com.mothblank.notasegura.ui.screens.payments

import com.mothblank.notasegura.domain.model.Payment
import com.mothblank.notasegura.domain.model.nextOccurrenceDate
import com.mothblank.notasegura.util.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PaymentPerformanceBenchmarkTest {

    @Test
    fun brazilianCurrencyInputUsesExactCentValues() {
        assertEquals(123456L, CurrencyUtils.parseToCents("1234,56"))
        assertEquals(123456L, CurrencyUtils.parseToCents("1234.56"))
        assertNull(CurrencyUtils.parseToCents("12,345"))
    }

    @Test
    fun monthlyRecurrenceKeepsItsAnchorDayAcrossShortMonths() {
        val january = Payment(
            id = "1",
            title = "Conta",
            amountCents = 1000,
            dueDate = LocalDate.of(2026, 1, 31),
            recurrenceMonths = 1,
            recurrenceAnchorDay = 31,
            seriesId = "series"
        )
        val february = january.nextOccurrenceDate()!!
        assertEquals(LocalDate.of(2026, 2, 28), february)

        val februaryOccurrence = january.copy(dueDate = february)
        assertEquals(
            LocalDate.of(2026, 3, 31),
            februaryOccurrence.nextOccurrenceDate()
        )
    }
}
