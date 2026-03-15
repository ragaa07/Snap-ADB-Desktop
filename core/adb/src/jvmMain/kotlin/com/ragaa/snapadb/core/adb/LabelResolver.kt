package com.ragaa.snapadb.core.adb

import com.ragaa.snapadb.common.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File

class LabelResolver(
    private val adbPath: AdbPath,
    private val dispatchers: DispatcherProvider,
) {

    private val aapt2Path: String? by lazy { findAapt2() }

    suspend fun resolveLabel(apkDevicePath: String, deviceSerial: String): String {
        val aapt2 = aapt2Path ?: return ""
        val adb = adbPath.resolve() ?: return ""

        return withContext(dispatchers.io) {
            val tempFile = File.createTempFile("snapadb_label_", ".apk")
            try {
                val pullProcess = ProcessBuilder(adb, "-s", deviceSerial, "pull", apkDevicePath, tempFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val pullExit = pullProcess.waitFor()
                if (pullExit != 0) return@withContext ""

                val dumpProcess = ProcessBuilder(aapt2, "dump", "badging", tempFile.absolutePath)
                    .redirectErrorStream(false)
                    .start()
                val output = dumpProcess.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().firstOrNull { it.startsWith("application-label:") }
                }
                dumpProcess.waitFor()

                output?.removePrefix("application-label:")?.trim()?.removeSurrounding("'") ?: ""
            } catch (_: Exception) {
                ""
            } finally {
                tempFile.delete()
            }
        }
    }

    fun isAvailable(): Boolean = aapt2Path != null

    private fun findAapt2(): String? {
        val aapt2Name = if (System.getProperty("os.name").lowercase().contains("win")) "aapt2.exe" else "aapt2"

        // Try all possible SDK roots
        for (sdkRoot in findSdkRoots()) {
            val buildToolsDir = File(sdkRoot, "build-tools")
            if (!buildToolsDir.isDirectory) continue

            val latestVersion = buildToolsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name }
                ?: continue

            val aapt2File = File(latestVersion, aapt2Name)
            if (aapt2File.canExecute()) return aapt2File.absolutePath
        }
        return null
    }

    private fun findSdkRoots(): List<File> = buildList {
        // 1. Derive from adb path: <SDK>/platform-tools/adb
        adbPath.resolve()?.let { adbBin ->
            val adbFile = File(adbBin)
            val parent = adbFile.parentFile
            if (parent?.name == "platform-tools") {
                parent.parentFile?.let { add(it) }
            }
        }

        // 2. ANDROID_HOME
        System.getenv("ANDROID_HOME")?.let { add(File(it)) }

        // 3. ANDROID_SDK_ROOT
        System.getenv("ANDROID_SDK_ROOT")?.let { add(File(it)) }

        // 4. Common default locations
        val home = System.getProperty("user.home")
        add(File(home, "Library/Android/sdk"))          // macOS
        add(File(home, "Android/Sdk"))                   // Linux
        add(File(home, "AppData/Local/Android/Sdk"))     // Windows
    }
}
