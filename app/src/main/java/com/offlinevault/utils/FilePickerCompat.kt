package com.offlinevault.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object FilePickerCompat {
    private val importMimeTypes = arrayOf(
        "application/json",
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "*/*"
    )

    fun createImportChooser(context: Context): Intent? {
        val packageManager = context.packageManager
        val candidates = listOf(
            buildOpenDocumentIntent(),
            buildGetContentIntent()
        ).filter { it.canBeHandledBy(packageManager) }

        if (candidates.isEmpty()) return null

        val primary = candidates.first()
        val alternates = candidates.drop(1).toTypedArray()

        return Intent.createChooser(primary, "选择导入文件").apply {
            if (alternates.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, alternates)
            }
        }
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

    private fun Intent.canBeHandledBy(packageManager: PackageManager): Boolean =
        resolveActivity(packageManager) != null
}
