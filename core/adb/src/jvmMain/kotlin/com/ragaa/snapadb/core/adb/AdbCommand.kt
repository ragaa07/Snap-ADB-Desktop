package com.ragaa.snapadb.core.adb

data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

interface AdbCommand<T> {
    fun args(): List<String>
    fun parse(result: ProcessResult): T
}

interface StreamingAdbCommand<T> {
    fun args(): List<String>
    fun parseLine(line: String): T?
}
