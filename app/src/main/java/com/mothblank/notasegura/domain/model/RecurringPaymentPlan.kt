package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

/** A monthly obligation, independently of whether any individual bill was paid. */
@Entity(tableName = "recurring_payment_plans")
data class RecurringPaymentPlan(
    @PrimaryKey val id: String,
    val title: String,
    val amountCents: Long,
    val startDate: LocalDate,
    val anchorDay: Int,
    val isActive: Boolean = true
) {
    fun dueIn(month: YearMonth): LocalDate =
        month.atDay(anchorDay.coerceIn(1, month.lengthOfMonth()))
}

/** Prevents an intentionally deleted individual bill from being regenerated. */
@Entity(tableName = "recurring_payment_skips", primaryKeys = ["seriesId", "dueDate"])
data class RecurringPaymentSkip(
    val seriesId: String,
    val dueDate: LocalDate
)
