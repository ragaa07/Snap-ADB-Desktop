package com.ragaa.snapadb.core.adb.model

data class AppInfo(
    val packageName: String,
    val appLabel: String = "",
    val apkPath: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val installerPackageName: String = "",
    val codeSize: Long = -1,
    val dataSize: Long = -1,
    val cacheSize: Long = -1,
    val firstInstallTime: Long = 0,
) {
    val displayName: String get() = appLabel.ifEmpty { packageName }
    val totalSize: Long get() = listOf(codeSize, dataSize, cacheSize).filter { it >= 0 }.sum()
}

enum class AppFilter {
    ALL, USER, SYSTEM;
}

enum class AppSort {
    NAME_ASC, NAME_DESC, RECENTLY_INSTALLED;
}

data class AppPermission(
    val name: String,
    val isGranted: Boolean,
)
