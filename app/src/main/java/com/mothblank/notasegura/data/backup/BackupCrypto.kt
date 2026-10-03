package com.mothblank.notasegura.data.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal const val MIN_BACKUP_PASSWORD_LENGTH = 8

internal class BackupAuthenticationException(
    cause: Throwable? = null
) : SecurityException("Senha incorreta ou backup adulterado.", cause)

internal object BackupCrypto {
    private val MAGIC = "NSBKP001".toByteArray(Charsets.US_ASCII)
    private const val ENVELOPE_VERSION = 1
    private const val KDF_ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val BUFFER_BYTES = 64 * 1024

    fun openEncryptedOutput(
        output: OutputStream,
        password: CharArray
    ): OutputStream {
        require(password.size >= MIN_BACKUP_PASSWORD_LENGTH) {
            "A senha do backup deve ter pelo menos $MIN_BACKUP_PASSWORD_LENGTH caracteres."
        }

        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = createHeader(salt, iv)
        val key = deriveKey(password, salt)

        return try {
            output.write(header)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(header)
            }

            CipherOutputStream(output, cipher)
        } finally {
            password.fill('\u0000')
        }
    }

    fun decrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        maxPlaintextBytes: Long
    ) {
        require(password.size >= MIN_BACKUP_PASSWORD_LENGTH) {
            "A senha do backup deve ter pelo menos $MIN_BACKUP_PASSWORD_LENGTH caracteres."
        }
        require(maxPlaintextBytes > 0L)

        try {
            val data = DataInputStream(input)
            val magic = ByteArray(MAGIC.size)
            data.readFully(magic)
            require(magic.contentEquals(MAGIC)) {
                "Formato de backup criptografado inválido."
            }

            val version = data.readInt()
            require(version == ENVELOPE_VERSION) {
                "Versão de criptografia de backup não suportada."
            }

            val iterations = data.readInt()
            require(iterations == KDF_ITERATIONS) {
                "Configuração de derivação de chave não suportada."
            }

            val salt = ByteArray(SALT_BYTES)
            val iv = ByteArray(IV_BYTES)
            data.readFully(salt)
            data.readFully(iv)

            val header = createHeader(salt, iv, iterations)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(header)
            }

            val encryptedBuffer = ByteArray(BUFFER_BYTES)
            var written = 0L

            while (true) {
                val count = data.read(encryptedBuffer)
                if (count < 0) break
                if (count == 0) continue

                val plaintext = cipher.update(encryptedBuffer, 0, count)
                if (plaintext != null && plaintext.isNotEmpty()) {
                    written = writeBounded(output, plaintext, written, maxPlaintextBytes)
                }
            }

            val finalPlaintext = cipher.doFinal()
            if (finalPlaintext.isNotEmpty()) {
                writeBounded(output, finalPlaintext, written, maxPlaintextBytes)
            }
            output.flush()
        } catch (error: AEADBadTagException) {
            throw BackupAuthenticationException(error)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun createHeader(
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int = KDF_ITERATIONS
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            write(MAGIC)
            writeInt(ENVELOPE_VERSION)
            writeInt(iterations)
            write(salt)
            write(iv)
            flush()
        }
        return bytes.toByteArray()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS)
        return try {
            val keyBytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            try {
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun writeBounded(
        output: OutputStream,
        bytes: ByteArray,
        alreadyWritten: Long,
        maxPlaintextBytes: Long
    ): Long {
        val newTotal = alreadyWritten + bytes.size
        require(newTotal <= maxPlaintextBytes) {
            "Backup excede o tamanho máximo permitido."
        }
        output.write(bytes)
        return newTotal
    }
}
