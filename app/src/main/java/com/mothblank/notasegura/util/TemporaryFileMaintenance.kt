package com.mothblank.notasegura.util

import android.content.Context
import java.io.File

object TemporaryFileMaintenance {
    private const val MAX_TEMP_AGE_MS = 24L * 60L * 60L * 1000L

    fun cleanup(context: Context, now: Long = System.currentTimeMillis()) {
        deleteStaleFiles(File(context.cacheDir, "camera"), now)
        deleteStaleFiles(File(context.cacheDir, "exports"), now)
        deleteStaleFiles(File(context.cacheDir, "attachment_staging"), now)

        context.cacheDir
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith("restore_") &&
                    file.name.endsWith(".notasegura")
            }
            .forEach { file ->
                deleteIfStale(file, now)
            }
    }

    private fun deleteStaleFiles(directory: File, now: Long) {
        directory.listFiles().orEmpty().forEach { file ->
            if (file.isFile) {
                deleteIfStale(file, now)
            } else if (file.isDirectory) {
                deleteStaleFiles(file, now)
                if (file.listFiles().isNullOrEmpty()) {
                    file.delete()
                }
            }
        }
    }

    private fun deleteIfStale(file: File, now: Long) {
        val lastModified = file.lastModified()
        if (lastModified <= 0L || now - lastModified >= MAX_TEMP_AGE_MS) {
            file.delete()
        }
    }
}
