package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.AppMemoryInfo
import com.ragaa.snapadb.core.adb.model.CpuRawReading
import com.ragaa.snapadb.core.adb.model.GpuInfo
import com.ragaa.snapadb.core.adb.model.NetworkIoInfo
import com.ragaa.snapadb.core.adb.parser.AppMemoryParser
import com.ragaa.snapadb.core.adb.parser.CpuParser
import com.ragaa.snapadb.core.adb.parser.GpuParser
import com.ragaa.snapadb.core.adb.parser.NetworkIoParser

private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")

class GetCpuStats : AdbCommand<CpuRawReading> {
    override fun args(): List<String> = listOf("shell", "cat /proc/stat")
    override fun parse(result: ProcessResult): CpuRawReading = CpuParser.parseRawReading(result.stdout)
}

class GetAppMemoryInfo(private val packageName: String) : AdbCommand<AppMemoryInfo> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name" }
    }

    override fun args(): List<String> = listOf("shell", "dumpsys meminfo $packageName")
    override fun parse(result: ProcessResult): AppMemoryInfo = AppMemoryParser.parse(result.stdout, packageName)
}

class GetGpuInfo(private val packageName: String) : AdbCommand<GpuInfo?> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name" }
    }

    override fun args(): List<String> = listOf("shell", "dumpsys gfxinfo $packageName")
    override fun parse(result: ProcessResult): GpuInfo? = GpuParser.parse(result.stdout)
}

class GetAppUid(private val packageName: String) : AdbCommand<Int?> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name" }
    }

    override fun args(): List<String> = listOf("shell", "dumpsys package $packageName")
    override fun parse(result: ProcessResult): Int? = NetworkIoParser.parseUid(result.stdout)
}

class GetNetworkStats : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "cat /proc/net/xt_qtaguid/stats")
    override fun parse(result: ProcessResult): String = result.stdout
}

class GetNetworkStatsDev : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "cat /proc/net/dev")
    override fun parse(result: ProcessResult): String = result.stdout
}
