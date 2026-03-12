package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.LogLevel
import com.ragaa.snapadb.core.adb.model.LogcatEntry

object LogcatParser {

    // Matches: "MM-DD HH:MM:SS.mmm  PID  TID L TAG     : message"
    private val THREADTIME_REGEX = Regex(
        """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+(.+?)\s*:\s(.*)$"""
    )

    fun parse(line: String): LogcatEntry? {
        val match = THREADTIME_REGEX.matchEntire(line) ?: return null
        val (timestamp, pid, tid, level, tag, message) = match.destructured
        return LogcatEntry(
            timestamp = timestamp,
            pid = pid,
            tid = tid,
            level = LogLevel.fromChar(level[0]),
            tag = tag.trim(),
            message = message,
        )
    }
}
