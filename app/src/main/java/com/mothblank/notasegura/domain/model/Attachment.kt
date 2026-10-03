package com.mothblank.notasegura.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["purchaseId"])]
)
data class Attachment(
    @PrimaryKey
    val id: String,
    val purchaseId: String,
    val mimeType: String,
    val type: String,
    val filePath: String,
    val displayName: String? = null,
    val sha256: String? = null,
    val ocrText: String? = null,
    val createdAt: Long
)

val Attachment.isImage: Boolean
    get() = mimeType.startsWith("image/")

val Attachment.isPdf: Boolean
    get() = mimeType == "application/pdf"

object AttachmentType {
    const val RECEIPT = "RECEIPT"
    const val INVOICE = "INVOICE"
    const val WARRANTY = "WARRANTY"
    const val MANUAL = "MANUAL"
    const val OTHER = "OTHER"

    val supported = setOf(RECEIPT, INVOICE, WARRANTY, MANUAL, OTHER)

    fun normalize(value: String): String =
        value.takeIf { it in supported } ?: OTHER
}
