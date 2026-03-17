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

// === Developer Options Commands ===

private val ANIMATION_SCALE_PATTERN = Regex("^(0(\\.0)?|0\\.5|1(\\.0)?|2(\\.0)?|5(\\.0)?|10(\\.0)?)$")

class GetAnimationScale : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "settings get global window_animation_scale")
    override fun parse(result: ProcessResult): String {
        val value = result.stdout.trim()
        return if (value == "null" || value.isBlank()) "1.0" else value
    }
}

class SetAllAnimationScales(private val scale: String) : AdbCommand<String> {
    init {
        require(scale.matches(ANIMATION_SCALE_PATTERN)) { "Invalid animation scale: $scale" }
    }

    override fun args(): List<String> = listOf(
        "shell",
        "settings put global window_animation_scale $scale && " +
            "settings put global transition_animation_scale $scale && " +
            "settings put global animator_duration_scale $scale",
    )

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetDarkMode : AdbCommand<Boolean?> {
    override fun args(): List<String> = listOf("shell", "cmd uimode night")
    override fun parse(result: ProcessResult): Boolean? {
        val output = result.stdout.trim().lowercase()
        return when {
            "yes" in output -> true
            "no" in output -> false
            else -> null
        }
    }
}

class SetDarkMode(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "cmd uimode night ${if (enabled) "yes" else "no"}")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetDontKeepActivities : AdbCommand<Boolean> {
    override fun args(): List<String> = listOf("shell", "settings get global always_finish_activities")
    override fun parse(result: ProcessResult): Boolean = result.stdout.trim() == "1"
}

class SetDontKeepActivities(private val enabled: Boolean) : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "settings put global always_finish_activities ${if (enabled) "1" else "0"}")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetFontScale : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "settings get system font_scale")
    override fun parse(result: ProcessResult): String {
        val value = result.stdout.trim()
        return if (value == "null" || value.isBlank()) "1.0" else value
    }
}

class SetFontScale(private val scale: String) : AdbCommand<String> {
    companion object {
        val ALLOWED_SCALES = setOf("0.85", "1.0", "1.15", "1.3", "1.5", "2.0")
    }

    init {
        require(scale in ALLOWED_SCALES) { "Invalid font scale: $scale" }
    }

    override fun args(): List<String> =
        listOf("shell", "settings put system font_scale $scale")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

private val LOCALE_PATTERN = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{1,8}){0,3}$")

class GetLocale : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "settings get system system_locales")
    override fun parse(result: ProcessResult): String {
        val value = result.stdout.trim()
        return if (value == "null" || value.isBlank()) "" else value
    }
}

class SetLocale(private val locale: String) : AdbCommand<String> {
    init {
        require(locale.isNotBlank()) { "Locale must not be blank" }
        require(locale.length <= 35) { "Locale too long" }
        require(locale.matches(LOCALE_PATTERN)) { "Invalid locale format: $locale (expected e.g. en-US, ar-EG)" }
    }

    override fun args(): List<String> {
        val escaped = locale.replace("'", "'\\''")
        return listOf("shell", "settings put system system_locales '$escaped'")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

// === Individual Animation Scale Commands ===

class GetIndividualAnimationScale(private val type: String) : AdbCommand<String> {
    companion object {
        const val WINDOW = "window_animation_scale"
        const val TRANSITION = "transition_animation_scale"
        const val ANIMATOR = "animator_duration_scale"
    }

    init {
        require(type in listOf(WINDOW, TRANSITION, ANIMATOR)) { "Invalid animation scale type: $type" }
    }

    override fun args(): List<String> = listOf("shell", "settings get global $type")
    override fun parse(result: ProcessResult): String {
        val value = result.stdout.trim()
        return if (value == "null" || value.isBlank()) "1.0" else value
    }
}

class SetIndividualAnimationScale(private val type: String, private val scale: String) : AdbCommand<String> {
    init {
        require(type in listOf(
            GetIndividualAnimationScale.WINDOW,
            GetIndividualAnimationScale.TRANSITION,
            GetIndividualAnimationScale.ANIMATOR,
        )) { "Invalid animation scale type: $type" }
        require(scale.matches(ANIMATION_SCALE_PATTERN)) { "Invalid animation scale: $scale" }
    }

    override fun args(): List<String> = listOf("shell", "settings put global $type $scale")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

// === Background Process Limit Commands ===

class GetBackgroundProcessLimit : AdbCommand<Int> {
    override fun args(): List<String> = listOf("shell", "settings get global background_process_limit")
    override fun parse(result: ProcessResult): Int {
        val value = result.stdout.trim()
        return when {
            value == "null" || value.isBlank() -> -1
            else -> value.toIntOrNull() ?: -1
        }
    }
}

class SetBackgroundProcessLimit(private val limit: Int) : AdbCommand<String> {
    init {
        require(limit in -1..4) { "Invalid background process limit: $limit (must be -1 to 4)" }
    }

    override fun args(): List<String> {
        return if (limit == -1) {
            listOf("shell", "settings delete global background_process_limit")
        } else {
            listOf("shell", "settings put global background_process_limit $limit")
        }
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

// === Debug Property Commands ===

private val DEBUG_PROP_PATTERN = Regex("^[a-zA-Z0-9._]+$")
private val DEBUG_PROP_VALUE_PATTERN = Regex("^[a-zA-Z0-9._-]+$")

class SetDebugProp(private val prop: String, private val value: String) : AdbCommand<String> {
    init {
        require(prop.matches(DEBUG_PROP_PATTERN)) { "Invalid property name: $prop" }
        require(value.length <= 92) { "Property value too long" }
        require(value.matches(DEBUG_PROP_VALUE_PATTERN)) { "Invalid property value: $value" }
    }

    override fun args(): List<String> {
        val escapedValue = value.replace("'", "'\\''")
        return listOf("shell", "setprop $prop '$escapedValue'")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class GetDebugProp(private val prop: String) : AdbCommand<String> {
    init {
        require(prop.matches(DEBUG_PROP_PATTERN)) { "Invalid property name: $prop" }
    }

    override fun args(): List<String> = listOf("shell", "getprop $prop")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

// === HTTP Proxy Commands ===

private val PROXY_HOST_PATTERN = Regex("^[a-zA-Z0-9._-]+$")

class GetHttpProxy : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "settings get global http_proxy")
    override fun parse(result: ProcessResult): String {
        val value = result.stdout.trim()
        return if (value == "null" || value == ":0" || value.isBlank()) "" else value
    }
}

class SetHttpProxy(private val host: String, private val port: Int) : AdbCommand<String> {
    init {
        require(host.isNotBlank()) { "Host must not be blank" }
        require(host.length <= 253) { "Host too long" }
        require(host.matches(PROXY_HOST_PATTERN)) { "Invalid host: $host" }
        require(port in 1..65535) { "Invalid port: $port (must be 1-65535)" }
    }

    override fun args(): List<String> =
        listOf("shell", "settings put global http_proxy '$host:$port'")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class ClearHttpProxy : AdbCommand<String> {
    override fun args(): List<String> = listOf("shell", "settings put global http_proxy :0")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

// === Certificate Installation Commands ===

private val CERT_PATH_PATTERN = Regex("^[a-zA-Z0-9/_.-]+$")

class ListUserCerts : AdbCommand<List<String>> {
    override fun args(): List<String> = listOf("shell", "ls /data/misc/user/0/cacerts-added/ 2>/dev/null")
    override fun parse(result: ProcessResult): List<String> {
        return result.stdout.trim().lines().filter { it.isNotBlank() && it != "ls: /data/misc/user/0/cacerts-added/: No such file or directory" }
    }
}

class InstallUserCert(private val localCertPath: String) : AdbCommand<String> {
    init {
        require(localCertPath.isNotBlank()) { "Certificate path must not be blank" }
    }

    override fun args(): List<String> =
        listOf("shell", "am start -a android.credentials.INSTALL -t application/x-x509-ca-cert -d file:///sdcard/Download/.snapadb_cert_tmp")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class OpenSecuritySettings : AdbCommand<String> {
    override fun args(): List<String> =
        listOf("shell", "am start -a android.settings.SECURITY_SETTINGS")

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
