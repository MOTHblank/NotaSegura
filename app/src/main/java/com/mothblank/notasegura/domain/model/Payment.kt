package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey
    val id: String,
    val title: String,
    val amount: Double,
    val dueDate: LocalDate,
    val isPaid: Boolean = false,
    val isRecurring: Boolean = false
)
