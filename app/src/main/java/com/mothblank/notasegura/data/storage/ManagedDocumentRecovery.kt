package com.mothblank.notasegura.data.storage

import android.content.Context
import android.util.Log
import com.mothblank.notasegura.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DocumentRecoveryReport(
    val quarantined: Int,
    val missingReferenced: Int
)

/**
 * Room is transactional, but filesystem writes cannot join its transaction.
 * On the next start after an interrupted save, delete or restore, an unreferenced
 * managed file may remain. Keep such files in private quarantine rather than
 * irreversibly deleting someone's receipt.
 *
 * Only move files older than the grace period, to avoid racing a live writer
 * that has copied a file but has not yet committed its database row.
 */
object ManagedDocumentRecovery {
    private const val TAG = "NotaSeguraRecovery"
    internal const val GRACE_PERIOD_MS = 7L * 24 * 60 * 60 * 1000

    suspend fun reconcile(context: Context, database: AppDatabase): DocumentRecoveryReport =
        withContext(Dispatchers.IO) {
            val referenced = database.purchaseDao().getAttachmentsSnapshot()
                .map { File(it.filePath).canonicalPath }
                .toSet()
            val report = reconcileFiles(
                storageRoot = File(context.filesDir, "attachments"),
                recoveryDirectory = File(context.filesDir, "attachment_recovery"),
                referencedFiles = referenced,
                now = System.currentTimeMillis(),
                gracePeriodMillis = GRACE_PERIOD_MS
            )
            if (report.missingReferenced > 0 || report.quarantined > 0) {
                Log.w(TAG, "Missing documents: ${report.missingReferenced}; " +
                    "quarantined abandoned documents: ${report.quarantined}")
            }
            report
        }

    internal fun reconcileFiles(
        storageRoot: File,
        recoveryDirectory: File,
        referencedFiles: Set<String>,
        now: Long,
        gracePeriodMillis: Long
    ): DocumentRecoveryReport {
        require(gracePeriodMillis >= 0L)
        if (!storageRoot.isDirectory) return DocumentRecoveryReport(0, 0)

        val canonicalRoot = storageRoot.canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        val canonicalReferences = referencedFiles.mapTo(hashSetOf()) {
            File(it).canonicalPath
        }
        val missing = canonicalReferences.count {
            it.startsWith(rootPrefix) && !File(it).isFile
        }

        var quarantined = 0
        canonicalRoot.walkTopDown().filter { it.isFile }.forEach { file ->
            val canonical = file.canonicalPath
            // Never follow a link out of managed storage or touch an active file.
            if (!canonical.startsWith(rootPrefix) || canonical in canonicalReferences) {
                return@forEach
            }
            val age = now - file.lastModified()
            if (file.lastModified() <= 0 || age < gracePeriodMillis) return@forEach

            if (!recoveryDirectory.isDirectory && !recoveryDirectory.mkdirs()) {
                return@forEach
            }
            val destination = File(
                recoveryDirectory,
                "${UUID.randomUUID()}_${file.name}"
            )
            // A move within app-private internal storage is atomic on Android.
            // If the move fails, preserve the source for the next scan.
            if (file.renameTo(destination)) quarantined++
        }
        return DocumentRecoveryReport(quarantined, missing)
    }
}
