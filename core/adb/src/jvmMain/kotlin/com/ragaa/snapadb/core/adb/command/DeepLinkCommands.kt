package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult

private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")
private val URI_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://.+$")

class FireDeepLink(
    private val uri: String,
    private val packageName: String? = null,
) : AdbCommand<String> {
    init {
        require(uri.matches(URI_PATTERN)) { "Invalid URI: $uri" }
        if (packageName != null) {
            require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
        }
    }

    override fun args(): List<String> {
        val cmd = buildString {
            append("am start -a android.intent.action.VIEW -d '${uri.replace("'", "'\\''")}'")
            if (packageName != null) {
                append(" -p $packageName")
            }
        }
        return listOf("shell", cmd)
    }

    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
