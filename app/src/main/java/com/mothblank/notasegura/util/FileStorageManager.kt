package com.mothblank.notasegura.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileStorageManager {

    /**
     * Copies a file from a given Uri to the internal storage of the app.
     * Returns the absolute path of the saved file, or null if it fails.
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            
            // Create a unique filename
            val fileName = "receipt_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)
            
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a file given its path.
     */
    fun deleteImageFromInternalStorage(path: String?): Boolean {
        if (path == null) return false
        val file = File(path)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
