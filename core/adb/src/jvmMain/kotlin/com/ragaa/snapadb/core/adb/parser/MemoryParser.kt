package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.MemoryInfo

object MemoryParser {

    private val MEMINFO_PATTERN = Regex("^(\\S+):\\s+(\\d+)\\s*kB$", RegexOption.IGNORE_CASE)

    /**
     * Parses `adb shell cat /proc/meminfo` output.
     * Format:
     * ```
     * MemTotal:       3956412 kB
     * MemFree:          12345 kB
     * MemAvailable:   1234567 kB
     * Buffers:           1234 kB
     * Cached:          123456 kB
     * ...
     * ```
     */
    fun parse(output: String): MemoryInfo {
        val values = output.lineSequence()
            .mapNotNull { line -> MEMINFO_PATTERN.matchEntire(line.trim()) }
            .associate { match -> match.groupValues[1] to (match.groupValues[2].toLongOrNull() ?: 0L) }

        return MemoryInfo(
            totalKb = values["MemTotal"] ?: 0L,
            freeKb = values["MemFree"] ?: 0L,
            availableKb = values["MemAvailable"] ?: 0L,
            buffersKb = values["Buffers"] ?: 0L,
            cachedKb = values["Cached"] ?: 0L,
        )
    }
}
