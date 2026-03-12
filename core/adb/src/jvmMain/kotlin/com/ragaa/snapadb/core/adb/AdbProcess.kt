package com.ragaa.snapadb.core.adb

import com.ragaa.snapadb.common.DispatcherProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader

class AdbProcess(private val dispatchers: DispatcherProvider) {

    suspend fun execute(args: List<String>, timeout: Long = 30_000L): ProcessResult =
        withContext(dispatchers.io) {
            val process = ProcessBuilder(args)
                .redirectErrorStream(false)
                .start()

            try {
                withTimeout(timeout) {
                    val stdout = async { process.inputStream.bufferedReader().use(BufferedReader::readText) }
                    val stderr = async { process.errorStream.bufferedReader().use(BufferedReader::readText) }

                    val exitCode = process.onExit().await().exitValue()
                    ProcessResult(
                        exitCode = exitCode,
                        stdout = stdout.await(),
                        stderr = stderr.await()
                    )
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                process.destroyForcibly()
                throw AdbException.Timeout(args.joinToString(" "))
            } catch (e: Exception) {
                process.destroyForcibly()
                throw e
            }
        }

    fun stream(args: List<String>): Flow<String> = callbackFlow {
        val process = ProcessBuilder(args)
            .redirectErrorStream(false)
            .start()

        // Drain stderr on a background coroutine to prevent process hang
        launch(dispatchers.io) {
            try { process.errorStream.bufferedReader().readText() } catch (_: Exception) {}
        }

        // Read stdout lines and send to the flow
        launch(dispatchers.io) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> channel.send(line) }
                }
            } catch (_: Exception) {
                // Stream closed or channel closed — expected on cancellation
            } finally {
                channel.close()
            }
        }

        awaitClose {
            process.destroyForcibly()
        }
    }
}
