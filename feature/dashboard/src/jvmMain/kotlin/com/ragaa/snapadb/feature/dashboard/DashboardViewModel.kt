package com.ragaa.snapadb.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetBatteryInfo
import com.ragaa.snapadb.core.adb.command.GetMemoryInfo
import com.ragaa.snapadb.core.adb.command.GetProperties
import com.ragaa.snapadb.core.adb.command.GetStorageInfo
import com.ragaa.snapadb.core.adb.model.BatteryInfo
import com.ragaa.snapadb.core.adb.model.MemoryInfo
import com.ragaa.snapadb.core.adb.model.StorageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
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

        val properties = adbClient.execute(GetProperties(), serial).getOrNull() ?: emptyMap()
        val battery = adbClient.execute(GetBatteryInfo(), serial).getOrNull()
        val storageList = adbClient.execute(GetStorageInfo(), serial).getOrNull() ?: emptyList()
        val memory = adbClient.execute(GetMemoryInfo(), serial).getOrNull()

        val deviceInfo = DeviceInfo(
            model = properties["ro.product.model"] ?: "Unknown",
            manufacturer = properties["ro.product.manufacturer"] ?: "Unknown",
            androidVersion = properties["ro.build.version.release"] ?: "Unknown",
            sdkVersion = properties["ro.build.version.sdk"] ?: "Unknown",
            buildNumber = properties["ro.build.display.id"] ?: "",
            serial = serial,
        )

        // Filter to meaningful partitions
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
}

sealed class DashboardState {
    data object NoDevice : DashboardState()
    data object Loading : DashboardState()
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
