package com.ragaa.snapadb.feature.dbinspector

import com.ragaa.snapadb.feature.dbinspector.model.ColumnInfo
import com.ragaa.snapadb.feature.dbinspector.model.QueryResult
import com.ragaa.snapadb.feature.dbinspector.model.TableInfo
import java.io.File
import java.sql.ResultSet
import java.util.Properties

/**
 * All public methods must be called from the same dispatcher (dispatchers.io)
 * to ensure thread safety of the underlying JDBC connection.
 */
class SqliteInspector {

    private var connection: java.sql.Connection? = null
    private var dbFile: File? = null

    fun open(localDbPath: File) {
        close()
        dbFile = localDbPath
        val props = Properties().apply {
            // SQLITE_OPEN_READONLY = 1 — enforces true read-only at the driver level
            setProperty("open_mode", "1")
        }
        connection = java.sql.DriverManager.getConnection(
            "jdbc:sqlite:${localDbPath.absolutePath}",
            props,
        )
    }

    fun close() {
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
        dbFile = null
    }

    val isOpen: Boolean get() = connection != null && connection?.isClosed == false

    fun listTables(): List<String> {
        val conn = connection ?: return emptyList()
        val tables = mutableListOf<String>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
            ).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString(1))
                }
            }
        }
        return tables
    }

    fun getTableInfo(tableName: String): TableInfo {
        val conn = connection ?: error("Database not open")
        val quotedName = "\"${tableName.replace("\"", "\"\"")}\""

        // Get columns via PRAGMA
        val columns = mutableListOf<ColumnInfo>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($quotedName)").use { rs ->
                while (rs.next()) {
                    columns.add(
                        ColumnInfo(
                            cid = rs.getInt("cid"),
                            name = rs.getString("name"),
                            type = rs.getString("type"),
                            notNull = rs.getInt("notnull") == 1,
                            defaultValue = rs.getString("dflt_value"),
                            primaryKey = rs.getInt("pk") > 0,
                        )
                    )
                }
            }
        }

        // Get row count
        val rowCount = conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM $quotedName").use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }

        return TableInfo(name = tableName, rowCount = rowCount, columns = columns)
    }

    fun getTableData(tableName: String, limit: Int = 200, offset: Int = 0): QueryResult {
        val quotedName = "\"${tableName.replace("\"", "\"\"")}\""
        return executeQuery("SELECT * FROM $quotedName LIMIT $limit OFFSET $offset")
    }

    fun executeQuery(sql: String): QueryResult {
        val conn = connection ?: return QueryResult(
            columns = emptyList(),
            rows = emptyList(),
            rowCount = 0,
            executionTimeMs = 0,
            error = "Database not open",
        )

        val startTime = System.currentTimeMillis()
        return try {
            conn.createStatement().use { stmt ->
                val hasResultSet = stmt.execute(sql)
                val elapsed = System.currentTimeMillis() - startTime

                if (hasResultSet) {
                    stmt.resultSet.use { rs ->
                        val meta = rs.metaData
                        val columnCount = meta.columnCount
                        val columns = (1..columnCount).map { meta.getColumnName(it) }
                        val rows = mutableListOf<List<String>>()

                        while (rs.next()) {
                            val row = (1..columnCount).map { i ->
                                formatValue(rs, i)
                            }
                            rows.add(row)
                        }

                        QueryResult(
                            columns = columns,
                            rows = rows,
                            rowCount = rows.size,
                            executionTimeMs = elapsed,
                        )
                    }
                } else {
                    val updateCount = stmt.updateCount
                    QueryResult(
                        columns = listOf("Result"),
                        rows = listOf(listOf("$updateCount row(s) affected")),
                        rowCount = 1,
                        executionTimeMs = elapsed,
                    )
                }
            }
        } catch (e: Exception) {
            QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = e.message ?: "Unknown SQL error",
            )
        }
    }

    private fun formatValue(rs: ResultSet, index: Int): String {
        val value = rs.getObject(index) ?: return "NULL"
        return when (value) {
            is ByteArray -> "[BLOB: ${value.size} bytes]"
            else -> value.toString()
        }
    }
}
