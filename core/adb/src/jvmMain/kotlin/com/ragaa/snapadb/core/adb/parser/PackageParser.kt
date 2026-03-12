package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.AppInfo

object PackageParser {

    private val PACKAGE_LINE = Regex("^package:(.+)=([a-zA-Z][a-zA-Z0-9_.]*)$")

    fun parseList(output: String, isSystemFilter: Boolean? = null): List<AppInfo> =
        output.lineSequence()
            .mapNotNull { line ->
                PACKAGE_LINE.matchEntire(line.trim())?.let { match ->
                    val apkPath = match.groupValues[1]
                    val packageName = match.groupValues[2]
                    val isSystem = isSystemFilter ?: apkPath.startsWith("/system/")
                    AppInfo(
                        packageName = packageName,
                        apkPath = apkPath,
                        isSystemApp = isSystem,
                    )
                }
            }
            .toList()

    fun parsePackageInfo(output: String, packageName: String): AppInfo {
        val lines = output.lines().map { it.trim() }
        val props = mutableMapOf<String, String>()

        for (line in lines) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                props[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
        }

        val versionName = props["versionName"] ?: ""
        val versionCode = props["versionCode"]?.toLongOrNull()
            ?: props["versionCode"]?.substringBefore(" ")?.toLongOrNull()
            ?: 0L
        val isSystem = output.contains("FLAG_SYSTEM") || output.contains("pkgFlags=[ SYSTEM")
        val isEnabled = !output.contains("enabled=false") &&
                !output.contains("enabledSetting=DISABLED")
        val installer = props["installerPackageName"] ?: ""
        val apkPath = props["codePath"] ?: ""

        return AppInfo(
            packageName = packageName,
            apkPath = apkPath,
            versionName = versionName,
            versionCode = versionCode,
            isSystemApp = isSystem,
            isEnabled = isEnabled,
            installerPackageName = installer,
        )
    }
}
