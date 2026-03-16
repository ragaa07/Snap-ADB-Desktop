package com.ragaa.snapadb.feature.dbinspector

import com.ragaa.snapadb.feature.dbinspector.model.QueryResult
import java.io.File
import java.io.StringWriter
import java.io.Writer

object CsvExporter {

    fun export(result: QueryResult, file: File) {
        file.bufferedWriter().use { writer ->
            writeCsv(writer, result.columns, result.rows)
        }
    }

    fun toCsvString(result: QueryResult): String {
        val writer = StringWriter()
        writeCsv(writer, result.columns, result.rows)
        return writer.toString()
    }

    private fun writeCsv(writer: Writer, columns: List<String>, rows: List<List<String>>) {
        writer.appendLine(columns.joinToString(",") { escapeField(it) })
        rows.forEach { row ->
            writer.appendLine(row.joinToString(",") { escapeField(it) })
        }
    }

    // RFC 4180: fields containing commas, quotes, or newlines must be enclosed in double quotes.
    // Double quotes within fields are escaped by doubling them.
    private fun escapeField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
