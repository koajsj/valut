package com.offlinevault.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    private val dateFormat = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)

    /** Filename-safe timestamp (e.g. 20260622-1530) so repeated exports don't overwrite each other. */
    fun fileStamp(): String = fileStampFormat.format(Date())

    fun relativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 604_800_000 -> "${diff / 86_400_000}天前"
            else -> dateFormat.format(Date(timestamp))
        }
    }

    fun fullDate(timestamp: Long): String = dateFormat.format(Date(timestamp))
}
