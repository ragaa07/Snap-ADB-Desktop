package com.ragaa.snapadb.core.adb.model

enum class LogLevel(val label: String) {
    VERBOSE("V"),
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
    FATAL("F"),
    SILENT("S");

    companion object {
        fun fromChar(c: Char): LogLevel = when (c) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'F' -> FATAL
            'S' -> SILENT
            else -> VERBOSE
        }
    }
}

data class LogcatEntry(
    val timestamp: String,
    val pid: String,
    val tid: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
)
