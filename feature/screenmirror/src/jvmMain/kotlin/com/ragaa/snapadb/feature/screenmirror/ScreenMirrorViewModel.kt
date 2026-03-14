package com.ragaa.snapadb.feature.screenmirror

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.mirror.DeviceMirror
import com.ragaa.mirror.DownloadProgress
import com.ragaa.mirror.ScrcpyManager
import com.ragaa.mirror.TouchInput
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetAutoRotateSetting
import com.ragaa.snapadb.core.adb.command.GetStayAwakeSetting
import com.ragaa.snapadb.core.adb.command.PullFile
import com.ragaa.snapadb.core.adb.command.RemoveRemoteFile
import com.ragaa.snapadb.core.adb.command.SetAutoRotate
import com.ragaa.snapadb.core.adb.command.SetStayAwake
import com.ragaa.snapadb.core.adb.command.SetUserRotation
import com.ragaa.snapadb.core.adb.command.StartScreenRecord
import com.ragaa.snapadb.core.adb.command.StopScreenRecord
import com.ragaa.snapadb.core.adb.command.TakeScreenshot
import com.ragaa.snapadb.feature.screenmirror.model.ScrcpyConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ScreenMirrorViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
    private val deviceMirror: DeviceMirror,
    private val scrcpyManager: ScrcpyManager,
) : ViewModel() {

    private val _state = MutableStateFlow<ScreenMirrorState>(ScreenMirrorState.NoDevice)
    val state: StateFlow<ScreenMirrorState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<ScreenMirrorResult?>(null)
    val actionResult: StateFlow<ScreenMirrorResult?> = _actionResult.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val recordingJob = AtomicReference<Job?>(null)
    private val timerJob = AtomicReference<Job?>(null)
    private val scrcpyProcess = AtomicReference<Process?>(null)
    private val mirrorJob = AtomicReference<Job?>(null)

    // Track temp files for cleanup
    private val tempFiles = AtomicReference<List<File>>(emptyList())

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                cancelRecording()
                stopScrcpy()
                stopMirror()
                _currentSerial.value = device?.serial
                _actionResult.value = null

                if (device == null) {
                    _state.value = ScreenMirrorState.NoDevice
                } else {
                    loadDeviceSettings(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: ScreenMirrorIntent) {
        when (intent) {
            is ScreenMirrorIntent.CaptureScreenshot -> captureScreenshot()
            is ScreenMirrorIntent.SaveScreenshot -> saveScreenshot(intent.targetFile)
            is ScreenMirrorIntent.StartRecording -> startRecording(intent.timeLimitSecs, intent.bitrate, intent.resolution)
            is ScreenMirrorIntent.StopRecording -> stopRecording()
            is ScreenMirrorIntent.SaveRecording -> saveRecording(intent.targetFile)
            is ScreenMirrorIntent.LaunchScrcpy -> launchScrcpy(intent.config)
            is ScreenMirrorIntent.StopScrcpy -> stopScrcpy()
            is ScreenMirrorIntent.ToggleAutoRotate -> toggleAutoRotate(intent.enabled)
            is ScreenMirrorIntent.SetRotation -> setRotation(intent.rotation)
            is ScreenMirrorIntent.ToggleStayAwake -> toggleStayAwake(intent.enabled)
            is ScreenMirrorIntent.DismissResult -> _actionResult.value = null
            is ScreenMirrorIntent.Retry -> _currentSerial.value?.let { loadDeviceSettings(it) }
            is ScreenMirrorIntent.StartMirror -> startMirror()
            is ScreenMirrorIntent.StopMirror -> stopMirror()
            is ScreenMirrorIntent.MirrorTap -> mirrorTap(intent.x, intent.y)
            is ScreenMirrorIntent.MirrorSwipe -> mirrorSwipe(intent.x1, intent.y1, intent.x2, intent.y2)
            is ScreenMirrorIntent.MirrorKeyEvent -> mirrorKeyEvent(intent.keyCode)
            is ScreenMirrorIntent.DownloadScrcpy -> downloadScrcpy()
        }
    }

    private fun loadDeviceSettings(serial: String) {
        viewModelScope.launch {
            _state.value = ScreenMirrorState.Loading
            try {
                // Run all three in parallel
                val autoRotateDeferred = async(dispatchers.io) {
                    adbClient.execute(GetAutoRotateSetting(), serial).getOrDefault(false)
                }
                val stayAwakeDeferred = async(dispatchers.io) {
                    adbClient.execute(GetStayAwakeSetting(), serial).getOrDefault(0)
                }
                val scrcpyDeferred = async(dispatchers.io) { scrcpyManager.isInstalled() }

                _state.value = ScreenMirrorState.Ready(
                    autoRotateEnabled = autoRotateDeferred.await(),
                    stayAwakeEnabled = stayAwakeDeferred.await() > 0,
                    scrcpyAvailable = scrcpyDeferred.await(),
                )
            } catch (e: Exception) {
                _state.value = ScreenMirrorState.Error(e.message ?: "Failed to load device info")
            }
        }
    }

    // --- In-App Mirror ---

    private fun startMirror() {
        val serial = _currentSerial.value ?: return
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return

        _state.value = currentState.copy(isMirroring = true)

        // Cancel old job before launching new one to prevent duplicates
        mirrorJob.getAndSet(null)?.cancel()

        val newJob = viewModelScope.launch {
            var frameCount = 0
            var lastFpsTime = System.currentTimeMillis()

            deviceMirror.start(serial).collect { frame ->
                try {
                    val bitmap = Image.makeFromEncoded(frame.pngBytes).toComposeImageBitmap()
                    frameCount++
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastFpsTime
                    val fps = if (elapsed >= 1000) {
                        val f = (frameCount * 1000.0 / elapsed).toInt()
                        frameCount = 0
                        lastFpsTime = now
                        f
                    } else {
                        val s = _state.value as? ScreenMirrorState.Ready
                        s?.mirrorFps ?: 0
                    }

                    val s = _state.value as? ScreenMirrorState.Ready ?: return@collect
                    _state.value = s.copy(
                        mirrorFrame = bitmap,
                        mirrorFrameWidth = frame.width,
                        mirrorFrameHeight = frame.height,
                        mirrorFps = fps,
                    )
                } catch (_: Exception) {
                    // Skip frame decode error
                }
            }
        }
        mirrorJob.set(newJob)
    }

    private fun stopMirror() {
        mirrorJob.getAndSet(null)?.cancel()
        val s = _state.value as? ScreenMirrorState.Ready
        if (s != null) {
            _state.value = s.copy(
                isMirroring = false,
                mirrorFrame = null,
                mirrorFps = 0,
            )
        }
    }

    private fun mirrorTap(x: Float, y: Float) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            deviceMirror.sendTap(serial, x, y)
        }
    }

    private fun mirrorSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            deviceMirror.sendSwipe(serial, x1, y1, x2, y2)
        }
    }

    private fun mirrorKeyEvent(keyCode: Int) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            deviceMirror.sendKeyEvent(serial, keyCode)
        }
    }

    // --- scrcpy Download ---

    private fun downloadScrcpy() {
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return
        _state.value = currentState.copy(scrcpyDownloading = true, scrcpyDownloadProgress = 0f, scrcpyDownloadError = null)

        viewModelScope.launch {
            try {
                scrcpyManager.download().collect { progress ->
                    val s = _state.value as? ScreenMirrorState.Ready ?: return@collect
                    _state.value = s.copy(scrcpyDownloadProgress = progress.fraction)
                }
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(
                        scrcpyDownloading = false,
                        scrcpyAvailable = scrcpyManager.isInstalled(),
                        scrcpyDownloadProgress = 0f,
                    )
                }
                _actionResult.value = ScreenMirrorResult.Success("scrcpy downloaded successfully")
            } catch (e: Exception) {
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(
                        scrcpyDownloading = false,
                        scrcpyDownloadError = e.message,
                        scrcpyDownloadProgress = 0f,
                    )
                }
                _actionResult.value = ScreenMirrorResult.Failure("scrcpy download failed: ${e.message}")
            }
        }
    }

    // --- Screenshot ---

    private fun captureScreenshot() {
        val serial = _currentSerial.value ?: return
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return

        viewModelScope.launch {
            _state.value = currentState.copy(isCapturing = true)
            val remotePath = "/sdcard/snapadb_screenshot_${System.currentTimeMillis()}.png"
            val tempFile = withContext(dispatchers.io) {
                File.createTempFile("snapadb_screenshot", ".png").also { it.deleteOnExit() }
            }
            try {
                withContext(dispatchers.io) {
                    adbClient.execute(TakeScreenshot(remotePath), serial).getOrThrow()
                    adbClient.execute(PullFile(remotePath, tempFile.absolutePath), serial).getOrThrow()
                    adbClient.execute(RemoveRemoteFile(remotePath), serial)
                }
                val bytes = withContext(dispatchers.io) { tempFile.readBytes() }
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                tempFile.delete()

                val s = _state.value as? ScreenMirrorState.Ready ?: return@launch
                _state.value = s.copy(
                    isCapturing = false,
                    screenshotBitmap = bitmap,
                    screenshotBytes = bytes,
                )
                _actionResult.value = ScreenMirrorResult.Success("Screenshot captured")
            } catch (e: Exception) {
                tempFile.delete()
                val s = _state.value as? ScreenMirrorState.Ready ?: return@launch
                _state.value = s.copy(isCapturing = false)
                _actionResult.value = ScreenMirrorResult.Failure("Screenshot failed: ${e.message}")
            }
        }
    }

    private fun saveScreenshot(targetFile: File) {
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return
        val bytes = currentState.screenshotBytes ?: return

        viewModelScope.launch(dispatchers.io) {
            try {
                targetFile.writeBytes(bytes)
                _actionResult.value = ScreenMirrorResult.Success("Screenshot saved to ${targetFile.name}")
            } catch (e: Exception) {
                _actionResult.value = ScreenMirrorResult.Failure("Save failed: ${e.message}")
            }
        }
    }

    // --- Recording ---

    private fun startRecording(timeLimitSecs: Int, bitrate: Int?, resolution: String?) {
        val serial = _currentSerial.value ?: return
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return

        val remotePath = "/sdcard/snapadb_recording_${System.currentTimeMillis()}.mp4"

        _state.value = currentState.copy(
            isRecording = true,
            recordingElapsedSecs = 0,
            lastRecordingLocalPath = null,
        )

        // Timer job
        val newTimerJob = viewModelScope.launch {
            var elapsed = 0
            while (true) {
                delay(1000)
                elapsed++
                val s = _state.value as? ScreenMirrorState.Ready ?: break
                if (!s.isRecording) break
                _state.value = s.copy(recordingElapsedSecs = elapsed)
            }
        }
        timerJob.getAndSet(newTimerJob)?.cancel()

        // Recording job
        val newRecordingJob = viewModelScope.launch {
            try {
                val timeoutMs = (timeLimitSecs + 10).toLong() * 1000L
                withContext(dispatchers.io) {
                    adbClient.execute(
                        StartScreenRecord(remotePath, timeLimitSecs, bitrate, resolution),
                        serial,
                        timeoutMs,
                    )
                }
            } catch (_: Exception) {
                // Recording ended (either naturally or by StopScreenRecord)
            }

            // Pull file after recording finishes
            delay(1000)
            try {
                val tempFile = withContext(dispatchers.io) {
                    File.createTempFile("snapadb_recording", ".mp4").also { it.deleteOnExit() }
                }
                trackTempFile(tempFile)

                withContext(dispatchers.io) {
                    adbClient.execute(PullFile(remotePath, tempFile.absolutePath), serial).getOrThrow()
                    adbClient.execute(RemoveRemoteFile(remotePath), serial)
                }
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(
                        isRecording = false,
                        lastRecordingLocalPath = tempFile.absolutePath,
                    )
                }
                _actionResult.value = ScreenMirrorResult.Success("Recording saved")
            } catch (e: Exception) {
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(isRecording = false)
                }
                _actionResult.value = ScreenMirrorResult.Failure("Recording pull failed: ${e.message}")
            }
            timerJob.getAndSet(null)?.cancel()
        }
        recordingJob.getAndSet(newRecordingJob)?.cancel()
    }

    private fun stopRecording() {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            adbClient.execute(StopScreenRecord(), serial)
        }
    }

    private fun cancelRecording() {
        val serial = _currentSerial.value
        if (serial != null && (_state.value as? ScreenMirrorState.Ready)?.isRecording == true) {
            viewModelScope.launch(dispatchers.io) {
                adbClient.execute(StopScreenRecord(), serial)
            }
        }
        recordingJob.getAndSet(null)?.cancel()
        timerJob.getAndSet(null)?.cancel()
    }

    private fun saveRecording(targetFile: File) {
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return
        val sourcePath = currentState.lastRecordingLocalPath ?: return

        viewModelScope.launch(dispatchers.io) {
            try {
                File(sourcePath).copyTo(targetFile, overwrite = true)
                _actionResult.value = ScreenMirrorResult.Success("Recording saved to ${targetFile.name}")
            } catch (e: Exception) {
                _actionResult.value = ScreenMirrorResult.Failure("Save failed: ${e.message}")
            }
        }
    }

    // --- scrcpy ---

    private fun launchScrcpy(config: ScrcpyConfig) {
        val serial = _currentSerial.value ?: return
        val currentState = _state.value as? ScreenMirrorState.Ready ?: return

        viewModelScope.launch {
            try {
                val scrcpyPath = scrcpyManager.resolveScrcpyPath() ?: "scrcpy"
                val args = buildList {
                    add(scrcpyPath)
                    add("-s")
                    add(serial)
                    addAll(config.toArgs())
                }
                val process = withContext(dispatchers.io) {
                    ProcessBuilder(args)
                        .redirectErrorStream(true)
                        .start()
                }
                scrcpyProcess.set(process)
                _state.value = currentState.copy(scrcpyRunning = true)
                _actionResult.value = ScreenMirrorResult.Success("scrcpy launched")

                // Monitor exit
                withContext(dispatchers.io) { process.waitFor() }
                scrcpyProcess.set(null)
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(scrcpyRunning = false)
                }
            } catch (e: Exception) {
                scrcpyProcess.set(null)
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) {
                    _state.value = s.copy(scrcpyRunning = false)
                }
                _actionResult.value = ScreenMirrorResult.Failure("scrcpy failed: ${e.message}")
            }
        }
    }

    private fun stopScrcpy() {
        scrcpyProcess.getAndSet(null)?.destroyForcibly()
        val s = _state.value as? ScreenMirrorState.Ready
        if (s != null) {
            _state.value = s.copy(scrcpyRunning = false)
        }
    }

    // --- Rotation & Stay Awake ---

    private fun toggleAutoRotate(enabled: Boolean) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(SetAutoRotate(enabled), serial)
            }.onSuccess {
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) _state.value = s.copy(autoRotateEnabled = enabled)
            }.onFailure { e ->
                _actionResult.value = ScreenMirrorResult.Failure("Failed: ${e.message}")
            }
        }
    }

    private fun setRotation(rotation: Int) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch {
            // Disable auto-rotate first
            val disableResult = withContext(dispatchers.io) {
                adbClient.execute(SetAutoRotate(false), serial)
            }
            if (disableResult.isFailure) {
                _actionResult.value = ScreenMirrorResult.Failure("Failed to disable auto-rotate: ${disableResult.exceptionOrNull()?.message}")
                return@launch
            }
            // Then set manual rotation
            withContext(dispatchers.io) {
                adbClient.execute(SetUserRotation(rotation), serial)
            }.onSuccess {
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) _state.value = s.copy(autoRotateEnabled = false)
                _actionResult.value = ScreenMirrorResult.Success("Rotation set to ${rotation * 90}\u00B0")
            }.onFailure { e ->
                _actionResult.value = ScreenMirrorResult.Failure("Failed: ${e.message}")
            }
        }
    }

    private fun toggleStayAwake(enabled: Boolean) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch {
            val mode = if (enabled) 7 else 0
            withContext(dispatchers.io) {
                adbClient.execute(SetStayAwake(mode), serial)
            }.onSuccess {
                val s = _state.value as? ScreenMirrorState.Ready
                if (s != null) _state.value = s.copy(stayAwakeEnabled = enabled)
            }.onFailure { e ->
                _actionResult.value = ScreenMirrorResult.Failure("Failed: ${e.message}")
            }
        }
    }

    // --- Helpers ---

    private fun trackTempFile(file: File) {
        tempFiles.getAndUpdate { it + file }
    }

    private fun cleanupTempFiles() {
        tempFiles.getAndSet(emptyList()).forEach { it.delete() }
    }

    override fun onCleared() {
        super.onCleared()
        cancelRecording()
        stopScrcpy()
        stopMirror()
        cleanupTempFiles()
    }
}

// State
sealed class ScreenMirrorState {
    data object NoDevice : ScreenMirrorState()
    data object Loading : ScreenMirrorState()
    data class Error(val message: String) : ScreenMirrorState()
    data class Ready(
        val screenshotBitmap: ImageBitmap? = null,
        val screenshotBytes: ByteArray? = null,
        val isCapturing: Boolean = false,
        val isRecording: Boolean = false,
        val recordingElapsedSecs: Int = 0,
        val lastRecordingLocalPath: String? = null,
        val scrcpyAvailable: Boolean = false,
        val scrcpyRunning: Boolean = false,
        val autoRotateEnabled: Boolean = false,
        val stayAwakeEnabled: Boolean = false,
        // In-app mirror
        val isMirroring: Boolean = false,
        val mirrorFrame: ImageBitmap? = null,
        val mirrorFrameWidth: Int = 0,
        val mirrorFrameHeight: Int = 0,
        val mirrorFps: Int = 0,
        // scrcpy download
        val scrcpyDownloading: Boolean = false,
        val scrcpyDownloadProgress: Float = 0f,
        val scrcpyDownloadError: String? = null,
    ) : ScreenMirrorState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ready) return false
            return isCapturing == other.isCapturing &&
                isRecording == other.isRecording &&
                recordingElapsedSecs == other.recordingElapsedSecs &&
                lastRecordingLocalPath == other.lastRecordingLocalPath &&
                scrcpyAvailable == other.scrcpyAvailable &&
                scrcpyRunning == other.scrcpyRunning &&
                autoRotateEnabled == other.autoRotateEnabled &&
                stayAwakeEnabled == other.stayAwakeEnabled &&
                screenshotBitmap === other.screenshotBitmap &&
                screenshotBytes.contentEquals(other.screenshotBytes) &&
                isMirroring == other.isMirroring &&
                mirrorFrame === other.mirrorFrame &&
                mirrorFrameWidth == other.mirrorFrameWidth &&
                mirrorFrameHeight == other.mirrorFrameHeight &&
                mirrorFps == other.mirrorFps &&
                scrcpyDownloading == other.scrcpyDownloading &&
                scrcpyDownloadProgress == other.scrcpyDownloadProgress &&
                scrcpyDownloadError == other.scrcpyDownloadError
        }

        override fun hashCode(): Int {
            var result = screenshotBitmap?.hashCode() ?: 0
            result = 31 * result + (screenshotBytes?.contentHashCode() ?: 0)
            result = 31 * result + isCapturing.hashCode()
            result = 31 * result + isRecording.hashCode()
            result = 31 * result + recordingElapsedSecs
            result = 31 * result + (lastRecordingLocalPath?.hashCode() ?: 0)
            result = 31 * result + scrcpyAvailable.hashCode()
            result = 31 * result + scrcpyRunning.hashCode()
            result = 31 * result + autoRotateEnabled.hashCode()
            result = 31 * result + stayAwakeEnabled.hashCode()
            result = 31 * result + isMirroring.hashCode()
            result = 31 * result + (mirrorFrame?.hashCode() ?: 0)
            result = 31 * result + mirrorFrameWidth
            result = 31 * result + mirrorFrameHeight
            result = 31 * result + mirrorFps
            result = 31 * result + scrcpyDownloading.hashCode()
            result = 31 * result + scrcpyDownloadProgress.hashCode()
            result = 31 * result + (scrcpyDownloadError?.hashCode() ?: 0)
            return result
        }
    }
}

// Intents
sealed class ScreenMirrorIntent {
    data object CaptureScreenshot : ScreenMirrorIntent()
    data class SaveScreenshot(val targetFile: File) : ScreenMirrorIntent()
    data class StartRecording(val timeLimitSecs: Int = 180, val bitrate: Int? = null, val resolution: String? = null) : ScreenMirrorIntent()
    data object StopRecording : ScreenMirrorIntent()
    data class SaveRecording(val targetFile: File) : ScreenMirrorIntent()
    data class LaunchScrcpy(val config: ScrcpyConfig) : ScreenMirrorIntent()
    data object StopScrcpy : ScreenMirrorIntent()
    data class ToggleAutoRotate(val enabled: Boolean) : ScreenMirrorIntent()
    data class SetRotation(val rotation: Int) : ScreenMirrorIntent()
    data class ToggleStayAwake(val enabled: Boolean) : ScreenMirrorIntent()
    data object DismissResult : ScreenMirrorIntent()
    data object Retry : ScreenMirrorIntent()
    // In-app mirror
    data object StartMirror : ScreenMirrorIntent()
    data object StopMirror : ScreenMirrorIntent()
    data class MirrorTap(val x: Float, val y: Float) : ScreenMirrorIntent()
    data class MirrorSwipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : ScreenMirrorIntent()
    data class MirrorKeyEvent(val keyCode: Int) : ScreenMirrorIntent()
    // scrcpy download
    data object DownloadScrcpy : ScreenMirrorIntent()
}

// Results
sealed class ScreenMirrorResult {
    data class Success(val message: String) : ScreenMirrorResult()
    data class Failure(val message: String) : ScreenMirrorResult()
}
