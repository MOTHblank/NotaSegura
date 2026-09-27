package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "payments",
    indices = [Index(value = ["seriesId", "dueDate"])]
)
data class Payment(
    @PrimaryKey
    val id: String,
    val title: String,
    val amountCents: Long,
    val dueDate: LocalDate,
    val isPaid: Boolean = false,
    val paidAt: LocalDate? = null,
    val recurrenceMonths: Int? = null,
    val recurrenceAnchorDay: Int? = null,
    val seriesId: String? = null,
    val generatedFromId: String? = null
)
