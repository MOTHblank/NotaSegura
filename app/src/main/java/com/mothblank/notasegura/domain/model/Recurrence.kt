package com.mothblank.notasegura.domain.model

import java.time.LocalDate
import java.time.YearMonth

fun Payment.nextOccurrenceDate(): LocalDate? {
    val months = recurrenceMonths?.takeIf { it > 0 } ?: return null
    val anchorDay = (recurrenceAnchorDay ?: dueDate.dayOfMonth).coerceIn(1, 31)
    val targetMonth = YearMonth.from(dueDate).plusMonths(months.toLong())
    return targetMonth.atDay(anchorDay.coerceAtMost(targetMonth.lengthOfMonth()))
}
