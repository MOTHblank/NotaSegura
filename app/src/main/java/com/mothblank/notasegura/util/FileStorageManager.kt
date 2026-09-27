package com.mothblank.notasegura.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class StagedFile(
    val id: String,
    val path: String,
    val mimeType: String,
    val displayName: String,
    val sha256: String
)

data class ManagedFile(
    val path: String,
    val sha256: String
)

object FileStorageManager {
    private const val ATTACHMENTS_DIR = "attachments"
    private const val STAGING_DIR = "attachment_staging"

    fun stageUri(context: Context, uri: Uri): StagedFile? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
            ?: if (uri.toString().endsWith(".jpg", ignoreCase = true)) "image/jpeg"
            else "application/octet-stream"
        val displayName = queryDisplayName(context, uri)
            ?: "documento_${System.currentTimeMillis()}.${extensionFor(mimeType, null)}"
        val extension = extensionFor(mimeType, displayName)
        val id = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
        val stagedFile = File(stagingDir, "$id.$extension")

        return try {
            val input = resolver.openInputStream(uri) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                FileOutputStream(stagedFile).use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        destination.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }
            if (stagedFile.length() == 0L) {
                stagedFile.delete()
                null
            } else {
                StagedFile(
                    id = id,
                    path = stagedFile.absolutePath,
                    mimeType = mimeType,
                    displayName = displayName,
                    sha256 = digest.digest().toHex()
                )
            }
        } catch (_: Exception) {
            stagedFile.delete()
            null
        }
    }

    fun commitStagedFile(context: Context, staged: StagedFile): ManagedFile? {
        val stagedFile = safeFileWithin(File(context.cacheDir, STAGING_DIR), staged.path) ?: return null
        if (!stagedFile.isFile || stagedFile.length() == 0L) return null

        val attachmentsDir = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
        val extension = extensionFor(staged.mimeType, staged.displayName)
        val destination = File(attachmentsDir, "${staged.id}.$extension")

        return try {
            stagedFile.inputStream().use { source ->
                FileOutputStream(destination).use { output ->
                    source.copyTo(output)
                }
            }

            val actualChecksum = if (destination.isFile && destination.length() > 0L) {
                sha256(destination)
            } else {
                null
            }

            if (actualChecksum == null || actualChecksum != staged.sha256) {
                destination.delete()
                null
            } else {
                ManagedFile(destination.absolutePath, actualChecksum)
            }
        } catch (_: Exception) {
            destination.delete()
            null
        }
    }

    fun deleteStagedFile(context: Context, path: String?): Boolean =
        deleteWithin(File(context.cacheDir, STAGING_DIR), path)

    fun deleteManagedFile(context: Context, path: String?): Boolean =
        deleteWithin(context.filesDir, path)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    fun extensionFor(mimeType: String, displayName: String?): String {
        val nameExtension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        return nameExtension
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: when {
                mimeType.startsWith("image/") -> "jpg"
                mimeType == "application/pdf" -> "pdf"
                else -> "bin"
            }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteWithin(root: File, path: String?): Boolean {
        if (path == null) return false
        val file = safeFileWithin(root, path) ?: return false
        return file.isFile && file.delete()
    }

    private fun safeFileWithin(root: File, path: String): File? {
        return try {
            val canonicalRoot = root.canonicalFile
            val canonicalTarget = File(path).canonicalFile
            val prefix = canonicalRoot.path + File.separator
            canonicalTarget.takeIf { it.path.startsWith(prefix) }
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }
}
