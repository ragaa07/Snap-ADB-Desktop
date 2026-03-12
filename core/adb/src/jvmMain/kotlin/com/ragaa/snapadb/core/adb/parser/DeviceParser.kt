package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.AdbDevice
import com.ragaa.snapadb.core.adb.model.DeviceState

object DeviceParser {

    private val WHITESPACE = "\\s+".toRegex()

    /**
     * Parses the output of `adb devices -l`.
     *
     * Example line:
     * `emulator-5554 device product:sdk_gphone model:sdk_gphone device:generic transport_id:1`
     */
    fun parse(output: String): List<AdbDevice> =
        output.lineSequence()
            .drop(1) // skip "List of devices attached" header
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("List of") }
            .mapNotNull { parseLine(it) }
            .toList()

    private fun parseLine(line: String): AdbDevice? {
        // Format: <serial> <state> [key:value ...]
        val parts = line.split(WHITESPACE)
        if (parts.size < 2) return null

        val serial = parts[0]

        // Handle "no permissions" which splits into two tokens
        val (state, propertiesStartIndex) = if (parts.size > 2 && parts[1] == "no") {
            "${parts[1]} ${parts[2]}" to 3
        } else {
            parts[1] to 2
        }

        val properties = parts.drop(propertiesStartIndex).associate { token ->
            val (key, value) = if (":" in token) {
                val idx = token.indexOf(':')
                token.substring(0, idx) to token.substring(idx + 1)
            } else {
                token to ""
            }
            key to value
        }

        return AdbDevice(
            serial = serial,
            state = DeviceState.fromString(state),
            product = properties["product"] ?: "",
            model = properties["model"] ?: "",
            device = properties["device"] ?: "",
            transportId = properties["transport_id"] ?: ""
        )
    }
}
