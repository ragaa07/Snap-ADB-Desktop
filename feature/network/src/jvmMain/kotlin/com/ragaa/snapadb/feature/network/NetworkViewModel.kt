package com.ragaa.snapadb.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.ClearHttpProxy
import com.ragaa.snapadb.core.adb.command.GetHttpProxy
import com.ragaa.snapadb.core.adb.command.InstallUserCert
import com.ragaa.snapadb.core.adb.command.OpenSecuritySettings
import com.ragaa.snapadb.core.adb.command.PushFile
import com.ragaa.snapadb.core.adb.command.SetHttpProxy
import com.ragaa.snapadb.feature.network.model.ProxyPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<NetworkState>(NetworkState.NoDevice)
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<NetworkResult?>(null)
    val actionResult: StateFlow<NetworkResult?> = _actionResult.asStateFlow()

    private var currentSerial: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                currentSerial = device?.serial
                _actionResult.value = null
                if (device == null) {
                    _state.value = NetworkState.NoDevice
                } else {
                    loadProxy(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: NetworkIntent) {
        when (intent) {
            is NetworkIntent.SetProxy -> setProxy(intent.host, intent.port)
            is NetworkIntent.ClearProxy -> clearProxy()
            is NetworkIntent.ApplyPreset -> applyPreset(intent.preset)
            is NetworkIntent.InstallCert -> installCert(intent.localPath)
            is NetworkIntent.OpenSecuritySettings -> openSecuritySettings()
            is NetworkIntent.Refresh -> currentSerial?.let { loadProxy(it) }
            is NetworkIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadProxy(serial: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = NetworkState.Loading
            try {
                val proxy = withContext(dispatchers.io) {
                    adbClient.execute(GetHttpProxy(), serial)
                }.getOrThrow()
                _state.value = NetworkState.Ready(currentProxy = proxy)
            } catch (e: Exception) {
                _state.value = NetworkState.Error(e.message ?: "Failed to load proxy info")
            }
        }
    }

    private fun executeAction(
        description: String,
        refreshAfter: Boolean = true,
        block: suspend (serial: String) -> Result<String>,
    ) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) { block(serial) }
                .onSuccess {
                    _actionResult.value = NetworkResult.Success(description)
                    if (refreshAfter) loadProxy(serial)
                }
                .onFailure { e ->
                    _actionResult.value = NetworkResult.Failure("$description failed: ${e.message}")
                }
        }
    }

    private fun setProxy(host: String, port: Int) =
        executeAction("Proxy set to $host:$port") { serial ->
            adbClient.execute(SetHttpProxy(host, port), serial)
        }

    private fun clearProxy() =
        executeAction("Proxy cleared") { serial ->
            adbClient.execute(ClearHttpProxy(), serial)
        }

    private fun applyPreset(preset: ProxyPreset) =
        executeAction("${preset.displayName} proxy applied") { serial ->
            adbClient.execute(SetHttpProxy(preset.defaultHost, preset.defaultPort), serial)
        }

    private fun installCert(localPath: String) =
        executeAction("Certificate pushed — complete installation on device", refreshAfter = false) { serial ->
            // Push cert to device download folder
            adbClient.execute(
                PushFile(localPath, "/sdcard/Download/.snapadb_cert_tmp"),
                serial,
            ).getOrThrow()
            // Open the install intent
            adbClient.execute(InstallUserCert(localPath), serial)
        }

    private fun openSecuritySettings() =
        executeAction("Security settings opened on device", refreshAfter = false) { serial ->
            adbClient.execute(OpenSecuritySettings(), serial)
        }
}

sealed class NetworkState {
    data object NoDevice : NetworkState()
    data object Loading : NetworkState()
    data class Error(val message: String) : NetworkState()
    data class Ready(val currentProxy: String) : NetworkState()
}

sealed class NetworkIntent {
    data class SetProxy(val host: String, val port: Int) : NetworkIntent()
    data object ClearProxy : NetworkIntent()
    data class ApplyPreset(val preset: ProxyPreset) : NetworkIntent()
    data class InstallCert(val localPath: String) : NetworkIntent()
    data object OpenSecuritySettings : NetworkIntent()
    data object Refresh : NetworkIntent()
    data object DismissResult : NetworkIntent()
}

sealed class NetworkResult {
    data class Success(val message: String) : NetworkResult()
    data class Failure(val message: String) : NetworkResult()
}
