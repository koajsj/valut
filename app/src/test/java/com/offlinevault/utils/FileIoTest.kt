package com.offlinevault.utils

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FileIoTest {
    @Test fun readsUtf8WithinLimit() {
        val input = ByteArrayInputStream("密码库".toByteArray())
        assertEquals("密码库", FileIo.readUtf8Limited(input, 32))
    }

    @Test fun rejectsOversizedInput() {
        val input = ByteArrayInputStream(ByteArray(33))
        assertThrows(IllegalArgumentException::class.java) {
            FileIo.readUtf8Limited(input, 32)
        }
    }
}
