package com.mothblank.notasegura.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mothblank.notasegura.data.local.AppDatabase
import com.mothblank.notasegura.domain.model.Attachment
import com.mothblank.notasegura.domain.model.AttachmentType
import com.mothblank.notasegura.util.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class QuarantinedDocument(
    val key: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String
)

/**
 * The quarantine is app-private. An abandoned file has no reliable purchase
 * association, so restoration requires an explicit choice of purchase.
 */
object QuarantinedDocumentStore {
    private const val TAG = "NotaSeguraRecovery"
    private const val QUARANTINE_DIR = "attachment_recovery"

    suspend fun list(context: Context): List<QuarantinedDocument> =
        withContext(Dispatchers.IO) {
            val root = File(context.filesDir, QUARANTINE_DIR).canonicalFile
            root.listFiles().orEmpty()
                .mapNotNull { file ->
                    if (!file.isFile || file.canonicalFile.parentFile != root) return@mapNotNull null
                    QuarantinedDocument(
                        key = file.name,
                        displayName = originalName(file.name),
                        mimeType = mimeType(file.name),
                        sizeBytes = file.length(),
                        path = file.absolutePath
                    )
                }
                .sortedBy { it.displayName.lowercase() }
        }

    suspend fun restore(
        context: Context,
        database: AppDatabase,
        key: String,
        purchaseId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val source = resolve(context, key)
                ?: throw IOException("Documento em quarentena não encontrado.")
            val mime = mimeType(source.name)
            if (mime == "application/octet-stream") {
                throw IOException("Formato desconhecido: exporte o arquivo para recuperá-lo.")
            }
            if (source.length() <= 0L || source.length() > FileStorageManager.MAX_ATTACHMENT_BYTES) {
                throw IOException("Tamanho do arquivo incompatível com anexos.")
            }
            // Check the destination before changing the filesystem.
            if (database.purchaseDao().getPurchaseById(purchaseId) == null) {
                throw IOException("A compra selecionada não existe mais.")
            }
            val id = UUID.randomUUID().toString()
            val destinationDir = File(context.filesDir, "attachments")
            if (!destinationDir.isDirectory && !destinationDir.mkdirs()) {
                throw IOException("Não foi possível criar a pasta de documentos.")
            }
            val destination = File(destinationDir, "${id}." +
                FileStorageManager.extensionFor(mime, source.name))
            try {
                source.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                if (destination.length() != source.length()) {
                    throw IOException("A cópia do documento ficou incompleta.")
                }
                val attachment = Attachment(
                    id = id,
                    purchaseId = purchaseId,
                    mimeType = mime,
                    type = AttachmentType.OTHER,
                    filePath = destination.absolutePath,
                    displayName = originalName(source.name),
                    sha256 = FileStorageManager.sha256(destination),
                    createdAt = System.currentTimeMillis()
                )
                database.purchaseDao().insertAttachments(listOf(attachment))
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            // Never delete the only copy before the database link is committed.
            if (!source.delete()) {
                Log.w(TAG, "Restored attachment but quarantine cleanup failed: ${key}")
            }
        }
    }

    suspend fun export(context: Context, key: String, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = resolve(context, key)
                    ?: throw IOException("Documento em quarentena não encontrado.")
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("Não foi possível abrir o destino selecionado.")
                output.use { destination ->
                    source.inputStream().use { input -> input.copyTo(destination) }
                }
            }
        }

    private fun resolve(context: Context, key: String): File? {
        if (key.isBlank() || key != File(key).name || key == "." || key == "..") return null
        val root = File(context.filesDir, QUARANTINE_DIR).canonicalFile
        val target = File(root, key).canonicalFile
        return target.takeIf { it.parentFile == root && it.isFile }
    }

    private fun originalName(key: String): String =
        key.replaceFirst(Regex("^[0-9a-fA-F-]{36}_"), "").ifBlank { "documento" }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        else -> "application/octet-stream"
    }
}
