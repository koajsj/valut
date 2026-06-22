package com.offlinevault.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object FilePickerCompat {
    private val importMimeTypes = arrayOf(
        "application/json",
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "*/*"
    )

    fun createImportChooser(): Intent =
        Intent.createChooser(buildGetContentIntent(), "选择导入文件").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(buildOpenDocumentIntent()))
        }

    fun extractUri(resultData: Intent?): Uri? = resultData?.data

    fun persistReadPermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    private fun buildOpenDocumentIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, importMimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

    private fun buildGetContentIntent(): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, importMimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
