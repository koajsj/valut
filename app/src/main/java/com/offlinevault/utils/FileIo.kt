package com.offlinevault.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object FileIo {
    const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { readUtf8Limited(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun writeText(context: Context, uri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                if (stream == null) {
                    false
                } else {
                    stream.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                        it.flush()
                    }
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }

    internal fun readUtf8Limited(input: InputStream, maxBytes: Int = MAX_IMPORT_BYTES): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "导入文件超过 ${maxBytes} 字节限制" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
