package com.ragaa.snapadb.feature.devicecontrols

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetAnimationScale
import com.ragaa.snapadb.core.adb.command.GetDarkMode
import com.ragaa.snapadb.core.adb.command.GetDontKeepActivities
import com.ragaa.snapadb.core.adb.command.GetFontScale
import com.ragaa.snapadb.core.adb.command.GetLocale
import com.ragaa.snapadb.core.adb.command.GetScreenDensity
import com.ragaa.snapadb.core.adb.command.GetScreenSize
import com.ragaa.snapadb.core.adb.command.GetSetting
import com.ragaa.snapadb.core.adb.command.PutSetting
import com.ragaa.snapadb.core.adb.command.RebootDevice
import com.ragaa.snapadb.core.adb.command.ResetScreenDensity
import com.ragaa.snapadb.core.adb.command.ResetScreenSize
import com.ragaa.snapadb.core.adb.command.SendKeyEvent
import com.ragaa.snapadb.core.adb.command.SendSwipe
import com.ragaa.snapadb.core.adb.command.SendTap
import com.ragaa.snapadb.core.adb.command.SendText
import com.ragaa.snapadb.core.adb.command.SetAirplaneMode
import com.ragaa.snapadb.core.adb.command.SetAllAnimationScales
import com.ragaa.snapadb.core.adb.command.SetDarkMode
import com.ragaa.snapadb.core.adb.command.SetDontKeepActivities
import com.ragaa.snapadb.core.adb.command.SetFontScale
import com.ragaa.snapadb.core.adb.command.SetLocale
import com.ragaa.snapadb.core.adb.command.SetMobileDataEnabled
import com.ragaa.snapadb.core.adb.command.SetScreenDensity
import com.ragaa.snapadb.core.adb.command.SetScreenSize
import com.ragaa.snapadb.core.adb.command.SetWifiEnabled
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceControlsViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<DeviceControlsState>(DeviceControlsState.NoDevice)
    val state: StateFlow<DeviceControlsState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<DeviceControlsResult?>(null)
    val actionResult: StateFlow<DeviceControlsResult?> = _actionResult.asStateFlow()

    private var currentSerial: String? = null

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                currentSerial = device?.serial
                _actionResult.value = null
                if (device == null) {
                    _state.value = DeviceControlsState.NoDevice
                } else {
                    loadDisplayInfo(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: DeviceControlsIntent) {
        when (intent) {
            is DeviceControlsIntent.SendKey -> sendKey(intent.keyCode)
            is DeviceControlsIntent.SendText -> sendText(intent.text)
            is DeviceControlsIntent.SendTap -> sendTap(intent.x, intent.y)
            is DeviceControlsIntent.SendSwipe -> sendSwipe(intent.x1, intent.y1, intent.x2, intent.y2, intent.durationMs)
            is DeviceControlsIntent.ToggleWifi -> toggleWifi(intent.enabled)
            is DeviceControlsIntent.ToggleMobileData -> toggleMobileData(intent.enabled)
            is DeviceControlsIntent.ToggleAirplaneMode -> toggleAirplaneMode(intent.enabled)
            is DeviceControlsIntent.Reboot -> reboot(intent.mode)
            is DeviceControlsIntent.SetDensity -> setDensity(intent.dpi)
            is DeviceControlsIntent.ResetDensity -> resetDensity()
            is DeviceControlsIntent.SetScreenSize -> setScreenSize(intent.width, intent.height)
            is DeviceControlsIntent.ResetScreenSize -> resetScreenSize()
            is DeviceControlsIntent.GetSettingValue -> getSettingValue(intent.namespace, intent.key)
            is DeviceControlsIntent.PutSettingValue -> putSettingValue(intent.namespace, intent.key, intent.value)
            is DeviceControlsIntent.SetAnimationSpeed -> setAnimationSpeed(intent.scale)
            is DeviceControlsIntent.ToggleDarkMode -> toggleDarkMode(intent.enabled)
            is DeviceControlsIntent.ToggleDontKeepActivities -> toggleDontKeepActivities(intent.enabled)
            is DeviceControlsIntent.SetFontScale -> setFontScale(intent.scale)
            is DeviceControlsIntent.SetLocale -> setLocale(intent.locale)
            is DeviceControlsIntent.RefreshDisplay -> currentSerial?.let { loadDisplayInfo(it) }
            is DeviceControlsIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadDisplayInfo(serial: String) {
        viewModelScope.launch {
            _state.value = DeviceControlsState.Loading
            try {
                val density = async(dispatchers.io) { adbClient.execute(GetScreenDensity(), serial) }
                val size = async(dispatchers.io) { adbClient.execute(GetScreenSize(), serial) }
                val animScale = async(dispatchers.io) { adbClient.execute(GetAnimationScale(), serial) }
                val darkMode = async(dispatchers.io) { adbClient.execute(GetDarkMode(), serial) }
                val dontKeep = async(dispatchers.io) { adbClient.execute(GetDontKeepActivities(), serial) }
                val fontScale = async(dispatchers.io) { adbClient.execute(GetFontScale(), serial) }
                val locale = async(dispatchers.io) { adbClient.execute(GetLocale(), serial) }

                _state.value = DeviceControlsState.Ready(
                    currentDensity = density.await().getOrNull() ?: "Unknown",
                    currentSize = size.await().getOrNull() ?: "Unknown",
                    animationScale = animScale.await().getOrNull() ?: "1.0",
                    darkMode = darkMode.await().getOrNull(),
                    dontKeepActivities = dontKeep.await().getOrNull() ?: false,
                    fontScale = fontScale.await().getOrNull() ?: "1.0",
                    locale = locale.await().getOrNull() ?: "",
                )
            } catch (e: Exception) {
                _state.value = DeviceControlsState.Error(e.message ?: "Failed to load device info")
            }
        }
    }

    private fun executeAction(
        description: String,
        refreshDisplay: Boolean = false,
        block: suspend (serial: String) -> Result<String>,
    ) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) { block(serial) }
                .onSuccess {
                    _actionResult.value = DeviceControlsResult.Success(description)
                    if (refreshDisplay) loadDisplayInfo(serial)
                }
                .onFailure { e ->
                    _actionResult.value = DeviceControlsResult.Failure("$description failed: ${e.message}")
                }
        }
    }

    private fun sendKey(keyCode: Int) = executeAction("Key event $keyCode") { serial ->
        adbClient.execute(SendKeyEvent(keyCode), serial)
    }

    private fun sendText(text: String) = executeAction("Text input sent") { serial ->
        adbClient.execute(SendText(text), serial)
    }

    private fun sendTap(x: Int, y: Int) = executeAction("Tap at ($x, $y)") { serial ->
        adbClient.execute(SendTap(x, y), serial)
    }

    private fun sendSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) =
        executeAction("Swipe sent") { serial ->
            adbClient.execute(SendSwipe(x1, y1, x2, y2, durationMs), serial)
        }

    private fun toggleWifi(enabled: Boolean) =
        executeAction("WiFi ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(SetWifiEnabled(enabled), serial)
        }

    private fun toggleMobileData(enabled: Boolean) =
        executeAction("Mobile data ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(SetMobileDataEnabled(enabled), serial)
        }

    private fun toggleAirplaneMode(enabled: Boolean) =
        executeAction("Airplane mode ${if (enabled) "on" else "off"}") { serial ->
            adbClient.execute(SetAirplaneMode(enabled), serial)
        }

    private fun reboot(mode: String?) =
        executeAction("Rebooting${mode?.let { " ($it)" } ?: ""}") { serial ->
            adbClient.execute(RebootDevice(mode), serial)
        }

    private fun setDensity(dpi: Int) =
        executeAction("Density set to $dpi", refreshDisplay = true) { serial ->
            adbClient.execute(SetScreenDensity(dpi), serial)
        }

    private fun resetDensity() =
        executeAction("Density reset", refreshDisplay = true) { serial ->
            adbClient.execute(ResetScreenDensity(), serial)
        }

    private fun setScreenSize(width: Int, height: Int) =
        executeAction("Screen size set to ${width}x$height", refreshDisplay = true) { serial ->
            adbClient.execute(SetScreenSize(width, height), serial)
        }

    private fun resetScreenSize() =
        executeAction("Screen size reset", refreshDisplay = true) { serial ->
            adbClient.execute(ResetScreenSize(), serial)
        }

    private fun getSettingValue(namespace: String, key: String) {
        val serial = currentSerial ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) {
                adbClient.execute(GetSetting(namespace, key), serial)
            }
                .onSuccess { value ->
                    _actionResult.value = DeviceControlsResult.Success("$namespace/$key = $value")
                }
                .onFailure { e ->
                    _actionResult.value = DeviceControlsResult.Failure("Failed to get setting: ${e.message}")
                }
        }
    }

    private fun putSettingValue(namespace: String, key: String, value: String) =
        executeAction("Set $namespace/$key") { serial ->
            adbClient.execute(PutSetting(namespace, key, value), serial)
        }

    private fun setAnimationSpeed(scale: String) =
        executeAction("Animation speed set to ${scale}x", refreshDisplay = true) { serial ->
            adbClient.execute(SetAllAnimationScales(scale), serial)
        }

    private fun toggleDarkMode(enabled: Boolean) =
        executeAction("Dark mode ${if (enabled) "enabled" else "disabled"}", refreshDisplay = true) { serial ->
            adbClient.execute(SetDarkMode(enabled), serial)
        }

    private fun toggleDontKeepActivities(enabled: Boolean) =
        executeAction("Don't Keep Activities ${if (enabled) "enabled" else "disabled"}", refreshDisplay = true) { serial ->
            adbClient.execute(SetDontKeepActivities(enabled), serial)
        }

    private fun setFontScale(scale: String) =
        executeAction("Font scale set to ${scale}x", refreshDisplay = true) { serial ->
            adbClient.execute(SetFontScale(scale), serial)
        }

    private fun setLocale(locale: String) =
        executeAction("Locale set to $locale", refreshDisplay = true) { serial ->
            adbClient.execute(SetLocale(locale), serial)
        }
}

sealed class DeviceControlsState {
    data object NoDevice : DeviceControlsState()
    data object Loading : DeviceControlsState()
    data class Error(val message: String) : DeviceControlsState()
    data class Ready(
        val currentDensity: String,
        val currentSize: String,
        val animationScale: String = "1.0",
        val darkMode: Boolean? = null,
        val dontKeepActivities: Boolean = false,
        val fontScale: String = "1.0",
        val locale: String = "",
    ) : DeviceControlsState()
}

sealed class DeviceControlsIntent {
    data class SendKey(val keyCode: Int) : DeviceControlsIntent()
    data class SendText(val text: String) : DeviceControlsIntent()
    data class SendTap(val x: Int, val y: Int) : DeviceControlsIntent()
    data class SendSwipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Int = 300) : DeviceControlsIntent()
    data class ToggleWifi(val enabled: Boolean) : DeviceControlsIntent()
    data class ToggleMobileData(val enabled: Boolean) : DeviceControlsIntent()
    data class ToggleAirplaneMode(val enabled: Boolean) : DeviceControlsIntent()
    data class Reboot(val mode: String? = null) : DeviceControlsIntent()
    data class SetDensity(val dpi: Int) : DeviceControlsIntent()
    data object ResetDensity : DeviceControlsIntent()
    data class SetScreenSize(val width: Int, val height: Int) : DeviceControlsIntent()
    data object ResetScreenSize : DeviceControlsIntent()
    data class GetSettingValue(val namespace: String, val key: String) : DeviceControlsIntent()
    data class PutSettingValue(val namespace: String, val key: String, val value: String) : DeviceControlsIntent()
    data class SetAnimationSpeed(val scale: String) : DeviceControlsIntent()
    data class ToggleDarkMode(val enabled: Boolean) : DeviceControlsIntent()
    data class ToggleDontKeepActivities(val enabled: Boolean) : DeviceControlsIntent()
    data class SetFontScale(val scale: String) : DeviceControlsIntent()
    data class SetLocale(val locale: String) : DeviceControlsIntent()
    data object RefreshDisplay : DeviceControlsIntent()
    data object DismissResult : DeviceControlsIntent()
}

sealed class DeviceControlsResult {
    data class Success(val message: String) : DeviceControlsResult()
    data class Failure(val message: String) : DeviceControlsResult()
}
