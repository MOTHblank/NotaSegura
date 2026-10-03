package com.mothblank.notasegura.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

sealed interface StageFileResult {
    data class Success(val file: StagedFile) : StageFileResult
    data object UnsupportedType : StageFileResult
    data object TooLarge : StageFileResult
    data object Empty : StageFileResult
    data object InvalidContent : StageFileResult
    data object ReadError : StageFileResult
}

object FileStorageManager {
    const val MAX_ATTACHMENT_BYTES = 100L * 1024L * 1024L

    private const val ATTACHMENTS_DIR = "attachments"
    private const val STAGING_DIR = "attachment_staging"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")

    fun stageUri(context: Context, uri: Uri): StageFileResult {
        val resolver = context.contentResolver
        val metadata = queryMetadata(context, uri)
        val declaredMimeType = runCatching { resolver.getType(uri) }
            .getOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        val displayName = sanitizeDisplayName(
            metadata.displayName
                ?: "documento_${System.currentTimeMillis()}"
        )
        val extension = displayName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()

        val expectsPdf = declaredMimeType == "application/pdf" || extension == "pdf"
        val expectsImage = declaredMimeType?.startsWith("image/") == true ||
            extension in IMAGE_EXTENSIONS

        if (!expectsPdf && !expectsImage) return StageFileResult.UnsupportedType
        if (metadata.size != null && metadata.size > MAX_ATTACHMENT_BYTES) {
            return StageFileResult.TooLarge
        }

        val id = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
        val temporaryFile = File(stagingDir, "$id.tmp")

        return try {
            val input = resolver.openInputStream(uri) ?: return StageFileResult.ReadError
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            input.use { source ->
                FileOutputStream(temporaryFile).use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue

                        totalBytes += count
                        if (totalBytes > MAX_ATTACHMENT_BYTES) {
                            throw AttachmentTooLargeException()
                        }

                        destination.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }

            if (totalBytes == 0L) {
                temporaryFile.delete()
                return StageFileResult.Empty
            }

            val detectedMimeType = when {
                expectsPdf && hasPdfSignature(temporaryFile) -> "application/pdf"
                expectsImage -> detectImageMimeType(temporaryFile)
                else -> null
            }

            if (detectedMimeType == null) {
                temporaryFile.delete()
                return StageFileResult.InvalidContent
            }

            if (expectsPdf && detectedMimeType != "application/pdf") {
                temporaryFile.delete()
                return StageFileResult.InvalidContent
            }
            if (expectsImage && !detectedMimeType.startsWith("image/")) {
                temporaryFile.delete()
                return StageFileResult.InvalidContent
            }

            val stagedFile = File(
                stagingDir,
                "$id.${extensionFor(detectedMimeType, null)}"
            )
            if (!temporaryFile.renameTo(stagedFile)) {
                temporaryFile.copyTo(stagedFile, overwrite = true)
                temporaryFile.delete()
            }

            StageFileResult.Success(
                StagedFile(
                    id = id,
                    path = stagedFile.absolutePath,
                    mimeType = detectedMimeType,
                    displayName = displayName,
                    sha256 = digest.digest().toHex()
                )
            )
        } catch (_: AttachmentTooLargeException) {
            temporaryFile.delete()
            StageFileResult.TooLarge
        } catch (_: Exception) {
            temporaryFile.delete()
            StageFileResult.ReadError
        }
    }

    fun commitStagedFile(context: Context, staged: StagedFile): ManagedFile? {
        val stagedFile = safeFileWithin(File(context.cacheDir, STAGING_DIR), staged.path) ?: return null
        if (!stagedFile.isFile || stagedFile.length() == 0L) return null
        if (stagedFile.length() > MAX_ATTACHMENT_BYTES) return null

        val attachmentsDir = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
        val extension = extensionFor(staged.mimeType, null)
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
        return when (mimeType.lowercase()) {
            "application/pdf" -> "pdf"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> {
                val nameExtension = displayName
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.lowercase()
                    ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                nameExtension
                    ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: if (mimeType.startsWith("image/")) "jpg" else "bin"
            }
        }
    }

    private fun detectImageMimeType(file: File): String? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outMimeType
            ?.lowercase()
            ?.takeIf {
                options.outWidth > 0 &&
                    options.outHeight > 0 &&
                    it.startsWith("image/")
            }
    }

    private fun hasPdfSignature(file: File): Boolean {
        if (file.length() < 5L) return false
        return file.inputStream().use { input ->
            val probe = ByteArray(1024)
            val count = input.read(probe)
            if (count < 5) return@use false

            val marker = "%PDF-".toByteArray(Charsets.US_ASCII)
            (0..count - marker.size).any { offset ->
                marker.indices.all { index ->
                    probe[offset + index] == marker[index]
                }
            }
        }
    }

    private fun queryMetadata(context: Context, uri: Uri): SourceMetadata {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use SourceMetadata()
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                SourceMetadata(
                    displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null,
                    size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        null
                    }
                )
            } ?: SourceMetadata()
        } catch (_: Exception) {
            SourceMetadata()
        }
    }

    private fun sanitizeDisplayName(value: String): String {
        val sanitized = value
            .map { character ->
                when {
                    character.code < 32 -> '_'
                    character == '/' || character == '\\' -> '_'
                    else -> character
                }
            }
            .joinToString("")
            .trim()
            .take(160)
        return sanitized.ifBlank { "documento" }
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

    private data class SourceMetadata(
        val displayName: String? = null,
        val size: Long? = null
    )

    private class AttachmentTooLargeException : IOException()
}
