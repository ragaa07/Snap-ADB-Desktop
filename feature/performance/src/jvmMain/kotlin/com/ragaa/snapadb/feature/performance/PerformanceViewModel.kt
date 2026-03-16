package com.ragaa.snapadb.feature.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetAppMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetAppUid
import com.ragaa.snapadb.core.adb.command.GetBatteryInfo
import com.ragaa.snapadb.core.adb.command.GetCpuStats
import com.ragaa.snapadb.core.adb.command.GetGpuInfo
import com.ragaa.snapadb.core.adb.command.GetMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetNetworkStats
import com.ragaa.snapadb.core.adb.command.GetNetworkStatsDev
import com.ragaa.snapadb.core.adb.model.AppMemoryInfo
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.CpuInfo
import com.ragaa.snapadb.core.adb.model.CpuRawReading
import com.ragaa.snapadb.core.adb.model.GpuInfo
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.NetworkIoInfo
import com.ragaa.snapadb.core.adb.model.NetworkIoRate
import com.ragaa.snapadb.core.adb.parser.CpuParser
import com.ragaa.snapadb.core.adb.parser.NetworkIoParser
import com.ragaa.snapadb.feature.performance.model.PerformanceDataPoint
import com.ragaa.snapadb.feature.performance.model.RollingTimeSeries
import com.ragaa.snapadb.feature.performance.model.ThresholdAlert
import com.ragaa.snapadb.feature.performance.model.ThresholdConfig
import com.ragaa.snapadb.feature.performance.session.PerformanceSessionRepository
import com.ragaa.snapadb.feature.performance.session.PerformanceSessionSummary
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
    private val sessionRepository: PerformanceSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PerformanceState>(PerformanceState.NoDevice)
    val state: StateFlow<PerformanceState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<PerformanceResult?>(null)
    val actionResult: StateFlow<PerformanceResult?> = _actionResult.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _pollingIntervalMs = MutableStateFlow(2000L)
    private val _isMonitoring = MutableStateFlow(false)
    private val _appPackageFilter = MutableStateFlow("")
    private val _thresholdConfig = MutableStateFlow(ThresholdConfig())
    private val _activeAlerts = MutableStateFlow<List<ThresholdAlert>>(emptyList())
    private val _sessions = MutableStateFlow<List<PerformanceSessionSummary>>(emptyList())
    private val _viewingSessionId = MutableStateFlow<Long?>(null)

    private val pollingJob = AtomicReference<Job?>(null)
    private val emitJob = AtomicReference<Job?>(null)

    // Data under mutex — all series and latest readings
    private val dataMutex = Mutex()
    private val cpuSeries = RollingTimeSeries()
    private val memorySeries = RollingTimeSeries()
    private val batterySeries = RollingTimeSeries()
    private val batteryTempSeries = RollingTimeSeries()
    private val networkRxSeries = RollingTimeSeries()
    private val networkTxSeries = RollingTimeSeries()
    private val lastCpuReading = AtomicReference<CpuRawReading?>(null)
    private val latestCpuInfo = AtomicReference<CpuInfo?>(null)
    private val latestMemoryInfo = AtomicReference<MemoryInfo?>(null)
    private val latestBatteryInfo = AtomicReference<BatteryInfo?>(null)
    private val latestAppMemory = AtomicReference<AppMemoryInfo?>(null)
    private val latestGpuInfo = AtomicReference<GpuInfo?>(null)
    private val lastNetworkReading = AtomicReference<NetworkIoInfo?>(null)
    private val latestNetworkRate = AtomicReference<NetworkIoRate?>(null)
    private val cachedAppUid = AtomicReference<Int?>(null)
    private val dirty = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                stopPolling()
                dataMutex.withLock { clearData() }
                _currentSerial.value = device?.serial
                _actionResult.value = null
                _viewingSessionId.value = null

                if (device == null) {
                    _state.value = PerformanceState.NoDevice
                } else {
                    _state.value = PerformanceState.Ready()
                    refreshSessions()
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
                cachedAppUid.set(null)
                lastNetworkReading.set(null)
                if (pkg.isNotBlank()) {
                    fetchAppUid(pkg)
                }
                dirty.set(true)
            }
            is PerformanceIntent.ExportCsv -> exportCsv(intent.targetFile)
            is PerformanceIntent.DismissResult -> _actionResult.value = null
            is PerformanceIntent.SetThresholdConfig -> {
                _thresholdConfig.value = intent.config
                emitState()
            }
            is PerformanceIntent.DismissAlerts -> {
                _activeAlerts.value = emptyList()
                emitState()
            }
            is PerformanceIntent.SaveSession -> saveSession()
            is PerformanceIntent.LoadSession -> loadSession(intent.sessionId)
            is PerformanceIntent.DeleteSession -> deleteSession(intent.sessionId)
            is PerformanceIntent.ExitSessionView -> {
                _viewingSessionId.value = null
                viewModelScope.launch {
                    dataMutex.withLock { clearData() }
                    emitState()
                }
            }
            is PerformanceIntent.RefreshSessions -> refreshSessions()
        }
    }

    private fun startMonitoring() {
        if (_currentSerial.value == null) return
        _viewingSessionId.value = null
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
                        } catch (e: Exception) {
                            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                            null
                        }
                    }
                } else null
                val gpuDeferred = if (currentFilter.isNotBlank()) {
                    async {
                        try {
                            adbClient.execute(GetGpuInfo(currentFilter), serial)
                        } catch (e: Exception) {
                            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                            null
                        }
                    }
                } else null
                val uid = cachedAppUid.get()
                val networkDeferred = if (uid != null) {
                    async {
                        try {
                            val result = adbClient.execute(GetNetworkStats(), serial)
                            result.getOrNull()?.let { NetworkIoParser.parse(it, uid, now) }
                        } catch (e: Exception) {
                            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                            try {
                                val result = adbClient.execute(GetNetworkStatsDev(), serial)
                                result.getOrNull()?.let { NetworkIoParser.parseDevLevel(it, now) }
                            } catch (e2: Exception) {
                                if (e2 is kotlin.coroutines.cancellation.CancellationException) throw e2
                                null
                            }
                        }
                    }
                } else null

                val cpuResult = cpuDeferred.await()
                val memResult = memDeferred.await()
                val battResult = battDeferred.await()
                val appMemResult = appMemDeferred?.await()
                val gpuResult = gpuDeferred?.await()
                val networkResult = networkDeferred?.await()

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

                    // GPU
                    if (gpuResult != null) {
                        latestGpuInfo.set(gpuResult.getOrNull())
                    }

                    // Network
                    networkResult?.let { current ->
                        val prev = lastNetworkReading.get()
                        lastNetworkReading.set(current)
                        if (prev != null) {
                            val dtSec = (current.timestampMs - prev.timestampMs) / 1000f
                            if (dtSec > 0f) {
                                val rxRate = (current.rxBytes - prev.rxBytes) / dtSec
                                val txRate = (current.txBytes - prev.txBytes) / dtSec
                                val rate = NetworkIoRate(rxRate.coerceAtLeast(0f), txRate.coerceAtLeast(0f))
                                latestNetworkRate.set(rate)
                                networkRxSeries.add(PerformanceDataPoint(now, rate.rxBytesPerSec))
                                networkTxSeries.add(PerformanceDataPoint(now, rate.txBytesPerSec))
                            }
                        }
                    }
                }

                // Threshold checking
                checkThresholds()

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

    private fun checkThresholds() {
        val config = _thresholdConfig.value
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<ThresholdAlert>()

        config.cpuPercent?.let { threshold ->
            latestCpuInfo.get()?.let { cpu ->
                if (cpu.overallPercent > threshold) {
                    alerts.add(ThresholdAlert("CPU", cpu.overallPercent, threshold, now))
                }
            }
        }
        config.memoryPercent?.let { threshold ->
            latestMemoryInfo.get()?.let { mem ->
                if (mem.usagePercent > threshold) {
                    alerts.add(ThresholdAlert("Memory", mem.usagePercent.toFloat(), threshold, now))
                }
            }
        }
        config.batteryTempC?.let { threshold ->
            latestBatteryInfo.get()?.let { batt ->
                if (batt.temperature > threshold) {
                    alerts.add(ThresholdAlert("Battery Temp", batt.temperature, threshold, now))
                }
            }
        }

        if (alerts.isNotEmpty()) {
            _activeAlerts.value = alerts
        }
    }

    private fun fetchAppUid(packageName: String) {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            try {
                val result = adbClient.execute(GetAppUid(packageName), serial)
                result.getOrNull()?.let { cachedAppUid.set(it) }
            } catch (_: Exception) {
                // UID fetch failed — network stats won't be available
            }
        }
    }

    private fun stopPolling() {
        pollingJob.getAndSet(null)?.cancel()
        emitJob.getAndSet(null)?.cancel()
    }

    private fun resetData() {
        viewModelScope.launch {
            dataMutex.withLock { clearData() }
            _activeAlerts.value = emptyList()
            emitState()
        }
    }

    private fun clearData() {
        cpuSeries.clear()
        memorySeries.clear()
        batterySeries.clear()
        batteryTempSeries.clear()
        networkRxSeries.clear()
        networkTxSeries.clear()
        lastCpuReading.set(null)
        latestCpuInfo.set(null)
        latestMemoryInfo.set(null)
        latestBatteryInfo.set(null)
        latestAppMemory.set(null)
        latestGpuInfo.set(null)
        lastNetworkReading.set(null)
        latestNetworkRate.set(null)
        cachedAppUid.set(null)
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
                    networkRxPoints = networkRxSeries.snapshot(),
                    networkTxPoints = networkTxSeries.snapshot(),
                    cpuInfo = latestCpuInfo.get(),
                    memoryInfo = latestMemoryInfo.get(),
                    batteryInfo = latestBatteryInfo.get(),
                    appMemoryInfo = latestAppMemory.get(),
                    gpuInfo = latestGpuInfo.get(),
                    networkIoRate = latestNetworkRate.get(),
                )
            }
            _state.value = PerformanceState.Ready(
                isMonitoring = _isMonitoring.value,
                pollingIntervalMs = _pollingIntervalMs.value,
                appPackageFilter = _appPackageFilter.value,
                snapshot = snapshot,
                thresholdConfig = _thresholdConfig.value,
                activeAlerts = _activeAlerts.value,
                sessions = _sessions.value,
                viewingSessionId = _viewingSessionId.value,
            )
        }
    }

    private fun saveSession() {
        val serial = _currentSerial.value ?: return
        viewModelScope.launch(dispatchers.io) {
            try {
                val seriesData = dataMutex.withLock {
                    buildMap {
                        if (cpuSeries.size > 0) put("cpu", cpuSeries.snapshot())
                        if (memorySeries.size > 0) put("memory", memorySeries.snapshot())
                        if (batterySeries.size > 0) put("battery", batterySeries.snapshot())
                        if (batteryTempSeries.size > 0) put("battery_temp", batteryTempSeries.snapshot())
                        if (networkRxSeries.size > 0) put("network_rx", networkRxSeries.snapshot())
                        if (networkTxSeries.size > 0) put("network_tx", networkTxSeries.snapshot())
                    }
                }
                if (seriesData.isEmpty()) {
                    _actionResult.value = PerformanceResult.Failure("No data to save")
                    return@launch
                }
                sessionRepository.saveSession(
                    serial = serial,
                    deviceName = null,
                    appPackage = _appPackageFilter.value.ifBlank { null },
                    pollingIntervalMs = _pollingIntervalMs.value,
                    seriesData = seriesData,
                )
                _actionResult.value = PerformanceResult.Success("Session saved")
                refreshSessions()
            } catch (e: Exception) {
                _actionResult.value = PerformanceResult.Failure("Save failed: ${e.message}")
            }
        }
    }

    private fun loadSession(sessionId: Long) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val data = sessionRepository.loadSession(sessionId)
                dataMutex.withLock {
                    clearData()
                    data["cpu"]?.forEach { cpuSeries.add(it) }
                    data["memory"]?.forEach { memorySeries.add(it) }
                    data["battery"]?.forEach { batterySeries.add(it) }
                    data["battery_temp"]?.forEach { batteryTempSeries.add(it) }
                    data["network_rx"]?.forEach { networkRxSeries.add(it) }
                    data["network_tx"]?.forEach { networkTxSeries.add(it) }
                }
                _viewingSessionId.value = sessionId
                _isMonitoring.value = false
                stopPolling()
                dirty.set(true)
                emitState()
            } catch (e: Exception) {
                _actionResult.value = PerformanceResult.Failure("Load failed: ${e.message}")
            }
        }
    }

    private fun deleteSession(sessionId: Long) {
        viewModelScope.launch(dispatchers.io) {
            try {
                sessionRepository.deleteSession(sessionId)
                if (_viewingSessionId.value == sessionId) {
                    _viewingSessionId.value = null
                    dataMutex.withLock { clearData() }
                }
                _actionResult.value = PerformanceResult.Success("Session deleted")
                refreshSessions()
                emitState()
            } catch (e: Exception) {
                _actionResult.value = PerformanceResult.Failure("Delete failed: ${e.message}")
            }
        }
    }

    private fun refreshSessions() {
        viewModelScope.launch(dispatchers.io) {
            try {
                _sessions.value = sessionRepository.getAllSessions()
                dirty.set(true)
            } catch (_: Exception) {
                // Non-critical — session list stays stale
            }
        }
    }

    private fun exportCsv(targetFile: File) {
        viewModelScope.launch(dispatchers.io) {
            try {
                val snapshot = dataMutex.withLock {
                    ExportData(
                        cpu = cpuSeries.snapshot(),
                        memory = memorySeries.snapshot(),
                        battery = batterySeries.snapshot(),
                        batteryTemp = batteryTempSeries.snapshot(),
                        networkRx = networkRxSeries.snapshot(),
                        networkTx = networkTxSeries.snapshot(),
                    )
                }

                val allTimestamps = (snapshot.cpu.map { it.timestampMs } +
                    snapshot.memory.map { it.timestampMs } +
                    snapshot.battery.map { it.timestampMs } +
                    snapshot.batteryTemp.map { it.timestampMs } +
                    snapshot.networkRx.map { it.timestampMs } +
                    snapshot.networkTx.map { it.timestampMs }).distinct().sorted()

                val cpuMap = snapshot.cpu.associateBy { it.timestampMs }
                val memMap = snapshot.memory.associateBy { it.timestampMs }
                val battMap = snapshot.battery.associateBy { it.timestampMs }
                val battTempMap = snapshot.batteryTemp.associateBy { it.timestampMs }
                val rxMap = snapshot.networkRx.associateBy { it.timestampMs }
                val txMap = snapshot.networkTx.associateBy { it.timestampMs }

                targetFile.bufferedWriter().use { writer ->
                    writer.appendLine("timestamp,cpu_percent,memory_percent,battery_level,battery_temp_c,network_rx_bps,network_tx_bps")
                    allTimestamps.forEach { ts ->
                        val cpu = cpuMap[ts]?.value?.let { "%.1f".format(it) } ?: ""
                        val mem = memMap[ts]?.value?.let { "%.1f".format(it) } ?: ""
                        val batt = battMap[ts]?.value?.let { "%.0f".format(it) } ?: ""
                        val battTemp = battTempMap[ts]?.value?.let { "%.1f".format(it) } ?: ""
                        val rx = rxMap[ts]?.value?.let { "%.0f".format(it) } ?: ""
                        val tx = txMap[ts]?.value?.let { "%.0f".format(it) } ?: ""
                        writer.appendLine("$ts,$cpu,$mem,$batt,$battTemp,$rx,$tx")
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

private data class ExportData(
    val cpu: List<PerformanceDataPoint>,
    val memory: List<PerformanceDataPoint>,
    val battery: List<PerformanceDataPoint>,
    val batteryTemp: List<PerformanceDataPoint>,
    val networkRx: List<PerformanceDataPoint>,
    val networkTx: List<PerformanceDataPoint>,
)

// Snapshot of all series data (immutable)
data class PerformanceSnapshot(
    val cpuPoints: List<PerformanceDataPoint> = emptyList(),
    val memoryPoints: List<PerformanceDataPoint> = emptyList(),
    val batteryPoints: List<PerformanceDataPoint> = emptyList(),
    val batteryTempPoints: List<PerformanceDataPoint> = emptyList(),
    val networkRxPoints: List<PerformanceDataPoint> = emptyList(),
    val networkTxPoints: List<PerformanceDataPoint> = emptyList(),
    val cpuInfo: CpuInfo? = null,
    val memoryInfo: MemoryInfo? = null,
    val batteryInfo: BatteryInfo? = null,
    val appMemoryInfo: AppMemoryInfo? = null,
    val gpuInfo: GpuInfo? = null,
    val networkIoRate: NetworkIoRate? = null,
)

// State
sealed class PerformanceState {
    data object NoDevice : PerformanceState()
    data class Ready(
        val isMonitoring: Boolean = false,
        val pollingIntervalMs: Long = 2000L,
        val appPackageFilter: String = "",
        val snapshot: PerformanceSnapshot = PerformanceSnapshot(),
        val thresholdConfig: ThresholdConfig = ThresholdConfig(),
        val activeAlerts: List<ThresholdAlert> = emptyList(),
        val sessions: List<PerformanceSessionSummary> = emptyList(),
        val viewingSessionId: Long? = null,
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
    data class SetThresholdConfig(val config: ThresholdConfig) : PerformanceIntent()
    data object DismissAlerts : PerformanceIntent()
    data object SaveSession : PerformanceIntent()
    data class LoadSession(val sessionId: Long) : PerformanceIntent()
    data class DeleteSession(val sessionId: Long) : PerformanceIntent()
    data object ExitSessionView : PerformanceIntent()
    data object RefreshSessions : PerformanceIntent()
}

// Results
sealed class PerformanceResult {
    data class Success(val message: String) : PerformanceResult()
    data class Failure(val message: String) : PerformanceResult()
}
