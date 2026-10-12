package com.mothblank.notasegura

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Purchase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PurchaseUpsertIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun cleanup() = database.close()

    @Test
    fun editingPurchaseDoesNotCascadeDeleteItsAttachments() = runBlocking {
        val purchase = Purchase(
            id = UUID.randomUUID().toString(),
            productName = "Original",
            purchaseDate = LocalDate.of(2026, 10, 1),
            createdAt = 1L,
            updatedAt = 1L
        )
        val attachment = Attachment(
            id = UUID.randomUUID().toString(),
            purchaseId = purchase.id,
            mimeType = "application/pdf",
            type = "RECEIPT",
            filePath = "/not-used-in-this-test.pdf",
            createdAt = 1L
        )
        database.purchaseDao().savePurchase(
            purchase, listOf(attachment), emptyList(), emptyList()
        )
        database.purchaseDao().savePurchase(
            purchase.copy(productName = "Edited", updatedAt = 2L),
            emptyList(), emptyList(), emptyList()
        )

        assertEquals("Edited", database.purchaseDao().getPurchasesSnapshot().single().productName)
        assertEquals(attachment.id, database.purchaseDao().getAttachmentsSnapshot().single().id)
    }
}
