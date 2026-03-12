package com.ragaa.snapadb.core.adb.parser

object PropertyParser {

    private val PROPERTY_PATTERN = Regex("^\\[(.+?)]: \\[(.*)]\$")

    /**
     * Parses `adb shell getprop` output.
     * Format: `[key]: [value]`
     */
    fun parse(output: String): Map<String, String> =
        output.lineSequence()
            .mapNotNull { line -> PROPERTY_PATTERN.matchEntire(line.trim()) }
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
}
