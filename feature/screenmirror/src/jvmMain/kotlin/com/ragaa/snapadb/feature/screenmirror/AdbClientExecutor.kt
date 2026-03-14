package com.ragaa.snapadb.feature.screenmirror

import com.ragaa.mirror.AdbExecutor
import com.ragaa.snapadb.core.adb.AdbPath
import com.ragaa.snapadb.common.DispatcherProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class AdbClientExecutor(
    private val adbPath: AdbPath,
    private val dispatchers: DispatcherProvider,
) : AdbExecutor {

    private fun resolveAdb(): String = adbPath.resolve() ?: "adb"

    override suspend fun exec(args: List<String>): ByteArray = withContext(dispatchers.io) {
        val fullArgs = listOf(resolveAdb()) + args
        val process = ProcessBuilder(fullArgs)
            .redirectErrorStream(false)
            .start()

        coroutineScope {
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val stdout = process.inputStream.readBytes()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            stderr.await()
            stdout
        }
    }

    override suspend fun execText(args: List<String>): String = withContext(dispatchers.io) {
        val fullArgs = listOf(resolveAdb()) + args
        val process = ProcessBuilder(fullArgs)
            .redirectErrorStream(false)
            .start()

        coroutineScope {
            val stderr = async { process.errorStream.bufferedReader().readText() }
            val stdout = async { process.inputStream.bufferedReader().use(BufferedReader::readText) }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            stderr.await()
            stdout.await()
        }
    }
}
