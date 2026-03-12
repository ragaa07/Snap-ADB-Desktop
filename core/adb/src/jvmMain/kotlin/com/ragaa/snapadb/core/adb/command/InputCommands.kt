package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult

private val SETTING_NAMESPACE_PATTERN = Regex("^(system|secure|global)$")
private val SETTING_KEY_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_.]*$")

class SendKeyEvent(private val keyCode: Int) : AdbCommand<String> {
    init {
        require(keyCode in 0..999) { "Invalid key code: $keyCode" }
    }

    override fun args(): List<String> = listOf("shell", "input keyevent $keyCode")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SendText(private val text: String) : AdbCommand<String> {
    init {
        require(text.isNotEmpty()) { "Text must not be empty" }
        require(text.length <= 1000) { "Text too long (max 1000 characters)" }
    }

    override fun args(): List<String> {
        val escaped = text.replace(" ", "%s").replace("'", "'\\''")
        return listOf("shell", "input text '$escaped'")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SendTap(private val x: Int, private val y: Int) : AdbCommand<String> {
    init {
        require(x in 0..10000) { "Invalid x coordinate: $x" }
        require(y in 0..10000) { "Invalid y coordinate: $y" }
    }

    override fun args(): List<String> = listOf("shell", "input tap $x $y")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SendSwipe(
    private val x1: Int,
    private val y1: Int,
    private val x2: Int,
    private val y2: Int,
    private val durationMs: Int = 300,
) : AdbCommand<String> {
    init {
        require(x1 in 0..10000 && y1 in 0..10000) { "Invalid start coordinates" }
        require(x2 in 0..10000 && y2 in 0..10000) { "Invalid end coordinates" }
        require(durationMs in 1..10000) { "Invalid duration: $durationMs" }
    }

    override fun args(): List<String> = listOf("shell", "input swipe $x1 $y1 $x2 $y2 $durationMs")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetSetting(private val namespace: String, private val key: String) : AdbCommand<String> {
    init {
        require(namespace.matches(SETTING_NAMESPACE_PATTERN)) { "Invalid namespace: $namespace" }
        require(key.matches(SETTING_KEY_PATTERN)) { "Invalid setting key: $key" }
    }

    override fun args(): List<String> = listOf("shell", "settings get $namespace $key")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class PutSetting(private val namespace: String, private val key: String, private val value: String) : AdbCommand<String> {
    init {
        require(namespace.matches(SETTING_NAMESPACE_PATTERN)) { "Invalid namespace: $namespace" }
        require(key.matches(SETTING_KEY_PATTERN)) { "Invalid setting key: $key" }
        require(value.length <= 500) { "Value too long" }
        require('\n' !in value && '\r' !in value) { "Value must not contain newlines" }
    }

    override fun args(): List<String> {
        val escapedValue = value.replace("'", "'\\''")
        return listOf("shell", "settings put $namespace $key '$escapedValue'")
    }
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class RebootDevice(private val mode: String? = null) : AdbCommand<String> {
    init {
        if (mode != null) {
            require(mode in listOf("bootloader", "recovery", "sideload")) { "Invalid reboot mode: $mode" }
        }
    }

    override fun args(): List<String> = if (mode != null) listOf("reboot", mode) else listOf("reboot")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetWifiEnabled(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "svc wifi ${if (enabled) "enable" else "disable"}")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetMobileDataEnabled(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "svc data ${if (enabled) "enable" else "disable"}")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetAirplaneMode(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> {
        val value = if (enabled) "1" else "0"
        return listOf("shell", "settings put global airplane_mode_on $value && am broadcast -a android.intent.action.AIRPLANE_MODE")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetScreenDensity : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "wm density")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetScreenDensity(private val dpi: Int) : AdbCommand<String> {
    init {
        require(dpi in 100..800) { "Invalid DPI: $dpi (must be 100-800)" }
    }

    override fun args(): List<String> = listOf("shell", "wm density $dpi")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class ResetScreenDensity : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "wm density reset")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetScreenSize : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "wm size")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SetScreenSize(private val width: Int, private val height: Int) : AdbCommand<String> {
    init {
        require(width in 100..10000 && height in 100..10000) { "Invalid screen size: ${width}x$height" }
    }

    override fun args(): List<String> = listOf("shell", "wm size ${width}x$height")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class ResetScreenSize : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "wm size reset")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
