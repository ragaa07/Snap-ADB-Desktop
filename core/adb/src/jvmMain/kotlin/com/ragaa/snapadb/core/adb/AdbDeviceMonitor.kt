package com.ragaa.snapadb.core.adb

import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.command.ListDevices
import com.ragaa.snapadb.core.adb.model.AdbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdbDeviceMonitor(
    private val adbClient: AdbClient,
    private val dispatchers: DispatcherProvider,
) {

    private val _devices = MutableStateFlow<List<AdbDevice>>(emptyList())
    val devices: StateFlow<List<AdbDevice>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AdbDevice?>(null)
    val selectedDevice: StateFlow<AdbDevice?> = _selectedDevice.asStateFlow()

    private val _error = MutableStateFlow<Exception?>(null)
    val error: StateFlow<Exception?> = _error.asStateFlow()

    private var monitorScope: CoroutineScope? = null
    private var pollingJob: Job? = null
    private var hasAutoSelected = false

    fun selectDevice(device: AdbDevice?) {
        _selectedDevice.value = device
    }

    fun start() {
        if (pollingJob?.isActive == true) return
        val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        monitorScope = scope
        pollingJob = scope.launch {
            while (true) {
                adbClient.execute(ListDevices())
                    .onSuccess { deviceList ->
                        _error.value = null
                        _devices.value = deviceList
                        reconcileSelection(deviceList)
                    }
                    .onFailure { e ->
                        _error.value = e as? Exception
                    }
                delay(2_000L)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        monitorScope?.let { (it.coroutineContext[Job])?.cancel() }
        monitorScope = null
    }

    private fun reconcileSelection(deviceList: List<AdbDevice>) {
        val current = _selectedDevice.value

        if (current != null && deviceList.none { it.serial == current.serial }) {
            _selectedDevice.value = null
        }

        // Only auto-select on first discovery, not after user clears selection
        if (!hasAutoSelected && _selectedDevice.value == null && deviceList.isNotEmpty()) {
            _selectedDevice.value = deviceList.first()
            hasAutoSelected = true
        }
    }
}
