package com.mothblank.notasegura.domain.repository

import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PurchaseRepository {
    fun getAllPurchases(): Flow<List<PurchaseWithAttachments>>

    suspend fun getPurchaseById(id: String): PurchaseWithAttachments?

    suspend fun savePurchase(
        purchase: Purchase,
        newAttachments: List<Attachment>,
        updatedAttachments: List<Attachment>,
        attachmentIdsToDelete: List<String>
    )

    suspend fun deletePurchaseById(id: String)

    suspend fun getPurchasesExpiringBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Purchase>
}
