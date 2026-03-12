package com.ragaa.snapadb.feature.multidevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.ActionResult
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ConnectDevice
import com.ragaa.snapadb.core.adb.command.DisconnectDevice
import com.ragaa.snapadb.core.adb.command.PairDevice
import com.ragaa.snapadb.core.adb.model.AdbDevice
import com.ragaa.snapadb.database.SnapAdbDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiDeviceViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val database: SnapAdbDatabase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    val devices: StateFlow<List<AdbDevice>> = deviceMonitor.devices
    val selectedDevice: StateFlow<AdbDevice?> = deviceMonitor.selectedDevice

    private val _nicknames = MutableStateFlow<Map<String, String>>(emptyMap())

    val devicesWithNicknames: StateFlow<List<DeviceWithNickname>> =
        combine(devices, _nicknames) { deviceList, nicknameMap ->
            deviceList.map { device ->
                DeviceWithNickname(device, nicknameMap[device.serial])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _connectResult = MutableStateFlow<ActionResult?>(null)
    val connectResult: StateFlow<ActionResult?> = _connectResult.asStateFlow()

    private val _pairResult = MutableStateFlow<ActionResult?>(null)
    val pairResult: StateFlow<ActionResult?> = _pairResult.asStateFlow()

    init {
        loadNicknames()
    }

    fun selectDevice(device: AdbDevice) {
        deviceMonitor.selectDevice(device)
    }

    fun connect(address: String) {
        viewModelScope.launch {
            _connectResult.value = ActionResult.Loading
            adbClient.execute(ConnectDevice(address))
                .onSuccess { _connectResult.value = ActionResult.Success(it) }
                .onFailure { _connectResult.value = ActionResult.Error(it.message ?: "Connection failed") }
        }
    }

    fun disconnect(address: String) {
        viewModelScope.launch {
            adbClient.execute(DisconnectDevice(address))
        }
    }

    fun pair(address: String, code: String) {
        viewModelScope.launch {
            _pairResult.value = ActionResult.Loading
            adbClient.execute(PairDevice(address, code))
                .onSuccess { _pairResult.value = ActionResult.Success(it) }
                .onFailure { _pairResult.value = ActionResult.Error(it.message ?: "Pairing failed") }
        }
    }

    fun setNickname(serial: String, nickname: String) {
        viewModelScope.launch {
            withContext(dispatchers.io) {
                if (nickname.isBlank()) {
                    database.snapAdbQueries.deleteNickname(serial)
                } else {
                    database.snapAdbQueries.upsertNickname(serial, nickname.trim())
                }
            }
            loadNicknames()
        }
    }

    fun clearConnectResult() { _connectResult.value = null }
    fun clearPairResult() { _pairResult.value = null }

    private fun loadNicknames() {
        viewModelScope.launch {
            val map = withContext(dispatchers.io) {
                database.snapAdbQueries.getAllNicknames().executeAsList()
                    .associate { it.serial to it.nickname }
            }
            _nicknames.value = map
        }
    }
}

data class DeviceWithNickname(
    val device: AdbDevice,
    val nickname: String?,
) {
    val displayName: String get() = nickname ?: device.model.ifEmpty { device.serial }
}

