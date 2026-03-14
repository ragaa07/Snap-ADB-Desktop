package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult

class TakeScreenshot(private val remotePath: String) : AdbCommand<String> {
    init {
        require(remotePath.startsWith("/sdcard/")) { "Screenshot path must start with /sdcard/" }
        require('\u0000' !in remotePath) { "Path must not contain null bytes" }
    }

    override fun args(): List<String> = listOf("shell", "screencap -p $remotePath")
    override fun parse(result: ProcessResult): String = remotePath
}

class StartScreenRecord(
    private val remotePath: String,
    private val timeLimitSecs: Int = 180,
    private val bitrate: Int? = null,
    private val resolution: String? = null,
) : AdbCommand<String> {
    init {
        require(remotePath.startsWith("/sdcard/")) { "Recording path must start with /sdcard/" }
        require(timeLimitSecs in 1..180) { "Time limit must be 1-180 seconds" }
        require('\u0000' !in remotePath) { "Path must not contain null bytes" }
        if (bitrate != null) require(bitrate in 100_000..200_000_000) { "Invalid bitrate" }
        if (resolution != null) require(resolution.matches(Regex("^\\d+x\\d+$"))) { "Invalid resolution format" }
    }

    override fun args(): List<String> = buildList {
        add("shell")
        val cmd = buildString {
            append("screenrecord")
            append(" --time-limit $timeLimitSecs")
            if (bitrate != null) append(" --bit-rate $bitrate")
            if (resolution != null) append(" --size $resolution")
            append(" $remotePath")
        }
        add(cmd)
    }

    override fun parse(result: ProcessResult): String = remotePath
}

class StopScreenRecord : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "pkill -2 screenrecord")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class RemoveRemoteFile(private val path: String) : AdbCommand<String> {
    init {
        require(path.startsWith("/sdcard/snapadb_")) { "Can only remove files with /sdcard/snapadb_ prefix" }
        require('\u0000' !in path) { "Path must not contain null bytes" }
    }

    override fun args(): List<String> = listOf("shell", "rm '$path'")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetAutoRotateSetting : AdbCommand<Boolean> {
    override fun args(): List<String> = listOf("shell", "settings get system accelerometer_rotation")
    override fun parse(result: ProcessResult): Boolean = result.stdout.trim() == "1"
}

class SetAutoRotate(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "settings put system accelerometer_rotation ${if (enabled) "1" else "0"}")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetUserRotation(private val rotation: Int) : AdbCommand<String> {
    init {
        require(rotation in 0..3) { "Rotation must be 0-3" }
    }

    override fun args(): List<String> =
        listOf("shell", "settings put system user_rotation $rotation")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetStayAwakeSetting : AdbCommand<Int> {
    override fun args(): List<String> = listOf("shell", "settings get global stay_on_while_plugged_in")
    override fun parse(result: ProcessResult): Int = result.stdout.trim().toIntOrNull() ?: 0
}

class SetStayAwake(private val mode: Int) : AdbCommand<String> {
    init {
        require(mode in 0..7) { "Stay awake mode must be 0-7" }
    }

    override fun args(): List<String> =
        listOf("shell", "settings put global stay_on_while_plugged_in $mode")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
