package com.mothblank.notasegura.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class ArchiveSizeLimitOutputStreamTest {
    @Test
    fun acceptsTheExactLimit() {
        val destination = ByteArrayOutputStream()
        ArchiveSizeLimitOutputStream(destination, 4).use { stream ->
            stream.write(byteArrayOf(1, 2, 3))
            stream.write(4)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), destination.toByteArray())
    }

    @Test
    fun rejectsOversizeWriteWithoutWritingPartialData() {
        val destination = ByteArrayOutputStream()
        val stream = ArchiveSizeLimitOutputStream(destination, 3)
        stream.write(byteArrayOf(1, 2))
        assertThrows(IllegalArgumentException::class.java) {
            stream.write(byteArrayOf(3, 4))
        }
        assertArrayEquals(byteArrayOf(1, 2), destination.toByteArray())
    }
}
