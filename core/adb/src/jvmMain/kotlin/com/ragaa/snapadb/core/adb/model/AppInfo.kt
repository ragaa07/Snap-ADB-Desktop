package com.ragaa.snapadb.core.adb.model

data class AppInfo(
    val packageName: String,
    val apkPath: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val installerPackageName: String = "",
)

enum class AppFilter {
    ALL, USER, SYSTEM;
}
