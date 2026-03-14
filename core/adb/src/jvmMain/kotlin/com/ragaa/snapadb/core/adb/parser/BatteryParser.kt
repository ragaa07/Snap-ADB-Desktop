package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.BatteryStatus
import com.ragaa.snapadb.core.adb.model.PlugType

object BatteryParser {

    /**
     * Parses `adb shell dumpsys battery` output.
     * Format:
     * ```
     * Current Battery Service state:
     *   AC powered: false
     *   USB powered: true
     *   status: 2
     *   health: 2
     *   level: 85
     *   temperature: 250
     *   ...
     * ```
     */
    fun parse(output: String): BatteryInfo {
        val props = output.lineSequence()
            .map { it.trim() }
            .filter { ":" in it }
            .associate { line ->
                val idx = line.indexOf(':')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }

        return BatteryInfo(
            level = props["level"]?.toIntOrNull() ?: 0,
            status = BatteryStatus.fromInt(props["status"]?.toIntOrNull() ?: 0),
            health = healthString(props["health"]?.toIntOrNull() ?: 0),
            plugged = PlugType.fromInt(props["plugged"]?.toIntOrNull() ?: 0),
            temperature = (props["temperature"]?.toFloatOrNull() ?: 0f) / 10f,
            voltage = props["voltage"]?.toIntOrNull() ?: 0,
            currentNow = props["current now"]?.toIntOrNull() ?: 0,
        )
    }

    private fun healthString(value: Int): String = when (value) {
        1 -> "Unknown"
        2 -> "Good"
        3 -> "Overheat"
        4 -> "Dead"
        5 -> "Over voltage"
        6 -> "Unspecified failure"
        7 -> "Cold"
        else -> "Unknown"
    }
}
