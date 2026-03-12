package com.ragaa.snapadb.core.database

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
            schema = SnapAdbDatabase.Schema,
            migrateEmptySchema = true,
        )
        return SnapAdbDatabase(driver)
    }
}
