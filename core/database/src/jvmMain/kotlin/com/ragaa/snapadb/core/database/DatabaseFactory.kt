package com.ragaa.snapadb.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ragaa.snapadb.database.SnapAdbDatabase
import java.io.File
import java.util.Properties

object DatabaseFactory {
    fun create(): SnapAdbDatabase {
        val dbDir = File(System.getProperty("user.home"), ".snapadb")
        check(dbDir.exists() || dbDir.mkdirs()) { "Failed to create database directory: ${dbDir.absolutePath}" }
        val dbFile = File(dbDir, "snapadb.db")
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties(),
        )
        migrateIfNeeded(driver, SnapAdbDatabase.Schema)
        return SnapAdbDatabase(driver)
    }

    private fun migrateIfNeeded(driver: JdbcSqliteDriver, schema: SqlSchema<QueryResult.Value<Unit>>) {
        val currentVersion = currentDbVersion(driver)
        val schemaVersion = schema.version
        if (currentVersion == 0L) {
            schema.create(driver)
            setDbVersion(driver, schemaVersion)
        } else if (currentVersion < schemaVersion) {
            schema.migrate(driver, currentVersion, schemaVersion)
            setDbVersion(driver, schemaVersion)
        }
    }

    private fun currentDbVersion(driver: JdbcSqliteDriver): Long {
        val cursor = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0)
        return cursor.value
    }

    private fun setDbVersion(driver: JdbcSqliteDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}
