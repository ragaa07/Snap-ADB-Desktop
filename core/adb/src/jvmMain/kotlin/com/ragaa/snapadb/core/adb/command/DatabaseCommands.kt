package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult

private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")
private val SAFE_DB_FILENAME_PATTERN = Regex("^[a-zA-Z0-9_.-]+$")

private fun shellQuote(s: String): String = "'${s.replace("'", "'\\''")}'"

data class DatabaseFileInfo(val name: String, val sizeBytes: Long)

class ListDatabases(private val packageName: String) : AdbCommand<List<DatabaseFileInfo>> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> {
        val pkg = shellQuote(packageName)
        return listOf(
            "shell", "run-as $pkg ls -la /data/data/$pkg/databases/"
        )
    }

    override fun parse(result: ProcessResult): List<DatabaseFileInfo> {
        return result.stdout.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("total") }
            .mapNotNull { line ->
                // Parse ls -la output: permissions links owner group size date time name
                val parts = line.split(Regex("\\s+"), limit = 9)
                if (parts.size < 8) return@mapNotNull null
                val name = parts.last()
                // Skip directories, -journal files (old format), and hidden files
                if (parts[0].startsWith("d") || name.startsWith(".")) return@mapNotNull null
                val size = parts[4].toLongOrNull() ?: 0L
                DatabaseFileInfo(name, size)
            }
    }
}

class CopyDbToTmp(
    private val packageName: String,
    private val dbName: String,
    private val tmpName: String,
) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        require(dbName.matches(SAFE_DB_FILENAME_PATTERN)) { "Invalid db name: $dbName" }
        require(tmpName.matches(SAFE_DB_FILENAME_PATTERN)) { "Invalid tmp name: $tmpName" }
    }

    override fun args(): List<String> {
        val pkg = shellQuote(packageName)
        val quotedDb = shellQuote(dbName)
        val quotedTmp = shellQuote(tmpName)
        val src = "/data/data/$pkg/databases/$quotedDb"
        val dst = "/data/local/tmp/$quotedTmp"
        // Use cat piped through run-as to copy from app-private dir to world-writable tmp.
        // run-as gives app identity which can read app data; shell redirect writes to /data/local/tmp/.
        return listOf(
            "shell",
            "run-as $pkg cat /data/data/$pkg/databases/$quotedDb > $dst 2>/dev/null && " +
                    "chmod 644 $dst 2>/dev/null; " +
                    "run-as $pkg cat /data/data/$pkg/databases/${quotedDb}-wal > ${dst}-wal 2>/dev/null && " +
                    "chmod 644 ${dst}-wal 2>/dev/null; " +
                    "run-as $pkg cat /data/data/$pkg/databases/${quotedDb}-shm > ${dst}-shm 2>/dev/null && " +
                    "chmod 644 ${dst}-shm 2>/dev/null; " +
                    "test -f $dst && echo OK || echo FAIL"
        )
    }

    override fun parse(result: ProcessResult): String {
        val output = result.stdout.trim()
        if (output.endsWith("FAIL")) {
            error("Failed to copy database to temp directory")
        }
        return output
    }
}

class CleanupTmpDb(private val tmpName: String) : AdbCommand<String> {
    init {
        require(tmpName.matches(SAFE_DB_FILENAME_PATTERN)) { "Invalid tmp name: $tmpName" }
    }

    override fun args(): List<String> {
        val base = "/data/local/tmp/${shellQuote(tmpName)}"
        return listOf("shell", "rm -f $base ${base}-wal ${base}-shm")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
