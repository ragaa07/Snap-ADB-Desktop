package com.ragaa.snapadb.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetBatteryInfo
import com.ragaa.snapadb.core.adb.command.GetMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetProperties
import com.ragaa.snapadb.core.adb.command.GetStorageInfo
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.StorageInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<DashboardState>(DashboardState.NoDevice)
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                if (device == null) {
                    _state.value = DashboardState.NoDevice
                } else {
                    loadDeviceInfo(device.serial)
                }
            }
        }
    }

    fun refresh() {
        val serial = deviceMonitor.selectedDevice.value?.serial ?: return
        viewModelScope.launch { loadDeviceInfo(serial) }
    }

    private suspend fun loadDeviceInfo(serial: String) {
        _state.value = DashboardState.Loading

        try {
            coroutineScope {
                val propertiesDeferred = async { adbClient.execute(GetProperties(), serial) }
                val batteryDeferred = async { adbClient.execute(GetBatteryInfo(), serial) }
                val storageDeferred = async { adbClient.execute(GetStorageInfo(), serial) }
                val memoryDeferred = async { adbClient.execute(GetMemoryInfo(), serial) }

                val properties = propertiesDeferred.await().getOrNull() ?: emptyMap()
                val battery = batteryDeferred.await().getOrNull()
                val storageList = storageDeferred.await().getOrNull() ?: emptyList()
                val memory = memoryDeferred.await().getOrNull()

                if (properties.isEmpty() && battery == null && memory == null) {
                    _state.value = DashboardState.Error("Failed to retrieve device information. Is the device connected?")
                    return@coroutineScope
                }

                val deviceInfo = DeviceInfo(
                    model = properties["ro.product.model"] ?: "Unknown",
                    manufacturer = properties["ro.product.manufacturer"] ?: "Unknown",
                    androidVersion = properties["ro.build.version.release"] ?: "Unknown",
                    sdkVersion = properties["ro.build.version.sdk"] ?: "Unknown",
                    buildNumber = properties["ro.build.display.id"] ?: "",
                    serial = serial,
                )

                val relevantStorage = storageList.filter { info ->
                    info.mountedOn in listOf("/data", "/storage/emulated", "/", "/system")
                            || info.mountedOn.startsWith("/storage/")
                }

                _state.value = DashboardState.Loaded(
                    deviceInfo = deviceInfo,
                    battery = battery,
                    storage = relevantStorage,
                    memory = memory,
                )
            }
        } catch (e: Exception) {
            _state.value = DashboardState.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class DashboardState {
    data object NoDevice : DashboardState()
    data object Loading : DashboardState()
    data class Error(val message: String) : DashboardState()
    data class Loaded(
        val deviceInfo: DeviceInfo,
        val battery: BatteryInfo?,
        val storage: List<StorageInfo>,
        val memory: MemoryInfo?,
    ) : DashboardState()
}

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: String,
    val buildNumber: String,
    val serial: String,
)
