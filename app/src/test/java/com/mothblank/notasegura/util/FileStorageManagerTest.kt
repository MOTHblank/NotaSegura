package com.mothblank.notasegura.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FileStorageManagerTest {

    @Test
    fun datePickerMillisAreInterpretedAsUtcCalendarDates() {
        val millis = 1_795_651_200_000L // 2026-11-26T00:00:00Z
        assertEquals(
            LocalDate.of(2026, 11, 26),
            DateUtils.datePickerMillisToLocalDate(millis)
        )
    }
}
