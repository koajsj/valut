package com.offlinevault.utils

import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileIoTest {
    @Test fun readsUtf8WithinLimit() {
        val input = ByteArrayInputStream("密码库".toByteArray(Charsets.UTF_8))
        assertEquals("密码库", FileIo.readUtf8Limited(input, 32))
    }

    @Test fun rejectsOversizedInput() {
        val input = ByteArrayInputStream(ByteArray(33))
        assertThrows(IllegalArgumentException::class.java) {
            FileIo.readUtf8Limited(input, 32)
        }
    }

    @Test fun rejectsInvalidUtf8() {
        val input = ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))
        assertThrows(CharacterCodingException::class.java) {
            FileIo.readUtf8Limited(input, 32)
        }
    }
}
