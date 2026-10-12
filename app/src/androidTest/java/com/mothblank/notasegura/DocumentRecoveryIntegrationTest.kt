package com.mothblank.notasegura

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mothblank.notasegura.data.storage.ManagedDocumentRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentRecoveryIntegrationTest {
    @Test
    fun onlyOldUnreferencedFilesAreQuarantined() {
        val base = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "recovery_test_${UUID.randomUUID()}"
        )
        val managed = File(base, "attachments").apply { mkdirs() }
        val quarantine = File(base, "quarantine")
        val referenced = File(managed, "current.pdf").apply { writeText("keep") }
        val orphan = File(managed, "orphan.pdf").apply { writeText("recover me") }
        val recent = File(managed, "pending.pdf").apply { writeText("in flight") }
        val now = System.currentTimeMillis()
        referenced.setLastModified(now - 10_000L)
        orphan.setLastModified(now - 10_000L)
        recent.setLastModified(now)

        try {
            val result = ManagedDocumentRecovery.reconcileFiles(
                managed, quarantine, setOf(referenced.canonicalPath), now, 5_000L
            )
            assertEquals(1, result.quarantined)
            assertEquals(0, result.missingReferenced)
            assertTrue(referenced.isFile)
            assertFalse(orphan.exists())
            assertTrue(recent.isFile)
            assertEquals("recover me", quarantine.listFiles()!!.single().readText())

            val again = ManagedDocumentRecovery.reconcileFiles(
                managed, quarantine, setOf(referenced.canonicalPath), now, 5_000L
            )
            assertEquals(0, again.quarantined)
        } finally {
            base.deleteRecursively()
        }
    }
}
