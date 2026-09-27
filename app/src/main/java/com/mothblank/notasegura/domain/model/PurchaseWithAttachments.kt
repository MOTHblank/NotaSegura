package com.mothblank.notasegura.domain.model

import androidx.room.Embedded
import androidx.room.Relation

data class PurchaseWithAttachments(
    @Embedded
    val purchase: Purchase,
    @Relation(
        parentColumn = "id",
        entityColumn = "purchaseId"
    )
    val attachments: List<Attachment>
)
