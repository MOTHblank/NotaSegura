package com.mothblank.notasegura.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {

    @Test
    fun encryptedBackup_roundTripsWithCorrectPassword() {
        val plaintext = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val encrypted = encrypt(plaintext, "correct horse battery staple")

        val restored = decrypt(encrypted, "correct horse battery staple", plaintext.size.toLong())

        assertArrayEquals(plaintext, restored)
    }

    @Test
    fun encryptedBackup_rejectsWrongPassword() {
        val encrypted = encrypt("nota segura".toByteArray(), "correct password")

        assertThrows(BackupAuthenticationException::class.java) {
            decrypt(encrypted, "wrong password", 1024)
        }
    }

    @Test
    fun encryptedBackup_rejectsTampering() {
        val encrypted = encrypt("sensitive receipt data".toByteArray(), "correct password")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(BackupAuthenticationException::class.java) {
            decrypt(encrypted, "correct password", 1024)
        }
    }

    @Test
    fun encryptedBackup_rejectsUnknownEnvelope() {
        val encrypted = encrypt("data".toByteArray(), "correct password")
        encrypted[0] = 'X'.code.toByte()

        assertThrows(IllegalArgumentException::class.java) {
            decrypt(encrypted, "correct password", 1024)
        }
    }

    private fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val output = ByteArrayOutputStream()
        BackupCrypto.openEncryptedOutput(output, password.toCharArray()).use { encrypted ->
            encrypted.write(plaintext)
        }
        return output.toByteArray()
    }

    private fun decrypt(
        encrypted: ByteArray,
        password: String,
        maxPlaintextBytes: Long
    ): ByteArray {
        val output = ByteArrayOutputStream()
        BackupCrypto.decrypt(
            input = ByteArrayInputStream(encrypted),
            output = output,
            password = password.toCharArray(),
            maxPlaintextBytes = maxPlaintextBytes
        )
        return output.toByteArray()
    }
}
