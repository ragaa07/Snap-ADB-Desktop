package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.NetworkIoInfo

object NetworkIoParser {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Parse /proc/net/xt_qtaguid/stats and sum rx_bytes/tx_bytes for a given UID.
     * Format: idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets ...
     */
    fun parse(output: String, uid: Int, timestampMs: Long): NetworkIoInfo {
        var rxTotal = 0L
        var txTotal = 0L

        for (line in output.lineSequence()) {
            if (line.startsWith("idx")) continue // header
            val parts = line.trim().split(WHITESPACE)
            if (parts.size < 9) continue
            val lineUid = parts[3].toIntOrNull() ?: continue
            if (lineUid != uid) continue
            rxTotal += parts[5].toLongOrNull() ?: 0L
            txTotal += parts[7].toLongOrNull() ?: 0L
        }

        return NetworkIoInfo(
            rxBytes = rxTotal,
            txBytes = txTotal,
            timestampMs = timestampMs,
        )
    }

    /**
     * Fallback: parse /proc/net/dev for device-level totals.
     * Format: iface: rx_bytes rx_packets ... tx_bytes tx_packets ...
     */
    fun parseDevLevel(output: String, timestampMs: Long): NetworkIoInfo {
        var rxTotal = 0L
        var txTotal = 0L

        for (line in output.lineSequence()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val iface = line.substring(0, colonIdx).trim()
            if (iface == "lo") continue // skip loopback
            val parts = line.substring(colonIdx + 1).trim().split(WHITESPACE)
            if (parts.size < 10) continue
            rxTotal += parts[0].toLongOrNull() ?: 0L
            txTotal += parts[8].toLongOrNull() ?: 0L
        }

        return NetworkIoInfo(
            rxBytes = rxTotal,
            txBytes = txTotal,
            timestampMs = timestampMs,
        )
    }

    /**
     * Extract userId from `dumpsys package <pkg>` output.
     */
    fun parseUid(output: String): Int? {
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("userId=")) {
                return trimmed.removePrefix("userId=").trim().toIntOrNull()
            }
        }
        return null
    }
}
