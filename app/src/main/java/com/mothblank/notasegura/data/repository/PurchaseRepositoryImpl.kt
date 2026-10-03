package com.mothblank.notasegura.data.repository

import com.mothblank.notasegura.data.local.PurchaseDao
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import com.mothblank.notasegura.domain.repository.PurchaseRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PurchaseRepositoryImpl(
    private val dao: PurchaseDao
) : PurchaseRepository {

    override fun getAllPurchases(): Flow<List<PurchaseWithAttachments>> =
        dao.getAllPurchases()

    override suspend fun getPurchaseById(id: String): PurchaseWithAttachments? =
        dao.getPurchaseById(id)

    override suspend fun savePurchase(
        purchase: Purchase,
        newAttachments: List<Attachment>,
        updatedAttachments: List<Attachment>,
        attachmentIdsToDelete: List<String>
    ) = dao.savePurchase(
        purchase,
        newAttachments,
        updatedAttachments,
        attachmentIdsToDelete
    )

    override suspend fun deletePurchaseById(id: String) =
        dao.deletePurchaseById(id)

    override suspend fun getPurchasesExpiringBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Purchase> = dao.getPurchasesExpiringBetween(startDate, endDate)
}
