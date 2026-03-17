package com.ragaa.snapadb.feature.monkey.command

import com.ragaa.snapadb.core.adb.StreamingAdbCommand
import com.ragaa.snapadb.feature.monkey.model.MonkeyOutputLine

class MonkeyStream(
    private val packageName: String,
    private val eventCount: Int,
    private val throttleMs: Int = 300,
    private val seed: Int? = null,
    private val verbosity: Int = 2,
    private val categories: List<String> = listOf("android.intent.category.LAUNCHER"),
    private val restrictToApp: Boolean = false,
) : StreamingAdbCommand<MonkeyOutputLine> {

    init {
        require(packageName.matches(PACKAGE_PATTERN)) { "Invalid package name: $packageName" }
        require(eventCount in 1..1_000_000) { "Event count must be 1..1,000,000" }
        require(throttleMs in 0..5000) { "Throttle must be 0..5000ms" }
        require(verbosity in 1..3) { "Verbosity must be 1..3" }
    }

    override fun args(): List<String> = buildList {
        add("shell")
        add("monkey")
        add("-p")
        add(packageName)
        add("--throttle")
        add(throttleMs.toString())
        if (seed != null) {
            add("-s")
            add(seed.toString())
        }
        categories.forEach { cat ->
            add("-c")
            add(cat)
        }
        if (restrictToApp) {
            add("--pct-syskeys")
            add("0")
            add("--pct-appswitch")
            add("0")
            add("--pct-anyevent")
            add("0")
        }
        repeat(verbosity) { add("-v") }
        add(eventCount.toString())
    }

    override fun parseLine(line: String): MonkeyOutputLine? {
        if (line.isBlank()) return null
        return when {
            CRASH_PATTERN.containsMatchIn(line) -> MonkeyOutputLine.CrashDetected(line)
            ANR_PATTERN.containsMatchIn(line) -> MonkeyOutputLine.ANRDetected(line)
            ABORTED_PATTERN.containsMatchIn(line) -> MonkeyOutputLine.Aborted(line)
            SUMMARY_PATTERN.containsMatchIn(line) -> {
                val count = SUMMARY_PATTERN.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                MonkeyOutputLine.Summary(line, count)
            }
            EVENT_PATTERN.containsMatchIn(line) -> MonkeyOutputLine.Event(line)
            else -> MonkeyOutputLine.Info(line)
        }
    }

    companion object {
        private val PACKAGE_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")
        private val CRASH_PATTERN = Regex("// CRASH:|FATAL EXCEPTION")
        private val ANR_PATTERN = Regex("// NOT RESPONDING:|ANR in")
        private val ABORTED_PATTERN = Regex("\\*\\* Monkey aborted")
        private val SUMMARY_PATTERN = Regex("Events injected:\\s*(\\d+)")
        private val EVENT_PATTERN = Regex(":Sending\\s+\\w+")
    }
}
