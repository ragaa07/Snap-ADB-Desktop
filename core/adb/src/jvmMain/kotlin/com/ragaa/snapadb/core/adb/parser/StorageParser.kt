package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.StorageInfo

object StorageParser {

    private val WHITESPACE = "\\s+".toRegex()

    /**
     * Parses `adb shell df -h` output.
     * Format:
     * ```
     * Filesystem      Size  Used Avail Use% Mounted on
     * /dev/block/...  54G   12G  42G   23%  /data
     * ```
     */
    fun parse(output: String): List<StorageInfo> =
        output.lineSequence()
            .drop(1) // skip header
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): StorageInfo? {
        val parts = line.split(WHITESPACE)
        if (parts.size < 6) return null

        val usePercent = parts[4].removeSuffix("%").toIntOrNull() ?: return null

        return StorageInfo(
            filesystem = parts[0],
            size = parts[1],
            used = parts[2],
            available = parts[3],
            usePercent = usePercent,
            mountedOn = parts.drop(5).joinToString(" "),
        )
    }
}
