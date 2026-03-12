package com.ragaa.snapadb.core.adb.command

import com.ragaa.snapadb.core.adb.AdbCommand
import com.ragaa.snapadb.core.adb.ProcessResult
import com.ragaa.snapadb.core.adb.model.AppInfo
import com.ragaa.snapadb.core.adb.parser.PackageParser

private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")

class ListPackages : AdbCommand<List<AppInfo>> {
    override fun args(): List<String> = listOf("shell", "pm list packages -f")
    override fun parse(result: ProcessResult): List<AppInfo> = PackageParser.parseList(result.stdout)
}

class GetPackageInfo(private val packageName: String) : AdbCommand<AppInfo> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> = listOf("shell", "dumpsys package $packageName")
    override fun parse(result: ProcessResult): AppInfo =
        PackageParser.parsePackageInfo(result.stdout, packageName)
}

class UninstallApp(private val packageName: String) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> = listOf("uninstall", packageName)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class ForceStopApp(private val packageName: String) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> = listOf("shell", "am force-stop $packageName")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class ClearAppData(private val packageName: String) : AdbCommand<String> {
    init {
        require(packageName.matches(PACKAGE_NAME_PATTERN)) { "Invalid package name: $packageName" }
    }

    override fun args(): List<String> = listOf("shell", "pm clear $packageName")
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}

class InstallApp(private val localApkPath: String) : AdbCommand<String> {
    init {
        require(localApkPath.endsWith(".apk", ignoreCase = true)) { "Not an APK file: $localApkPath" }
        require('\u0000' !in localApkPath) { "Path contains null bytes" }
    }

    override fun args(): List<String> = listOf("install", "-r", localApkPath)
    override fun parse(result: ProcessResult): String = result.stdout.trim()
}
