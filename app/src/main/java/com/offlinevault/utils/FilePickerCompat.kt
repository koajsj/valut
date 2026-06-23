package com.offlinevault.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object FilePickerCompat {
    /**
     * MIME types offered to the picker. Backup files are detected by content afterwards, so we keep
     * a wildcard in the list to guarantee a file stays selectable even when a storage provider
     * reports an unexpected MIME (common on third-party ROMs).
     */
    val importMimeTypes = arrayOf(
        "application/json",
        "text/csv",
        "text/comma-separated-values",
        "text/plain",
        "*/*"
    )

    /**
     * Fallback chooser for the rare device whose system DocumentsUI (ACTION_OPEN_DOCUMENT) is missing.
     * Uses ACTION_GET_CONTENT, which more apps respond to.
     */
    fun createFallbackImportChooser(): Intent =
        Intent.createChooser(buildGetContentIntent(), "选择导入文件")

    /**
     * Bare ACTION_GET_CONTENT without the chooser wrapper. Last resort for ROMs where even the
     * system chooser refuses to launch.
     */
    fun createDirectGetContentIntent(): Intent = buildGetContentIntent()

    fun extractUri(resultData: Intent?): Uri? = resultData?.data

    fun persistReadPermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    private fun buildGetContentIntent(): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, importMimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
