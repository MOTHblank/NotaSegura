package com.mothblank.notasegura.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileStorageManager {
    private const val RECEIPTS_DIR = "receipts"
    private const val STAGING_DIR = "receipt_staging"

    fun stageImage(context: Context, uri: Uri): String? {
        val stagingDir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
        val stagedFile = File(stagingDir, "receipt_${UUID.randomUUID()}.jpg")

        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                FileOutputStream(stagedFile).use { destination ->
                    source.copyTo(destination)
                }
            }
            if (stagedFile.length() == 0L) {
                stagedFile.delete()
                null
            } else {
                stagedFile.absolutePath
            }
        } catch (_: Exception) {
            stagedFile.delete()
            null
        }
    }

    fun commitStagedImage(context: Context, stagedPath: String): String? {
        val stagedFile = safeFileWithin(File(context.cacheDir, STAGING_DIR), stagedPath) ?: return null
        if (!stagedFile.isFile || stagedFile.length() == 0L) return null

        val receiptsDir = File(context.filesDir, RECEIPTS_DIR).apply { mkdirs() }
        val destination = File(receiptsDir, "receipt_${UUID.randomUUID()}.jpg")

        return try {
            stagedFile.inputStream().use { source ->
                FileOutputStream(destination).use { output -> source.copyTo(output) }
            }
            if (!destination.isFile || destination.length() == 0L) {
                destination.delete()
                null
            } else {
                stagedFile.delete()
                destination.absolutePath
            }
        } catch (_: Exception) {
            destination.delete()
            null
        }
    }

    fun deleteStagedImage(context: Context, path: String?): Boolean =
        deleteWithin(File(context.cacheDir, STAGING_DIR), path)

    fun deleteManagedImage(context: Context, path: String?): Boolean =
        deleteWithin(context.filesDir, path)

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
}
