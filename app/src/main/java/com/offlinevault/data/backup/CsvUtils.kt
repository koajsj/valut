package com.offlinevault.data.backup

/** Minimal RFC-4180 style CSV reader/writer — enough for Chrome exports and our own CSV. */
object CsvUtils {

    /** Parses CSV text into rows of fields, honouring quoted fields and embedded commas/newlines. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val s = text.replace("\r\n", "\n").replace("\r", "\n")
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < s.length && s[i + 1] == '"') {
                            field.append('"'); i++
                        } else inQuotes = false
                    } else field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> {
                    row.add(field.toString()); field = StringBuilder()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!inQuotes) { "CSV contains an unterminated quoted field" }
        // flush trailing field/row
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { it.any { f -> f.isNotBlank() } }
    }

    // Leading characters a spreadsheet may interpret as the start of a formula (CSV injection).
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@')
    private val FORMULA_PREFIX_WHITESPACE = setOf(' ', '\t', '\r', '\n')

    fun escape(value: String): String {
        // Neutralise CSV/formula injection: a cell that opens with a formula trigger gets a leading
        // apostrophe so Excel / Sheets render it as literal text instead of evaluating it.
        val guarded = if (startsLikeFormula(value)) "'$value" else value
        return if (guarded.contains(',') || guarded.contains('"') || guarded.contains('\n')) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else guarded
    }

    fun buildRow(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    private fun startsLikeFormula(value: String): Boolean {
        val firstSignificant = value.dropWhile { it in FORMULA_PREFIX_WHITESPACE }.firstOrNull()
        return firstSignificant != null && firstSignificant in FORMULA_TRIGGERS
    }
}
