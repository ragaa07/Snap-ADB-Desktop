package com.ragaa.snapadb.feature.dbinspector.model

data class TableInfo(
    val name: String,
    val rowCount: Long,
    val columns: List<ColumnInfo>,
)

data class ColumnInfo(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val primaryKey: Boolean,
)

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val error: String? = null,
)
