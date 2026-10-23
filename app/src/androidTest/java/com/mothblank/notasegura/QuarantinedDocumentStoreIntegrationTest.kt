package com.mothblank.notasegura

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.data.storage.QuarantinedDocumentStore
import com.mothblank.notasegura.domain.model.Purchase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QuarantinedDocumentStoreIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val temporaryFiles = mutableListOf<File>()
    private val pdf = "%PDF-1.4\nRecovered receipt\n".toByteArray()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun cleanup() {
        db.close()
        temporaryFiles.forEach { it.delete() }
    }

    @Test
    fun listsAndRestoresToChosenPurchaseWithoutLosingBytes() = runBlocking {
        val purchaseId = UUID.randomUUID().toString()
        db.purchaseDao().insertPurchase(
            Purchase(
                id = purchaseId,
                productName = "Geladeira",
                purchaseDate = LocalDate.of(2026, 10, 1),
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val source = quarantined("nota.pdf")
        val found = QuarantinedDocumentStore.list(context).single { it.key == source.name }
        assertEquals("nota.pdf", found.displayName)
        assertEquals("application/pdf", found.mimeType)

        val result = QuarantinedDocumentStore.restore(context, db, found.key, purchaseId)
        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val attachment = db.purchaseDao().getAttachmentsSnapshot().single()
        temporaryFiles.add(File(attachment.filePath))
        assertEquals(purchaseId, attachment.purchaseId)
        assertEquals("nota.pdf", attachment.displayName)
        assertArrayEquals(pdf, File(attachment.filePath).readBytes())
        assertFalse(source.exists())
    }

    @Test
    fun nonexistentPurchasePreservesQuarantinedOriginal() = runBlocking {
        val source = quarantined("recibo.pdf")
        val result = QuarantinedDocumentStore.restore(
            context, db, source.name, UUID.randomUUID().toString()
        )
        assertTrue(result.isFailure)
        assertTrue(source.isFile)
        assertArrayEquals(pdf, source.readBytes())
        assertTrue(db.purchaseDao().getAttachmentsSnapshot().isEmpty())
    }

    private fun quarantined(originalName: String): File {
        val root = File(context.filesDir, "attachment_recovery").apply { mkdirs() }
        val file = File(root, "${UUID.randomUUID()}_${originalName}")
        file.writeBytes(pdf)
        temporaryFiles.add(file)
        return file
    }
}
