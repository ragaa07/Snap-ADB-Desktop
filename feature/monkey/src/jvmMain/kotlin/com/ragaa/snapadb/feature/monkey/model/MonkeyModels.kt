package com.ragaa.snapadb.feature.monkey.model

data class MonkeyConfig(
    val id: Long = 0,
    val name: String = "",
    val packageName: String = "",
    val eventCount: Int = 500,
    val seed: Int? = null,
    val throttleMs: Int = 300,
    val categories: List<String> = listOf("android.intent.category.LAUNCHER"),
    val verbosity: Int = 2,
    val restrictToApp: Boolean = false,
)

enum class MonkeyRunStatus {
    Running,
    Completed,
    Crashed,
    ANR,
    Aborted,
    Stopped,
}

data class MonkeyRunSummary(
    val id: Long,
    val configName: String,
    val packageName: String,
    val deviceSerial: String,
    val startedAt: Long,
    val endedAt: Long?,
    val totalEvents: Int,
    val injectedEvents: Int,
    val status: MonkeyRunStatus,
    val crashLog: String?,
    val seed: Int?,
)

sealed class MonkeyOutputLine(val text: String) {
    class Event(text: String) : MonkeyOutputLine(text)
    class CrashDetected(text: String) : MonkeyOutputLine(text)
    class ANRDetected(text: String) : MonkeyOutputLine(text)
    class Summary(text: String, val injectedEvents: Int) : MonkeyOutputLine(text)
    class Aborted(text: String) : MonkeyOutputLine(text)
    class Info(text: String) : MonkeyOutputLine(text)
}
