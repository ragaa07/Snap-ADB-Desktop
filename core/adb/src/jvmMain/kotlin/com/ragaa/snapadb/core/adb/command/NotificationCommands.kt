package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult

private val TAG_PATTERN = Regex("^[a-zA-Z0-9_.-]{1,100}$")
private val EXTRAS_KEY_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_.]*$")

class PostNotification(
    private val tag: String,
    private val title: String,
    private val text: String,
) : AdbCommand<String> {
    init {
        require(tag.matches(TAG_PATTERN)) { "Invalid notification tag: $tag" }
        require(title.isNotBlank()) { "Title must not be blank" }
        require(title.length <= 200) { "Title too long (max 200)" }
        require(text.length <= 1000) { "Text too long (max 1000)" }
    }

    override fun args(): List<String> {
        val escapedTitle = title.replace("'", "'\\''")
        val escapedText = text.replace("'", "'\\''")
        return listOf("shell", "cmd notification post -S bigtext -t '$escapedTitle' '$tag' '$escapedText'")
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class CancelNotification(private val tag: String) : AdbCommand<String> {
    init {
        require(tag.matches(TAG_PATTERN)) { "Invalid notification tag: $tag" }
    }

    override fun args(): List<String> = listOf("shell", "cmd notification cancel '$tag'")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class SendBroadcast(
    private val action: String,
    private val extras: Map<String, String> = emptyMap(),
) : AdbCommand<String> {
    init {
        require(action.isNotBlank()) { "Action must not be blank" }
        require(action.length <= 300) { "Action too long" }
        extras.keys.forEach { key ->
            require(key.matches(EXTRAS_KEY_PATTERN)) { "Invalid extras key: $key" }
        }
    }

    override fun args(): List<String> {
        val cmd = buildString {
            append("am broadcast -a '${action.replace("'", "'\\''")}'")
            extras.forEach { (key, value) ->
                append(" --es '${key.replace("'", "'\\''")}'")
                append(" '${value.replace("'", "'\\''")}'")
            }
        }
        return listOf("shell", cmd)
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
