package com.ragaa.snapadb.feature.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetAppMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetBatteryInfo
import com.ragaa.snapadb.core.adb.command.GetCpuStats
import com.ragaa.snapadb.core.adb.command.GetMemoryInfo
import com.ragaa.snapadb.core.adb.model.AppMemoryInfo
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.CpuInfo
import com.ragaa.snapadb.core.adb.model.CpuRawReading
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.parser.CpuParser
import com.ragaa.snapadb.feature.performance.model.PerformanceDataPoint
import com.ragaa.snapadb.feature.performance.model.RollingTimeSeries
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class PerformanceViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<PerformanceState>(PerformanceState.NoDevice)
    val state: StateFlow<PerformanceState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<PerformanceResult?>(null)
    val actionResult: StateFlow<PerformanceResult?> = _actionResult.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _pollingIntervalMs = MutableStateFlow(2000L)
    private val _isMonitoring = MutableStateFlow(false)
    private val _appPackageFilter = MutableStateFlow("")

    private val pollingJob = AtomicReference<Job?>(null)
    private val emitJob = AtomicReference<Job?>(null)

    // Data under mutex — all series and latest readings
    private val dataMutex = Mutex()
    private val cpuSeries = RollingTimeSeries()
    private val memorySeries = RollingTimeSeries()
    private val batterySeries = RollingTimeSeries()
    private val batteryTempSeries = RollingTimeSeries()
    private val lastCpuReading = AtomicReference<CpuRawReading?>(null)
    private val latestCpuInfo = AtomicReference<CpuInfo?>(null)
    private val latestMemoryInfo = AtomicReference<MemoryInfo?>(null)
    private val latestBatteryInfo = AtomicReference<BatteryInfo?>(null)
    private val latestAppMemory = AtomicReference<AppMemoryInfo?>(null)
    private val dirty = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                stopPolling()
                dataMutex.withLock { clearData() }
                _currentSerial.value = device?.serial
                _actionResult.value = null

                if (device == null) {
                    _state.value = PerformanceState.NoDevice
                } else {
                    _state.value = PerformanceState.Ready()
                }
            }
        }
    }

    fun onIntent(intent: PerformanceIntent) {
        when (intent) {
            is PerformanceIntent.StartMonitoring -> startMonitoring()
            is PerformanceIntent.StopMonitoring -> {
                _isMonitoring.value = false
                stopPolling()
                emitState()
            }
            is PerformanceIntent.ResetData -> resetData()
            is PerformanceIntent.SetPollingInterval -> {
                _pollingIntervalMs.value = intent.intervalMs
                if (_isMonitoring.value) {
                    stopPolling()
                    startPolling()
                }
                emitState()
            }
            is PerformanceIntent.SetAppPackageFilter -> {
                val pkg = intent.packageName
                if (pkg.isNotBlank() && !pkg.matches(PACKAGE_NAME_PATTERN)) {
                    _actionResult.value = PerformanceResult.Failure("Invalid package name: $pkg")
                    return
                }
                _appPackageFilter.value = pkg
                dirty.set(true)
            }
            is PerformanceIntent.ExportCsv -> exportCsv(intent.targetFile)
            is PerformanceIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun startMonitoring() {
        if (_currentSerial.value == null) return
        _isMonitoring.value = true
        startPolling()
        emitState()
    }

    private fun startPolling() {
        val serial = _currentSerial.value ?: return

        val newPollingJob = viewModelScope.launch(dispatchers.io) {
            while (true) {
                val now = System.currentTimeMillis()
                val currentFilter = _appPackageFilter.value

                val cpuDeferred = async { adbClient.execute(GetCpuStats(), serial) }
                val memDeferred = async { adbClient.execute(GetMemoryInfo(), serial) }
                val battDeferred = async { adbClient.execute(GetBatteryInfo(), serial) }
                val appMemDeferred = if (currentFilter.isNotBlank()) {
                    async {
                        try {
                            adbClient.execute(GetAppMemoryInfo(currentFilter), serial)
                        } catch (_: Exception) {
                            null
                        }
                    }
                } else null

                val cpuResult = cpuDeferred.await()
                val memResult = memDeferred.await()
                val battResult = battDeferred.await()
                val appMemResult = appMemDeferred?.await()

                dataMutex.withLock {
                    // CPU: need two readings for delta
                    cpuResult.getOrNull()?.let { reading ->
                        val prev = lastCpuReading.get()
                        lastCpuReading.set(reading)
                        if (prev != null) {
                            val info = CpuParser.computeUsage(prev, reading)
                            latestCpuInfo.set(info)
                            cpuSeries.add(PerformanceDataPoint(now, info.overallPercent))
                        }
                    }

                    // Memory
                    memResult.getOrNull()?.let { mem ->
                        latestMemoryInfo.set(mem)
                        memorySeries.add(PerformanceDataPoint(now, mem.usagePercent.toFloat()))
                    }

                    // Battery
                    battResult.getOrNull()?.let { batt ->
                        latestBatteryInfo.set(batt)
                        batterySeries.add(PerformanceDataPoint(now, batt.level.toFloat()))
                        batteryTempSeries.add(PerformanceDataPoint(now, batt.temperature))
                    }

                    // App memory
                    if (appMemResult != null) {
                        latestAppMemory.set(appMemResult.getOrNull())
                    }
                }

                dirty.set(true)
                delay(_pollingIntervalMs.value)
            }
        }
        pollingJob.getAndSet(newPollingJob)?.cancel()

        val newEmitJob = viewModelScope.launch {
            while (true) {
                delay(EMIT_INTERVAL_MS)
                if (dirty.compareAndSet(true, false)) {
                    emitState()
                }
            }
        }
        emitJob.getAndSet(newEmitJob)?.cancel()
    }

    private fun stopPolling() {
        pollingJob.getAndSet(null)?.cancel()
        emitJob.getAndSet(null)?.cancel()
    }

    private fun resetData() {
        viewModelScope.launch {
            dataMutex.withLock { clearData() }
            emitState()
        }
    }

    private fun clearData() {
        cpuSeries.clear()
        memorySeries.clear()
        batterySeries.clear()
        batteryTempSeries.clear()
        lastCpuReading.set(null)
        latestCpuInfo.set(null)
        latestMemoryInfo.set(null)
        latestBatteryInfo.set(null)
        latestAppMemory.set(null)
        dirty.set(false)
    }

    private fun emitState() {
        viewModelScope.launch {
            val snapshot = dataMutex.withLock {
                PerformanceSnapshot(
                    cpuPoints = cpuSeries.snapshot(),
                    memoryPoints = memorySeries.snapshot(),
                    batteryPoints = batterySeries.snapshot(),
                    batteryTempPoints = batteryTempSeries.snapshot(),
                    cpuInfo = latestCpuInfo.get(),
                    memoryInfo = latestMemoryInfo.get(),
                    batteryInfo = latestBatteryInfo.get(),
                    appMemoryInfo = latestAppMemory.get(),
                )
            }
            _state.value = PerformanceState.Ready(
                isMonitoring = _isMonitoring.value,
                pollingIntervalMs = _pollingIntervalMs.value,
                appPackageFilter = _appPackageFilter.value,
                snapshot = snapshot,
            )
        }
    }

    private fun exportCsv(targetFile: File) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val snapshot = dataMutex.withLock {
                    Triple(
                        cpuSeries.snapshot(),
                        memorySeries.snapshot(),
                        batterySeries.snapshot(),
                    )
                }

                val allTimestamps = (snapshot.first.map { it.timestampMs } +
                    snapshot.second.map { it.timestampMs } +
                    snapshot.third.map { it.timestampMs }).distinct().sorted()

                val cpuMap = snapshot.first.associateBy { it.timestampMs }
                val memMap = snapshot.second.associateBy { it.timestampMs }
                val battMap = snapshot.third.associateBy { it.timestampMs }

                targetFile.bufferedWriter().use { writer ->
                    writer.appendLine("timestamp,cpu_percent,memory_percent,battery_level")
                    allTimestamps.forEach { ts ->
                        val cpu = cpuMap[ts]?.value?.let { "%.1f".format(it) } ?: ""
                        val mem = memMap[ts]?.value?.let { "%.1f".format(it) } ?: ""
                        val batt = battMap[ts]?.value?.let { "%.0f".format(it) } ?: ""
                        writer.appendLine("$ts,$cpu,$mem,$batt")
                    }
                }
                _actionResult.value = PerformanceResult.Success("CSV exported to ${targetFile.name}")
            } catch (e: Exception) {
                _actionResult.value = PerformanceResult.Failure("Export failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    companion object {
        private const val EMIT_INTERVAL_MS = 100L
        private val PACKAGE_NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.]*$")
    }
}

// Snapshot of all series data (immutable)
data class PerformanceSnapshot(
    val cpuPoints: List<PerformanceDataPoint> = emptyList(),
    val memoryPoints: List<PerformanceDataPoint> = emptyList(),
    val batteryPoints: List<PerformanceDataPoint> = emptyList(),
    val batteryTempPoints: List<PerformanceDataPoint> = emptyList(),
    val cpuInfo: CpuInfo? = null,
    val memoryInfo: MemoryInfo? = null,
    val batteryInfo: BatteryInfo? = null,
    val appMemoryInfo: AppMemoryInfo? = null,
)

// State
sealed class PerformanceState {
    data object NoDevice : PerformanceState()
    data class Ready(
        val isMonitoring: Boolean = false,
        val pollingIntervalMs: Long = 2000L,
        val appPackageFilter: String = "",
        val snapshot: PerformanceSnapshot = PerformanceSnapshot(),
    ) : PerformanceState()
}

// Intents
sealed class PerformanceIntent {
    data object StartMonitoring : PerformanceIntent()
    data object StopMonitoring : PerformanceIntent()
    data object ResetData : PerformanceIntent()
    data class SetPollingInterval(val intervalMs: Long) : PerformanceIntent()
    data class SetAppPackageFilter(val packageName: String) : PerformanceIntent()
    data class ExportCsv(val targetFile: File) : PerformanceIntent()
    data object DismissResult : PerformanceIntent()
}

// Results
sealed class PerformanceResult {
    data class Success(val message: String) : PerformanceResult()
    data class Failure(val message: String) : PerformanceResult()
}
