package com.mothblank.notasegura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Purchase
import com.mothblank.notasegura.domain.model.PurchaseWithAttachments
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PurchaseDao {

    @Transaction
    @Query(
        """
        SELECT * FROM purchases
        ORDER BY
            CASE WHEN warrantyEndDate IS NULL THEN 1 ELSE 0 END,
            warrantyEndDate ASC,
            purchaseDate DESC
        """
    )
    fun getAllPurchases(): Flow<List<PurchaseWithAttachments>>

    @Transaction
    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: String): PurchaseWithAttachments?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("DELETE FROM attachments WHERE id IN (:attachmentIds)")
    suspend fun deleteAttachmentsById(attachmentIds: List<String>)

    @Query("DELETE FROM purchases WHERE id = :purchaseId")
    suspend fun deletePurchaseById(purchaseId: String)

    @Transaction
    suspend fun savePurchase(
        purchase: Purchase,
        newAttachments: List<Attachment>,
        updatedAttachments: List<Attachment>,
        attachmentIdsToDelete: List<String>
    ) {
        insertPurchase(purchase)
        if (newAttachments.isNotEmpty()) {
            insertAttachments(newAttachments)
        }
        if (updatedAttachments.isNotEmpty()) {
            insertAttachments(updatedAttachments)
        }
        if (attachmentIdsToDelete.isNotEmpty()) {
            deleteAttachmentsById(attachmentIdsToDelete)
        }
    }

    @Query(
        """
        SELECT * FROM purchases
        WHERE warrantyEndDate IS NOT NULL
          AND warrantyEndDate BETWEEN :startDate AND :endDate
        ORDER BY warrantyEndDate ASC
        """
    )
    suspend fun getPurchasesExpiringBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Purchase>

    @Query("SELECT * FROM purchases")
    suspend fun getPurchasesSnapshot(): List<Purchase>

    @Query("SELECT * FROM attachments")
    suspend fun getAttachmentsSnapshot(): List<Attachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchases(purchases: List<Purchase>)

    @Query("DELETE FROM attachments")
    suspend fun clearAttachments()

    @Query("DELETE FROM purchases")
    suspend fun clearPurchases()
}
