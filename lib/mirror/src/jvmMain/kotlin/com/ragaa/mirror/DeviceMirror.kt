package com.ragaa.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

class DeviceMirror(
    private val adbExecutor: AdbExecutor,
    private val config: MirrorConfig = MirrorConfig(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun start(deviceSerial: String): Flow<Frame> = flow {
        while (currentCoroutineContext().isActive) {
            val startMs = System.currentTimeMillis()
            try {
                val pngBytes = adbExecutor.exec(
                    listOf("-s", deviceSerial, "exec-out", "screencap", "-p")
                )
                if (pngBytes.size > 100) { // Valid PNG is always > 100 bytes
                    val dimensions = readPngDimensions(pngBytes)
                    emit(
                        Frame(
                            pngBytes = pngBytes,
                            width = dimensions.first,
                            height = dimensions.second,
                            timestampMs = System.currentTimeMillis(),
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Skip frame on error, retry next interval
            }
            val elapsed = System.currentTimeMillis() - startMs
            val remaining = config.refreshIntervalMs - elapsed
            if (remaining > 0) delay(remaining)
        }
    }.flowOn(ioDispatcher)

    fun stop() {
        // No-op: stopping is handled by cancelling the collecting job
    }

    suspend fun sendTap(deviceSerial: String, x: Float, y: Float) {
        withContext(ioDispatcher) {
            adbExecutor.execText(
                listOf("-s", deviceSerial, "shell", "input tap ${x.toInt()} ${y.toInt()}")
            )
        }
    }

    suspend fun sendSwipe(
        deviceSerial: String,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 300,
    ) {
        withContext(ioDispatcher) {
            adbExecutor.execText(
                listOf(
                    "-s", deviceSerial, "shell",
                    "input swipe ${x1.toInt()} ${y1.toInt()} ${x2.toInt()} ${y2.toInt()} $durationMs"
                )
            )
        }
    }

    suspend fun sendKeyEvent(deviceSerial: String, keyCode: Int) {
        withContext(ioDispatcher) {
            adbExecutor.execText(
                listOf("-s", deviceSerial, "shell", "input keyevent $keyCode")
            )
        }
    }

    private fun readPngDimensions(pngBytes: ByteArray): Pair<Int, Int> {
        return try {
            val stream = ByteArrayInputStream(pngBytes)
            val imageStream = ImageIO.createImageInputStream(stream)
            val reader = ImageIO.getImageReadersByFormatName("png").next()
            try {
                reader.input = imageStream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                Pair(width, height)
            } finally {
                reader.dispose()
                imageStream.close()
            }
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }
}
