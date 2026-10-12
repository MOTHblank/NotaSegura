package com.mothblank.notasegura

import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.backup.BackupArchiveManager
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.Payment
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
class BackupArchiveIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val filesToRemove = mutableListOf<File>()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun cleanup() = runBlocking {
        db.purchaseDao().getAttachmentsSnapshot().forEach {
            filesToRemove.add(File(it.filePath))
        }
        db.close()
        filesToRemove.forEach { it.delete() }
        filesToRemove.mapNotNull { it.parentFile }
            .filter { it.name.startsWith("restore_") }
            .distinct()
            .forEach { it.deleteRecursively() }
    }

    @Test
    fun restoreRoundTripPreservesOriginalDocumentsAndRecords() = runBlocking {
        val original = seed()
        val manager = BackupArchiveManager(context, db)
        val archive = newArchive()

        assertTrue(manager.createBackup(archiveUri(archive), PASSWORD).isSuccess)
        // Modify the live database, then ensure the archive actually replaces it.
        db.purchaseDao().clearAttachments()
        db.purchaseDao().clearPurchases()
        db.paymentDao().clearPayments()

        val result = manager.restoreBackup(archiveUri(archive), PASSWORD)
        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(1, db.purchaseDao().getPurchasesSnapshot().size)
        assertEquals(1, db.paymentDao().getPaymentsSnapshot().size)
        val restored = db.purchaseDao().getAttachmentsSnapshot().single()
        assertEquals(original.id, restored.id)
        assertArrayEquals(TEST_DOCUMENT, File(restored.filePath).readBytes())
    }

    @Test
    fun wrongPasswordAndTamperingLeaveLiveRecordsUntouched() = runBlocking {
        seed()
        val manager = BackupArchiveManager(context, db)
        val archive = newArchive()
        assertTrue(manager.createBackup(archiveUri(archive), PASSWORD).isSuccess)

        assertTrue(manager.restoreBackup(archiveUri(archive), "wrong-password").isFailure)
        assertEquals(1, db.purchaseDao().getPurchasesSnapshot().size)
        assertEquals(1, db.purchaseDao().getAttachmentsSnapshot().size)

        val bytes = archive.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        archive.writeBytes(bytes)
        assertFalse(manager.restoreBackup(archiveUri(archive), PASSWORD).isSuccess)
        assertEquals(1, db.purchaseDao().getPurchasesSnapshot().size)
        assertTrue(File(db.purchaseDao().getAttachmentsSnapshot().single().filePath).isFile)
    }

    private suspend fun seed(): Attachment {
        val id = UUID.randomUUID().toString()
        val attachmentId = UUID.randomUUID().toString()
        val document = File(context.filesDir, "attachments/$attachmentId.pdf")
        document.parentFile!!.mkdirs()
        document.writeBytes(TEST_DOCUMENT)
        filesToRemove.add(document)

        db.purchaseDao().insertPurchase(
            Purchase(
                id = id,
                productName = "Produto de teste",
                purchaseDate = LocalDate.of(2026, 9, 20),
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val attachment = Attachment(
            id = attachmentId,
            purchaseId = id,
            mimeType = "application/pdf",
            type = "RECEIPT",
            filePath = document.absolutePath,
            createdAt = 1L
        )
        db.purchaseDao().insertAttachments(listOf(attachment))
        db.paymentDao().insertPayment(
            Payment(
                id = UUID.randomUUID().toString(),
                title = "Parcela",
                amountCents = 1250L,
                dueDate = LocalDate.of(2026, 10, 20)
            )
        )
        return attachment
    }

    private fun newArchive(): File {
        val archive = File(context.cacheDir, "exports/backup_test_${UUID.randomUUID()}.notasegura")
        archive.parentFile!!.mkdirs()
        filesToRemove.add(archive)
        return archive
    }

    private fun archiveUri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    companion object {
        private const val PASSWORD = "strong-test-password"
        private val TEST_DOCUMENT = "%PDF-1.4\nNotaSegura test\n".toByteArray()
    }
}
