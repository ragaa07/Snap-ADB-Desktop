package com.ragaa.snapadb.feature.monkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.ShellExec
import com.ragaa.snapadb.feature.monkey.command.MonkeyStream
import com.ragaa.snapadb.feature.monkey.data.MonkeyRepository
import com.ragaa.snapadb.feature.monkey.model.MonkeyConfig
import com.ragaa.snapadb.feature.monkey.model.MonkeyOutputLine
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunStatus
import com.ragaa.snapadb.feature.monkey.model.MonkeyRunSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

class MonkeyViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
    private val repository: MonkeyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MonkeyState>(MonkeyState.NoDevice)
    val state: StateFlow<MonkeyState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<MonkeyResult?>(null)
    val actionResult: StateFlow<MonkeyResult?> = _actionResult.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())
    private val _packageSuggestions = MutableStateFlow<List<String>>(emptyList())
    private val _configs = MutableStateFlow<List<MonkeyConfig>>(emptyList())
    private val _runs = MutableStateFlow<List<MonkeyRunSummary>>(emptyList())
    private val _currentConfig = MutableStateFlow(MonkeyConfig())

    private val streamJob = AtomicReference<Job?>(null)
    private val emitJob = AtomicReference<Job?>(null)
    private val outputMutex = Mutex()
    private val outputBuffer = ArrayDeque<MonkeyOutputLine>(MAX_OUTPUT_LINES)
    private val dirty = AtomicBoolean(false)

    // Running state tracking — use AtomicInteger for thread-safe increment
    private val _injectedEvents = AtomicInteger(0)
    private val _totalEvents = AtomicInteger(0)
    private val _crashDetected = AtomicBoolean(false)
    private val _anrDetected = AtomicBoolean(false)
    private val _abortDetected = AtomicBoolean(false)
    // Crash log is protected by outputMutex (same lock as outputBuffer)
    private val _crashLog = StringBuilder()
    private val _currentRunId = AtomicReference<Long?>(null)

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                stopStream()
                _currentSerial.value = device?.serial
                _installedPackages.value = emptyList()
                _packageSuggestions.value = emptyList()
                _actionResult.value = null
                outputMutex.withLock {
                    outputBuffer.clear()
                    _crashLog.clear()
                }

                if (device == null) {
                    _state.value = MonkeyState.NoDevice
                } else {
                    loadData(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: MonkeyIntent) {
        when (intent) {
            is MonkeyIntent.UpdatePackageName -> {
                _currentConfig.value = _currentConfig.value.copy(packageName = intent.name)
                updateSuggestions(intent.name)
                emitIdleState()
            }
            is MonkeyIntent.SelectPackage -> {
                _currentConfig.value = _currentConfig.value.copy(packageName = intent.name)
                _packageSuggestions.value = emptyList()
                emitIdleState()
            }
            is MonkeyIntent.DismissSuggestions -> {
                _packageSuggestions.value = emptyList()
                emitIdleState()
            }
            is MonkeyIntent.UpdateEventCount -> {
                val count = intent.count.toIntOrNull() ?: return
                if (count in 1..1_000_000) {
                    _currentConfig.value = _currentConfig.value.copy(eventCount = count)
                    emitIdleState()
                }
            }
            is MonkeyIntent.UpdateSeed -> {
                _currentConfig.value = _currentConfig.value.copy(seed = intent.seed.toIntOrNull())
                emitIdleState()
            }
            is MonkeyIntent.UpdateThrottle -> {
                val ms = intent.ms.toIntOrNull() ?: return
                if (ms in 0..5000) {
                    _currentConfig.value = _currentConfig.value.copy(throttleMs = ms)
                    emitIdleState()
                }
            }
            is MonkeyIntent.UpdateVerbosity -> {
                _currentConfig.value = _currentConfig.value.copy(verbosity = intent.level)
                emitIdleState()
            }
            is MonkeyIntent.ToggleRestrictToApp -> {
                _currentConfig.value = _currentConfig.value.copy(restrictToApp = !_currentConfig.value.restrictToApp)
                emitIdleState()
            }
            is MonkeyIntent.ToggleCategory -> {
                val current = _currentConfig.value.categories.toMutableList()
                if (intent.category in current) current.remove(intent.category)
                else current.add(intent.category)
                _currentConfig.value = _currentConfig.value.copy(categories = current)
                emitIdleState()
            }
            is MonkeyIntent.UpdateConfigName -> {
                _currentConfig.value = _currentConfig.value.copy(name = intent.name)
                emitIdleState()
            }
            is MonkeyIntent.SaveConfig -> saveConfig()
            is MonkeyIntent.LoadConfig -> {
                _currentConfig.value = intent.config
                _packageSuggestions.value = emptyList()
                emitIdleState()
            }
            is MonkeyIntent.DeleteConfig -> deleteConfig(intent.id)
            is MonkeyIntent.StartTest -> startTest()
            is MonkeyIntent.StopTest -> stopTest()
            is MonkeyIntent.DeleteRun -> deleteRun(intent.id)
            is MonkeyIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadData(serial: String) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ListPackages(), serial)
            }.onSuccess { apps ->
                _installedPackages.value = apps.map { it.packageName }.sorted()
            }
            emitIdleState()
        }
        viewModelScope.launch {
            try {
                _configs.value = repository.getAllConfigs()
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Failed to load configs: ${e.message}")
            }
            emitIdleState()
        }
        viewModelScope.launch {
            try {
                _runs.value = repository.getAllRuns()
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Failed to load run history: ${e.message}")
            }
            emitIdleState()
        }
    }

    private fun updateSuggestions(query: String) {
        val packages = _installedPackages.value
        _packageSuggestions.value = if (query.length >= 2) {
            packages.filter { it.contains(query, ignoreCase = true) }.take(10)
        } else {
            emptyList()
        }
    }

    private fun saveConfig() {
        val config = _currentConfig.value
        if (config.name.isBlank()) {
            _actionResult.value = MonkeyResult.Failure("Config name is required")
            return
        }
        if (config.packageName.isBlank()) {
            _actionResult.value = MonkeyResult.Failure("Package name is required")
            return
        }
        viewModelScope.launch {
            try {
                val id = repository.saveConfig(config)
                _currentConfig.value = config.copy(id = id)
                _configs.value = repository.getAllConfigs()
                _actionResult.value = MonkeyResult.Success("Config saved")
                emitIdleState()
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Save failed: ${e.message}")
            }
        }
    }

    private fun deleteConfig(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteConfig(id)
                _configs.value = repository.getAllConfigs()
                _actionResult.value = MonkeyResult.Success("Config deleted")
                emitIdleState()
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Delete failed: ${e.message}")
            }
        }
    }

    private fun startTest() {
        val serial = _currentSerial.value ?: return
        val config = _currentConfig.value

        if (config.packageName.isBlank()) {
            _actionResult.value = MonkeyResult.Failure("Package name is required")
            return
        }

        val command = try {
            MonkeyStream(
                packageName = config.packageName,
                eventCount = config.eventCount,
                throttleMs = config.throttleMs,
                seed = config.seed,
                verbosity = config.verbosity,
                categories = config.categories,
                restrictToApp = config.restrictToApp,
            )
        } catch (e: IllegalArgumentException) {
            _actionResult.value = MonkeyResult.Failure(e.message ?: "Invalid configuration")
            return
        }

        // Reset running state
        _injectedEvents.set(0)
        _totalEvents.set(config.eventCount)
        _crashDetected.set(false)
        _anrDetected.set(false)
        _abortDetected.set(false)

        // Launch single coroutine: insert run record, then start stream
        val newStreamJob = viewModelScope.launch(dispatchers.io) {
            // Clear buffer synchronously before streaming
            outputMutex.withLock {
                outputBuffer.clear()
                _crashLog.clear()
            }

            // Insert run record and await result before streaming
            val runId = try {
                repository.insertRun(
                    configName = config.name.ifBlank { "Unnamed" },
                    packageName = config.packageName,
                    deviceSerial = serial,
                    totalEvents = config.eventCount,
                    seed = config.seed,
                )
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Failed to create run record: ${e.message}")
                return@launch
            }
            _currentRunId.set(runId)

            try {
                adbClient.stream(command, serial).collect { line ->
                    outputMutex.withLock {
                        if (outputBuffer.size >= MAX_OUTPUT_LINES) {
                            outputBuffer.removeFirst()
                        }
                        outputBuffer.addLast(line)

                        when (line) {
                            is MonkeyOutputLine.CrashDetected -> {
                                _crashDetected.set(true)
                                _crashLog.appendLine(line.text)
                            }
                            is MonkeyOutputLine.ANRDetected -> {
                                _anrDetected.set(true)
                                _crashLog.appendLine(line.text)
                            }
                            is MonkeyOutputLine.Summary -> {
                                _injectedEvents.set(line.injectedEvents)
                            }
                            is MonkeyOutputLine.Event -> {
                                _injectedEvents.incrementAndGet()
                            }
                            is MonkeyOutputLine.Aborted -> {
                                _abortDetected.set(true)
                                _crashLog.appendLine(line.text)
                            }
                            is MonkeyOutputLine.Info -> {
                                if (_crashDetected.get() || _anrDetected.get()) {
                                    _crashLog.appendLine(line.text)
                                }
                            }
                        }
                    }
                    dirty.set(true)
                }
                // Stream completed normally
                onStreamCompleted()
            } catch (e: CancellationException) {
                // Cancelled by stopTest() — it handles finalization, don't call onStreamCompleted
                throw e
            } catch (e: Exception) {
                onStreamCompleted()
            }
        }
        streamJob.getAndSet(newStreamJob)?.cancel()

        emitRunningState()

        val newEmitJob = viewModelScope.launch {
            while (true) {
                delay(EMIT_INTERVAL_MS)
                if (dirty.compareAndSet(true, false)) {
                    emitRunningState()
                }
            }
        }
        emitJob.getAndSet(newEmitJob)?.cancel()
    }

    private fun onStreamCompleted() {
        val status = when {
            _crashDetected.get() -> MonkeyRunStatus.Crashed
            _anrDetected.get() -> MonkeyRunStatus.ANR
            _abortDetected.get() -> MonkeyRunStatus.Aborted
            else -> MonkeyRunStatus.Completed
        }
        finalizeRun(status)
    }

    private fun stopTest() {
        val serial = _currentSerial.value
        stopStream()
        finalizeRun(MonkeyRunStatus.Stopped)
        // Kill the monkey process on the device — it survives the local ADB bridge kill
        if (serial != null) {
            viewModelScope.launch(dispatchers.io) {
                try {
                    adbClient.execute(ShellExec("pkill -f com.android.commands.monkey"), serial, 3000L)
                } catch (_: Exception) {
                    // Best effort — process may have already exited
                }
            }
        }
    }

    private fun finalizeRun(status: MonkeyRunStatus) {
        val runId = _currentRunId.getAndSet(null)
        val injected = _injectedEvents.get()
        viewModelScope.launch {
            if (runId != null) {
                val crashLogText = outputMutex.withLock {
                    _crashLog.toString().ifBlank { null }
                }
                try {
                    repository.updateRunCompletion(
                        id = runId,
                        injectedEvents = injected,
                        status = status,
                        crashLog = crashLogText,
                    )
                    _runs.value = repository.getAllRuns()
                } catch (_: Exception) {}
            }
            emitIdleState()
            _actionResult.value = when (status) {
                MonkeyRunStatus.Completed -> MonkeyResult.Success("Test completed: $injected events injected")
                MonkeyRunStatus.Crashed -> MonkeyResult.Failure("Crash detected after $injected events")
                MonkeyRunStatus.ANR -> MonkeyResult.Failure("ANR detected after $injected events")
                MonkeyRunStatus.Stopped -> MonkeyResult.Success("Test stopped: $injected events injected")
                MonkeyRunStatus.Aborted -> MonkeyResult.Failure("Monkey aborted after $injected events")
                MonkeyRunStatus.Running -> MonkeyResult.Success("Test finished")
            }
        }
    }

    private fun stopStream() {
        streamJob.getAndSet(null)?.cancel()
        emitJob.getAndSet(null)?.cancel()
    }

    private fun deleteRun(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteRun(id)
                _runs.value = repository.getAllRuns()
                _actionResult.value = MonkeyResult.Success("Run deleted")
                emitIdleState()
            } catch (e: Exception) {
                _actionResult.value = MonkeyResult.Failure("Delete failed: ${e.message}")
            }
        }
    }

    private fun emitIdleState() {
        _state.value = MonkeyState.Idle(
            configs = _configs.value,
            runs = _runs.value,
            currentConfig = _currentConfig.value,
            packageSuggestions = _packageSuggestions.value,
        )
    }

    private fun emitRunningState() {
        viewModelScope.launch {
            val lines = outputMutex.withLock { outputBuffer.toList() }
            _state.value = MonkeyState.Running(
                config = _currentConfig.value,
                outputLines = lines,
                injectedEvents = _injectedEvents.get(),
                totalEvents = _totalEvents.get(),
                crashDetected = _crashDetected.get(),
                anrDetected = _anrDetected.get(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }

    companion object {
        private const val EMIT_INTERVAL_MS = 100L
        private const val MAX_OUTPUT_LINES = 5000
    }
}

// State
sealed class MonkeyState {
    data object NoDevice : MonkeyState()
    data class Idle(
        val configs: List<MonkeyConfig>,
        val runs: List<MonkeyRunSummary>,
        val currentConfig: MonkeyConfig,
        val packageSuggestions: List<String>,
    ) : MonkeyState()
    data class Running(
        val config: MonkeyConfig,
        val outputLines: List<MonkeyOutputLine>,
        val injectedEvents: Int,
        val totalEvents: Int,
        val crashDetected: Boolean,
        val anrDetected: Boolean,
    ) : MonkeyState()
}

// Intents
sealed class MonkeyIntent {
    data class UpdatePackageName(val name: String) : MonkeyIntent()
    data class SelectPackage(val name: String) : MonkeyIntent()
    data object DismissSuggestions : MonkeyIntent()
    data class UpdateEventCount(val count: String) : MonkeyIntent()
    data class UpdateSeed(val seed: String) : MonkeyIntent()
    data class UpdateThrottle(val ms: String) : MonkeyIntent()
    data class UpdateVerbosity(val level: Int) : MonkeyIntent()
    data object ToggleRestrictToApp : MonkeyIntent()
    data class ToggleCategory(val category: String) : MonkeyIntent()
    data class UpdateConfigName(val name: String) : MonkeyIntent()
    data object SaveConfig : MonkeyIntent()
    data class LoadConfig(val config: MonkeyConfig) : MonkeyIntent()
    data class DeleteConfig(val id: Long) : MonkeyIntent()
    data object StartTest : MonkeyIntent()
    data object StopTest : MonkeyIntent()
    data class DeleteRun(val id: Long) : MonkeyIntent()
    data object DismissResult : MonkeyIntent()
}

// Results
sealed class MonkeyResult {
    data class Success(val message: String) : MonkeyResult()
    data class Failure(val message: String) : MonkeyResult()
}
