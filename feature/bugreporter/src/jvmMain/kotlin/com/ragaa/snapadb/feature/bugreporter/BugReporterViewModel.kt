package com.ragaa.snapadb.feature.bugreporter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.DumpLogcat
import com.ragaa.snapadb.core.adb.command.GetBatteryInfo
import com.ragaa.snapadb.core.adb.command.GetMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetPackageInfo
import com.ragaa.snapadb.core.adb.command.GetProperties
import com.ragaa.snapadb.core.adb.command.GetStorageInfo
import com.ragaa.snapadb.core.adb.command.ListPackages
import com.ragaa.snapadb.core.adb.command.PullFile
import com.ragaa.snapadb.core.adb.command.RemoveRemoteFile
import com.ragaa.snapadb.core.adb.command.TakeScreenshot
import com.ragaa.snapadb.feature.bugreporter.model.BugReport
import com.ragaa.snapadb.feature.bugreporter.model.DeviceSnapshot
import com.ragaa.snapadb.feature.bugreporter.model.ScreenshotData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities
import kotlin.coroutines.cancellation.CancellationException

class BugReporterViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<BugReporterState>(BugReporterState.NoDevice)
    val state: StateFlow<BugReporterState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<BugReporterResult?>(null)
    val actionResult: StateFlow<BugReporterResult?> = _actionResult.asStateFlow()

    private val _packageSuggestions = MutableStateFlow<List<String>>(emptyList())
    val packageSuggestions: StateFlow<List<String>> = _packageSuggestions.asStateFlow()

    private val _currentSerial = MutableStateFlow<String?>(null)
    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())
    private val _selectedPackage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                _currentSerial.value = device?.serial
                _installedPackages.value = emptyList()
                _selectedPackage.value = null
                _actionResult.value = null
                _packageSuggestions.value = emptyList()
                if (device == null) {
                    _state.value = BugReporterState.NoDevice
                } else {
                    _state.value = BugReporterState.Ready
                    loadInstalledPackages(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: BugReporterIntent) {
        when (intent) {
            is BugReporterIntent.UpdatePackageQuery -> updatePackageQuery(intent.query)
            is BugReporterIntent.SetAppPackage -> {
                _selectedPackage.value = intent.packageName
                _packageSuggestions.value = emptyList()
            }
            is BugReporterIntent.CaptureReport -> captureReport()
            is BugReporterIntent.UpdateTitle -> updateReport { it.copy(title = intent.title) }
            is BugReporterIntent.UpdateDescription -> updateReport { it.copy(description = intent.description) }
            is BugReporterIntent.UpdateReproSteps -> updateReport { it.copy(reproSteps = intent.steps) }
            is BugReporterIntent.ExportZip -> exportZip(intent.file)
            is BugReporterIntent.CopyMarkdown -> copyMarkdown()
            is BugReporterIntent.Reset -> {
                _selectedPackage.value = null
                _state.value = BugReporterState.Ready
            }
            is BugReporterIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadInstalledPackages(serial: String) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(ListPackages(), serial)
            }.onSuccess { apps ->
                _installedPackages.value = apps.map { it.packageName }.sorted()
            }
        }
    }

    private fun updatePackageQuery(query: String) {
        val packages = _installedPackages.value
        _packageSuggestions.value = if (query.length >= 2) {
            packages.filter { it.contains(query, ignoreCase = true) }.take(10)
        } else {
            emptyList()
        }
    }

    private fun updateReport(transform: (BugReport) -> BugReport) {
        val current = _state.value as? BugReporterState.Editing ?: return
        _state.value = BugReporterState.Editing(transform(current.report))
    }

    private fun captureReport() {
        val serial = _currentSerial.value ?: return
        val selectedPkg = _selectedPackage.value

        _state.value = BugReporterState.Capturing("Capturing report...")

        viewModelScope.launch {
            try {
                val report = withContext(dispatchers.io) {
                    coroutineScope {
                        val screenshotDeferred = async { captureScreenshot(serial) }
                        val logcatDeferred = async {
                            adbClient.execute(DumpLogcat(), serial).getOrNull() ?: ""
                        }
                        val propsDeferred = async {
                            adbClient.execute(GetProperties(), serial).getOrNull() ?: emptyMap()
                        }
                        val batteryDeferred = async {
                            adbClient.execute(GetBatteryInfo(), serial).getOrNull()
                        }
                        val memoryDeferred = async {
                            adbClient.execute(GetMemoryInfo(), serial).getOrNull()
                        }
                        val storageDeferred = async {
                            adbClient.execute(GetStorageInfo(), serial).getOrNull()
                        }
                        val appInfoDeferred = if (selectedPkg != null) {
                            async {
                                adbClient.execute(GetPackageInfo(selectedPkg), serial).getOrNull()
                            }
                        } else null

                        val screenshot = screenshotDeferred.await()
                        val logcat = logcatDeferred.await()
                        val props = propsDeferred.await()
                        val battery = batteryDeferred.await()
                        val memory = memoryDeferred.await()
                        val storage = storageDeferred.await()
                        val appInfo = appInfoDeferred?.await()

                        val deviceSnapshot = DeviceSnapshot(
                            model = props["ro.product.model"] ?: "Unknown",
                            manufacturer = props["ro.product.manufacturer"] ?: "Unknown",
                            androidVersion = props["ro.build.version.release"] ?: "Unknown",
                            sdkVersion = props["ro.build.version.sdk"] ?: "Unknown",
                            buildId = props["ro.build.display.id"] ?: "Unknown",
                            serial = serial,
                        )

                        val timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.now())

                        BugReport(
                            timestamp = timestamp,
                            title = "Bug Report — ${deviceSnapshot.model}",
                            description = "",
                            reproSteps = DEFAULT_REPRO_STEPS,
                            deviceInfo = deviceSnapshot,
                            batteryInfo = battery,
                            memoryInfo = memory,
                            storageInfo = storage,
                            appInfo = appInfo,
                            screenshot = screenshot?.let { ScreenshotData(it) },
                            logcat = logcat,
                        )
                    }
                }
                _state.value = BugReporterState.Editing(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BugReporterState.Error(e.message ?: "Failed to capture report")
            }
        }
    }

    private suspend fun captureScreenshot(serial: String): ByteArray? {
        return try {
            val remotePath = "/sdcard/snapadb_bugreport_screenshot.png"
            adbClient.execute(TakeScreenshot(remotePath), serial).getOrNull() ?: return null

            val tempFile = File.createTempFile("snapadb_screenshot", ".png")
            try {
                adbClient.execute(PullFile(remotePath, tempFile.absolutePath), serial).getOrNull() ?: return null
                val bytes = tempFile.readBytes()
                bytes.takeIf { it.isNotEmpty() }
            } finally {
                tempFile.delete()
                try {
                    adbClient.execute(RemoveRemoteFile(remotePath), serial)
                } catch (_: Exception) {
                    // Best effort cleanup
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun exportZip(file: File) {
        val report = (_state.value as? BugReporterState.Editing)?.report ?: return
        viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    ReportGenerator.exportZip(report, file)
                }
                _actionResult.value = BugReporterResult.Success("Report exported to ${file.name}")
            } catch (e: Exception) {
                _actionResult.value = BugReporterResult.Failure("Export failed: ${e.message}")
            }
        }
    }

    private fun copyMarkdown() {
        val report = (_state.value as? BugReporterState.Editing)?.report ?: return
        viewModelScope.launch {
            val markdown = withContext(dispatchers.io) {
                ReportGenerator.generateMarkdown(report)
            }
            SwingUtilities.invokeLater {
                val selection = StringSelection(markdown)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            }
            _actionResult.value = BugReporterResult.Success("Markdown copied to clipboard")
        }
    }

    companion object {
        const val DEFAULT_REPRO_STEPS = """## Steps to Reproduce
1.
2.
3.

## Expected Behavior


## Actual Behavior
"""
    }
}

sealed class BugReporterState {
    data object NoDevice : BugReporterState()
    data object Ready : BugReporterState()
    data class Capturing(val status: String) : BugReporterState()
    data class Editing(val report: BugReport) : BugReporterState()
    data class Error(val message: String) : BugReporterState()
}

sealed class BugReporterIntent {
    data class UpdatePackageQuery(val query: String) : BugReporterIntent()
    data class SetAppPackage(val packageName: String) : BugReporterIntent()
    data object CaptureReport : BugReporterIntent()
    data class UpdateTitle(val title: String) : BugReporterIntent()
    data class UpdateDescription(val description: String) : BugReporterIntent()
    data class UpdateReproSteps(val steps: String) : BugReporterIntent()
    data class ExportZip(val file: File) : BugReporterIntent()
    data object CopyMarkdown : BugReporterIntent()
    data object Reset : BugReporterIntent()
    data object DismissResult : BugReporterIntent()
}

sealed class BugReporterResult {
    data class Success(val message: String) : BugReporterResult()
    data class Failure(val message: String) : BugReporterResult()
}
