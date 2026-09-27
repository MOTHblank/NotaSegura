package com.mothblank.notasegura.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object DateUtils {
    fun datePickerMillisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
