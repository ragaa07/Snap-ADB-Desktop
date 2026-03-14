package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.AppMemoryInfo
import com.ragaa.snapadb.core.adb.model.CpuRawReading
import com.ragaa.snapadb.core.adb.parser.AppMemoryParser
import com.ragaa.snapadb.core.adb.parser.CpuParser

class GetCpuStats : AdbCommand<CpuRawReading> {
    override fun args(): List<String> = listOf("shell", "cat /proc/stat")
    override fun parse(result: ProcessResult): CpuRawReading = CpuParser.parseRawReading(result.stdout)
}

class GetAppMemoryInfo(private val packageName: String) : AdbCommand<AppMemoryInfo> {
    init {
        require(packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_.]*$"))) { "Invalid package name" }
    }

    override fun args(): List<String> = listOf("shell", "dumpsys meminfo $packageName")
    override fun parse(result: ProcessResult): AppMemoryInfo = AppMemoryParser.parse(result.stdout, packageName)
}
