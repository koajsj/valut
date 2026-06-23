package com.offlinevault.utils

object ImportFormatDetector {
    fun looksLikeCsv(sourceName: String, content: String): Boolean {
        val lowerName = sourceName.lowercase()
        if (lowerName.endsWith(".csv")) return true

        val trimmed = content.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return false

        val firstNonBlankLine = content.lineSequence().firstOrNull { it.isNotBlank() }
        return firstNonBlankLine?.contains(",") == true
    }
}
