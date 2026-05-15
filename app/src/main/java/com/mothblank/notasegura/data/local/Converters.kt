package com.mothblank.notasegura.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDate? {
        // Converte um Long (epoch day) de volta para LocalDate
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDate?): Long? {
        // Converte um LocalDate para um Long (número de dias desde 1970-01-01)
        return date?.toEpochDay()
    }
}