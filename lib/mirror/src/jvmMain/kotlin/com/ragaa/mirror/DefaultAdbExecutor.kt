package com.ragaa.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

class DefaultAdbExecutor(
    private val adbPath: String = findAdb(),
) : AdbExecutor {

    override suspend fun exec(args: List<String>): ByteArray = withContext(Dispatchers.IO) {
        val fullArgs = listOf(adbPath) + args
        val process = ProcessBuilder(fullArgs)
            .redirectErrorStream(false)
            .start()

        coroutineScope {
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val stdout = process.inputStream.readBytes()
            process.waitFor()
            stderr.await() // drain stderr
            stdout
        }
    }

    override suspend fun execText(args: List<String>): String = withContext(Dispatchers.IO) {
        val fullArgs = listOf(adbPath) + args
        val process = ProcessBuilder(fullArgs)
            .redirectErrorStream(false)
            .start()

        coroutineScope {
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val stdout = async { process.inputStream.bufferedReader().readText() }
            process.waitFor()
            stderr.await()
            stdout.await()
        }
    }

    companion object {
        fun findAdb(): String {
            System.getenv("ANDROID_HOME")?.let { home ->
                val adb = File(home, "platform-tools${File.separator}adb")
                if (adb.canExecute()) return adb.absolutePath
            }
            System.getenv("ANDROID_SDK_ROOT")?.let { root ->
                val adb = File(root, "platform-tools${File.separator}adb")
                if (adb.canExecute()) return adb.absolutePath
            }
            val adbName = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
            val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
            return pathDirs
                .map { File(it, adbName) }
                .firstOrNull { it.canExecute() }
                ?.absolutePath
                ?: "adb"
        }
    }
}
