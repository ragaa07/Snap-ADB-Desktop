package com.ragaa.snapadb.core.adb

import java.io.File

class AdbPath(private val userConfigPath: String? = null) {

    fun resolve(): String? {
        // 1. User-configured path takes priority
        userConfigPath?.let { path ->
            if (File(path).canExecute()) return path
        }

        // 2. ANDROID_HOME/platform-tools/adb
        System.getenv("ANDROID_HOME")?.let { home ->
            val adb = File(home, "platform-tools${File.separator}adb")
            if (adb.canExecute()) return adb.absolutePath
        }

        // 3. ANDROID_SDK_ROOT/platform-tools/adb
        System.getenv("ANDROID_SDK_ROOT")?.let { root ->
            val adb = File(root, "platform-tools${File.separator}adb")
            if (adb.canExecute()) return adb.absolutePath
        }

        // 4. Look up adb on PATH
        return findOnPath()
    }

    private fun findOnPath(): String? {
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: return null
        val adbName = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
        return pathDirs
            .map { File(it, adbName) }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }
}
