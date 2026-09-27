package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["productName"]),
        Index(value = ["merchant"]),
        Index(value = ["purchaseDate"]),
        Index(value = ["warrantyEndDate"])
    ]
)
data class Purchase(
    @PrimaryKey
    val id: String,
    val productName: String,
    val merchant: String? = null,
    val purchaseValueCents: Long? = null,
    val purchaseDate: LocalDate,
    val warrantyEndDate: LocalDate? = null,
    val category: String = "",
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
