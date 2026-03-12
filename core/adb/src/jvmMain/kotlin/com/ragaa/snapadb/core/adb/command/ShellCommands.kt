package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.StorageInfo
import com.ragaa.snapadb.core.adb.parser.BatteryParser
import com.ragaa.snapadb.core.adb.parser.MemoryParser
import com.ragaa.snapadb.core.adb.parser.PropertyParser
import com.ragaa.snapadb.core.adb.parser.StorageParser

class GetProperties : AdbCommand<Map<String, String>> {
    override fun args(): List<String> = listOf("shell", "getprop")
    override fun parse(result: ProcessResult): Map<String, String> = PropertyParser.parse(result.stdout)
}

class GetBatteryInfo : AdbCommand<BatteryInfo> {
    override fun args(): List<String> = listOf("shell", "dumpsys", "battery")
    override fun parse(result: ProcessResult): BatteryInfo = BatteryParser.parse(result.stdout)
}

class GetStorageInfo : AdbCommand<List<StorageInfo>> {
    override fun args(): List<String> = listOf("shell", "df", "-h")
    override fun parse(result: ProcessResult): List<StorageInfo> = StorageParser.parse(result.stdout)
}

class GetMemoryInfo : AdbCommand<MemoryInfo> {
    override fun args(): List<String> = listOf("shell", "cat", "/proc/meminfo")
    override fun parse(result: ProcessResult): MemoryInfo = MemoryParser.parse(result.stdout)
}
