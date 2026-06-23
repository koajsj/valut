package com.offlinevault.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    // DateTimeFormatter is immutable and thread-safe, unlike SimpleDateFormat — safe to share even if
    // these are called off the main thread (e.g. from a background export).
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val fileStampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US)

    private fun localDateTime(timestamp: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

    /** Filename-safe timestamp (e.g. 20260622-1530) so repeated exports don't overwrite each other. */
    fun fileStamp(): String = fileStampFormat.format(LocalDateTime.now())

    fun relativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 604_800_000 -> "${diff / 86_400_000}天前"
            else -> dateFormat.format(localDateTime(timestamp))
        }
    }

    fun fullDate(timestamp: Long): String = dateFormat.format(localDateTime(timestamp))
}
