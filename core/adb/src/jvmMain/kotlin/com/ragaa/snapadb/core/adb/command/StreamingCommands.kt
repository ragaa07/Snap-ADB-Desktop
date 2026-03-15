package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.StreamingAdbCommand
import com.ragaa.snapadb.core.adb.model.LogcatEntry
import com.ragaa.snapadb.core.adb.parser.LogcatParser

class LogcatStream : StreamingAdbCommand<LogcatEntry> {
    override fun args(): List<String> = listOf("logcat", "-v", "threadtime")
    override fun parseLine(line: String): LogcatEntry? = LogcatParser.parse(line)
}

class ShellExec(private val command: String) : AdbCommand<String> {

    init {
        require(command.isNotBlank()) { "Shell command must not be blank" }
    }

    override fun args(): List<String> = listOf("shell", command)

    override fun parse(result: ProcessResult): String {
        return if (result.stderr.isNotBlank() && result.stdout.isBlank()) {
            result.stderr
        } else {
            result.stdout
        }
    }
}

class ClearLogcat : AdbCommand<String> {
    override fun args(): List<String> = listOf("logcat", "-c")
    override fun parse(result: ProcessResult): String = result.stdout
}

class DumpLogcat(private val maxLines: Int = 5000) : AdbCommand<String> {
    init {
        require(maxLines in 100..50_000) { "maxLines must be between 100 and 50,000" }
    }

    override fun args(): List<String> = listOf("logcat", "-d", "-v", "threadtime", "-t", "$maxLines")
    override fun parse(result: ProcessResult): String = result.stdout
}
