package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.FileEntry
import com.ragaa.snapadb.core.adb.parser.FileParser

private fun shellQuote(path: String): String = "'${path.replace("'", "'\\''")}'"

private val CRITICAL_PATHS = setOf("/", "/system", "/data", "/vendor", "/dev", "/proc", "/sys")

class ListFiles(private val remotePath: String) : AdbCommand<List<FileEntry>> {
    override fun args(): List<String> = listOf("shell", "ls -la ${shellQuote(remotePath)}")
    override fun parse(result: ProcessResult): List<FileEntry> =
        FileParser.parse(result.stdout, remotePath)
}

class PullFile(private val remotePath: String, private val localPath: String) : AdbCommand<String> {
    override fun args(): List<String> = listOf("pull", remotePath, localPath)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class PushFile(private val localPath: String, private val remotePath: String) : AdbCommand<String> {
    override fun args(): List<String> = listOf("push", localPath, remotePath)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class DeleteFile(private val remotePath: String) : AdbCommand<String> {
    init {
        require(remotePath.trimEnd('/') !in CRITICAL_PATHS) {
            "Refusing to delete critical path: $remotePath"
        }
        require(remotePath.length > 1) { "Refusing to delete root path" }
    }

    override fun args(): List<String> = listOf("shell", "rm -rf ${shellQuote(remotePath)}")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class MakeDirectory(private val remotePath: String) : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "mkdir -p ${shellQuote(remotePath)}")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
