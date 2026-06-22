package com.mothblank.notasegura.util

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileStorageManagerTest {

    private lateinit var tempFilesDir: File
    private lateinit var stubContext: Context

    @Before
    fun setup() {
        // Create a temporary directory to act as filesDir
        tempFilesDir = Files.createTempDirectory("test_filesDir").toFile()

        // Create a simple Stub Context that only overrides getFilesDir()
        stubContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File {
                return tempFilesDir
            }
        }
    }

    @Test
    fun `deleteImageFromInternalStorage should delete file within filesDir`() {
        // Arrange
        val validFile = File(tempFilesDir, "valid_receipt.jpg")
        validFile.createNewFile()
        assertTrue("Test file should exist before deletion", validFile.exists())

        // Act
        val result = FileStorageManager.deleteImageFromInternalStorage(stubContext, validFile.absolutePath)

        // Assert
        assertTrue("Deletion should be successful", result)
        assertFalse("File should no longer exist", validFile.exists())
    }

    @Test
    fun `deleteImageFromInternalStorage should return false for path traversal attempt`() {
        // Arrange
        // Create an outside directory and file
        val outsideDir = Files.createTempDirectory("test_outsideDir").toFile()
        val maliciousFile = File(outsideDir, "secret.txt")
        maliciousFile.createNewFile()
        assertTrue("Malicious file should exist before test", maliciousFile.exists())

        // Create a path that attempts to use traversal to reach the malicious file
        // For example: <tempFilesDir>/../<outsideDir>/secret.txt
        val traversalPath = tempFilesDir.absolutePath + "/../" + outsideDir.name + "/" + maliciousFile.name

        // Act
        val result = FileStorageManager.deleteImageFromInternalStorage(stubContext, traversalPath)

        // Assert
        assertFalse("Deletion should fail due to path traversal prevention", result)
        assertTrue("Malicious file should still exist", maliciousFile.exists())
    }

    @Test
    fun `deleteImageFromInternalStorage should return false for arbitrary absolute path outside filesDir`() {
        // Arrange
        // Create an outside directory and file
        val outsideDir = Files.createTempDirectory("test_outsideDir2").toFile()
        val maliciousFile = File(outsideDir, "secret2.txt")
        maliciousFile.createNewFile()
        assertTrue("Malicious file should exist before test", maliciousFile.exists())

        // Act
        val result = FileStorageManager.deleteImageFromInternalStorage(stubContext, maliciousFile.absolutePath)

        // Assert
        assertFalse("Deletion should fail due to path not being in filesDir", result)
        assertTrue("Malicious file should still exist", maliciousFile.exists())
    }
}
