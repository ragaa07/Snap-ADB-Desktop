package com.ragaa.snapadb.core.adb.parser

import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.model.AppPermission

object PackageParser {

    private val PACKAGE_LINE = Regex("^package:(.+)=([a-zA-Z][a-zA-Z0-9_.]*)$")
    private val INSTALL_TIME_REGEX = Regex("firstInstallTime=(\\d+)")
    private val SIZE_REGEX = Regex("(codeSize|dataSize|cacheSize)=(\\d+)")
    private val GRANTED_PERMISSION_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9_.]*): granted=(true|false)")

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

        val sizes = mutableMapOf<String, Long>()
        for (line in lines) {
            SIZE_REGEX.find(line)?.let { match ->
                sizes[match.groupValues[1]] = match.groupValues[2].toLongOrNull() ?: -1
            }
        }

        val firstInstallTime = INSTALL_TIME_REGEX.find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        return AppInfo(
            packageName = packageName,
            apkPath = apkPath,
            versionName = versionName,
            versionCode = versionCode,
            isSystemApp = isSystem,
            isEnabled = isEnabled,
            installerPackageName = installer,
            codeSize = sizes["codeSize"] ?: -1,
            dataSize = sizes["dataSize"] ?: -1,
            cacheSize = sizes["cacheSize"] ?: -1,
            firstInstallTime = firstInstallTime,
        )
    }

    fun parsePermissions(output: String): List<AppPermission> {
        val permissions = mutableListOf<AppPermission>()
        var inRequestedSection = false
        var inInstallSection = false

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("requested permissions:")) {
                inRequestedSection = true
                inInstallSection = false
                continue
            }
            if (trimmed.startsWith("install permissions:") || trimmed.startsWith("runtime permissions:")) {
                inRequestedSection = false
                inInstallSection = true
                continue
            }
            if (trimmed.startsWith("Packages:") || trimmed.startsWith("Queries:") ||
                (trimmed.isNotEmpty() && !trimmed.startsWith("android.permission") && !trimmed.contains("granted="))
            ) {
                if (inRequestedSection || inInstallSection) {
                    if (!trimmed.startsWith("android.") && !trimmed.startsWith("com.") && !trimmed.startsWith("granted=")) {
                        inRequestedSection = false
                        inInstallSection = false
                    }
                }
            }

            if (inInstallSection) {
                GRANTED_PERMISSION_REGEX.find(trimmed)?.let { match ->
                    permissions.add(
                        AppPermission(
                            name = match.groupValues[1],
                            isGranted = match.groupValues[2] == "true",
                        )
                    )
                }
            }
        }
        return permissions
    }

}
