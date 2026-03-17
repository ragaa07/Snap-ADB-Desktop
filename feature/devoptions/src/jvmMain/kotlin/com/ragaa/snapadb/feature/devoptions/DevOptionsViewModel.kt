package com.ragaa.snapadb.feature.devoptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ragaa.snapadb.common.DispatcherProvider
import com.ragaa.snapadb.core.adb.AdbClient
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.command.GetBackgroundProcessLimit
import com.ragaa.snapadb.core.adb.command.GetDebugProp
import com.ragaa.snapadb.core.adb.command.GetIndividualAnimationScale
import com.ragaa.snapadb.core.adb.command.GetSetting
import com.ragaa.snapadb.core.adb.command.PutSetting
import com.ragaa.snapadb.core.adb.command.SetBackgroundProcessLimit
import com.ragaa.snapadb.core.adb.command.SetDebugProp
import com.ragaa.snapadb.core.adb.command.SetIndividualAnimationScale
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsIntent
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsResult
import com.ragaa.snapadb.feature.devoptions.model.DevOptionsState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DevOptionsViewModel(
    private val adbClient: AdbClient,
    private val deviceMonitor: AdbDeviceMonitor,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<DevOptionsState>(DevOptionsState.NoDevice)
    val state: StateFlow<DevOptionsState> = _state.asStateFlow()

    private val _actionResult = MutableStateFlow<DevOptionsResult?>(null)
    val actionResult: StateFlow<DevOptionsResult?> = _actionResult.asStateFlow()

    private val currentSerial = AtomicReference<String?>(null)

    init {
        viewModelScope.launch {
            deviceMonitor.selectedDevice.collectLatest { device ->
                currentSerial.set(device?.serial)
                _actionResult.value = null
                if (device == null) {
                    _state.value = DevOptionsState.NoDevice
                } else {
                    loadOptions(device.serial)
                }
            }
        }
    }

    fun onIntent(intent: DevOptionsIntent) {
        when (intent) {
            is DevOptionsIntent.ToggleLayoutBounds -> toggleLayoutBounds(intent.enabled)
            is DevOptionsIntent.ToggleGpuOverdraw -> toggleGpuOverdraw(intent.enabled)
            is DevOptionsIntent.ToggleShowTouches -> toggleShowTouches(intent.enabled)
            is DevOptionsIntent.TogglePointerLocation -> togglePointerLocation(intent.enabled)
            is DevOptionsIntent.ToggleStrictMode -> toggleStrictMode(intent.enabled)
            is DevOptionsIntent.SetProfileGpuRendering -> setProfileGpuRendering(intent.mode)
            is DevOptionsIntent.ToggleForce4xMsaa -> toggleForce4xMsaa(intent.enabled)
            is DevOptionsIntent.SetBackgroundProcessLimit -> setBackgroundProcessLimit(intent.limit)
            is DevOptionsIntent.SetWindowAnimationScale -> setAnimationScale(
                GetIndividualAnimationScale.WINDOW, intent.scale, "Window animation",
            ) { copy(windowAnimationScale = intent.scale) }
            is DevOptionsIntent.SetTransitionAnimationScale -> setAnimationScale(
                GetIndividualAnimationScale.TRANSITION, intent.scale, "Transition animation",
            ) { copy(transitionAnimationScale = intent.scale) }
            is DevOptionsIntent.SetAnimatorDurationScale -> setAnimationScale(
                GetIndividualAnimationScale.ANIMATOR, intent.scale, "Animator duration",
            ) { copy(animatorDurationScale = intent.scale) }
            is DevOptionsIntent.SetStayAwake -> setStayAwake(intent.mode)
            is DevOptionsIntent.ToggleForceRtl -> toggleForceRtl(intent.enabled)
            is DevOptionsIntent.SetSimulateColorSpace -> setSimulateColorSpace(intent.mode)
            is DevOptionsIntent.SetPeakRefreshRate -> setRefreshRate("peak_refresh_rate", intent.rate, "Peak refresh rate")
            is DevOptionsIntent.SetMinRefreshRate -> setRefreshRate("min_refresh_rate", intent.rate, "Min refresh rate")
            is DevOptionsIntent.ToggleForceResizable -> toggleForceResizable(intent.enabled)
            is DevOptionsIntent.ToggleFreeformWindows -> toggleFreeformWindows(intent.enabled)
            is DevOptionsIntent.ToggleWifiVerboseLogging -> toggleWifiVerboseLogging(intent.enabled)
            is DevOptionsIntent.Refresh -> currentSerial.get()?.let { loadOptions(it) }
            is DevOptionsIntent.DismissResult -> _actionResult.value = null
        }
    }

    private fun loadOptions(serial: String) {
        viewModelScope.launch {
            _state.value = DevOptionsState.Loading
            try {
                // Debug Overlays
                val showTouches = async(dispatchers.io) {
                    adbClient.execute(GetSetting("system", "show_touches"), serial)
                }
                val pointerLocation = async(dispatchers.io) {
                    adbClient.execute(GetSetting("system", "pointer_location"), serial)
                }
                val strictMode = async(dispatchers.io) {
                    adbClient.execute(GetSetting("system", "strict_mode_visual_indicator"), serial)
                }
                val layoutBounds = async(dispatchers.io) {
                    adbClient.execute(GetDebugProp("debug.layout"), serial)
                }
                val gpuOverdraw = async(dispatchers.io) {
                    adbClient.execute(GetDebugProp("debug.hwui.overdraw"), serial)
                }
                // Rendering
                val profileGpu = async(dispatchers.io) {
                    adbClient.execute(GetDebugProp("debug.hwui.profile"), serial)
                }
                val force4xMsaa = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "force_msaa"), serial)
                }
                // Process Management
                val bgProcessLimit = async(dispatchers.io) {
                    adbClient.execute(GetBackgroundProcessLimit(), serial)
                }
                // Animation Scales
                val windowAnim = async(dispatchers.io) {
                    adbClient.execute(GetIndividualAnimationScale(GetIndividualAnimationScale.WINDOW), serial)
                }
                val transitionAnim = async(dispatchers.io) {
                    adbClient.execute(GetIndividualAnimationScale(GetIndividualAnimationScale.TRANSITION), serial)
                }
                val animatorAnim = async(dispatchers.io) {
                    adbClient.execute(GetIndividualAnimationScale(GetIndividualAnimationScale.ANIMATOR), serial)
                }
                // Display
                val stayAwake = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "stay_on_while_plugged_in"), serial)
                }
                val forceRtl = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "debug.force_rtl"), serial)
                }
                val daltonizer = async(dispatchers.io) {
                    adbClient.execute(GetSetting("secure", "accessibility_display_daltonizer"), serial)
                }
                val daltonizerEnabled = async(dispatchers.io) {
                    adbClient.execute(GetSetting("secure", "accessibility_display_daltonizer_enabled"), serial)
                }
                val peakRefresh = async(dispatchers.io) {
                    adbClient.execute(GetSetting("system", "peak_refresh_rate"), serial)
                }
                val minRefresh = async(dispatchers.io) {
                    adbClient.execute(GetSetting("system", "min_refresh_rate"), serial)
                }
                // Multi-window
                val forceResizable = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "force_resizable_activities"), serial)
                }
                val freeform = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "enable_freeform_support"), serial)
                }
                // Network
                val wifiVerbose = async(dispatchers.io) {
                    adbClient.execute(GetSetting("global", "wifi_verbose_logging_enabled"), serial)
                }

                val daltonizerEnabledVal = daltonizerEnabled.await().getOrNull() == "1"
                val daltonizerVal = daltonizer.await().getOrNull()?.toIntOrNull() ?: -1
                val peakRefreshVal = peakRefresh.await().getOrNull()?.let {
                    if (it == "null" || it.isBlank()) "" else it
                } ?: ""
                val minRefreshVal = minRefresh.await().getOrNull()?.let {
                    if (it == "null" || it.isBlank()) "" else it
                } ?: ""

                _state.value = DevOptionsState.Ready(
                    showLayoutBounds = layoutBounds.await().getOrNull()?.equals("true", ignoreCase = true) ?: false,
                    gpuOverdraw = gpuOverdraw.await().getOrNull()?.equals("show", ignoreCase = true) ?: false,
                    showTouches = showTouches.await().getOrNull() == "1",
                    pointerLocation = pointerLocation.await().getOrNull() == "1",
                    strictModeVisual = strictMode.await().getOrNull() == "1",
                    profileGpuRendering = profileGpu.await().getOrNull()?.ifBlank { "false" } ?: "false",
                    force4xMsaa = force4xMsaa.await().getOrNull() == "1",
                    backgroundProcessLimit = bgProcessLimit.await().getOrNull() ?: -1,
                    windowAnimationScale = windowAnim.await().getOrNull() ?: "1.0",
                    transitionAnimationScale = transitionAnim.await().getOrNull() ?: "1.0",
                    animatorDurationScale = animatorAnim.await().getOrNull() ?: "1.0",
                    stayAwake = stayAwake.await().getOrNull()?.toIntOrNull() ?: 0,
                    forceRtl = forceRtl.await().getOrNull() == "1",
                    simulateColorSpace = if (daltonizerEnabledVal) daltonizerVal else -1,
                    peakRefreshRate = peakRefreshVal,
                    minRefreshRate = minRefreshVal,
                    forceResizableActivities = forceResizable.await().getOrNull() == "1",
                    freeformWindows = freeform.await().getOrNull() == "1",
                    wifiVerboseLogging = wifiVerbose.await().getOrNull() == "1",
                )
            } catch (e: Exception) {
                _state.value = DevOptionsState.Error(e.message ?: "Failed to load developer options")
            }
        }
    }

    private fun executeAction(
        description: String,
        refreshDisplay: Boolean = false,
        block: suspend (serial: String) -> Result<String>,
    ) {
        val serial = currentSerial.get() ?: return
        viewModelScope.launch {
            withContext(dispatchers.io) { block(serial) }
                .onSuccess {
                    _actionResult.value = DevOptionsResult.Success(description)
                    if (refreshDisplay) loadOptions(serial)
                }
                .onFailure { e ->
                    _actionResult.value = DevOptionsResult.Failure("$description failed: ${e.message}")
                }
        }
    }

    private fun updateReadyState(transform: DevOptionsState.Ready.() -> DevOptionsState.Ready) {
        val current = _state.value as? DevOptionsState.Ready ?: return
        _state.value = current.transform()
    }

    // --- Debug Overlays ---

    private fun toggleLayoutBounds(enabled: Boolean) {
        updateReadyState { copy(showLayoutBounds = enabled) }
        executeAction("Layout bounds ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(SetDebugProp("debug.layout", if (enabled) "true" else "false"), serial)
        }
    }

    private fun toggleGpuOverdraw(enabled: Boolean) {
        updateReadyState { copy(gpuOverdraw = enabled) }
        executeAction("GPU overdraw ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(SetDebugProp("debug.hwui.overdraw", if (enabled) "show" else "false"), serial)
        }
    }

    private fun toggleShowTouches(enabled: Boolean) {
        updateReadyState { copy(showTouches = enabled) }
        executeAction("Show touches ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("system", "show_touches", if (enabled) "1" else "0"), serial)
        }
    }

    private fun togglePointerLocation(enabled: Boolean) {
        updateReadyState { copy(pointerLocation = enabled) }
        executeAction("Pointer location ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("system", "pointer_location", if (enabled) "1" else "0"), serial)
        }
    }

    private fun toggleStrictMode(enabled: Boolean) {
        updateReadyState { copy(strictModeVisual = enabled) }
        executeAction("Strict mode ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("system", "strict_mode_visual_indicator", if (enabled) "1" else "0"), serial)
        }
    }

    // --- Rendering ---

    private fun setProfileGpuRendering(mode: String) {
        updateReadyState { copy(profileGpuRendering = mode) }
        val label = when (mode) {
            "false" -> "Off"
            "visual_bars" -> "Bars"
            else -> mode
        }
        executeAction("Profile GPU rendering: $label") { serial ->
            adbClient.execute(SetDebugProp("debug.hwui.profile", mode), serial)
        }
    }

    private fun toggleForce4xMsaa(enabled: Boolean) {
        updateReadyState { copy(force4xMsaa = enabled) }
        executeAction("Force 4x MSAA ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("global", "force_msaa", if (enabled) "1" else "0"), serial)
        }
    }

    // --- Process Management ---

    private fun setBackgroundProcessLimit(limit: Int) {
        val label = if (limit == -1) "Standard" else "At most $limit"
        updateReadyState { copy(backgroundProcessLimit = limit) }
        executeAction("Background process limit: $label") { serial ->
            adbClient.execute(SetBackgroundProcessLimit(limit), serial)
        }
    }

    // --- Animation Scales ---

    private fun setAnimationScale(
        type: String,
        scale: String,
        label: String,
        stateUpdate: DevOptionsState.Ready.() -> DevOptionsState.Ready,
    ) {
        updateReadyState(stateUpdate)
        executeAction("$label scale set to ${scale}x") { serial ->
            adbClient.execute(SetIndividualAnimationScale(type, scale), serial)
        }
    }

    // --- Display ---

    private fun setStayAwake(mode: Int) {
        updateReadyState { copy(stayAwake = mode) }
        val label = when (mode) {
            0 -> "Off"
            1 -> "USB"
            2 -> "AC"
            3 -> "USB + AC"
            4 -> "Wireless"
            7 -> "All"
            else -> mode.toString()
        }
        executeAction("Stay awake: $label") { serial ->
            adbClient.execute(PutSetting("global", "stay_on_while_plugged_in", mode.toString()), serial)
        }
    }

    private fun toggleForceRtl(enabled: Boolean) {
        updateReadyState { copy(forceRtl = enabled) }
        executeAction("Force RTL ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("global", "debug.force_rtl", if (enabled) "1" else "0"), serial)
        }
    }

    private fun setSimulateColorSpace(mode: Int) {
        updateReadyState { copy(simulateColorSpace = mode) }
        val label = when (mode) {
            -1 -> "Disabled"
            0 -> "Monochromacy"
            11 -> "Deuteranomaly"
            12 -> "Protanomaly"
            13 -> "Tritanomaly"
            else -> "Unknown"
        }
        if (mode == -1) {
            executeAction("Color simulation: $label") { serial ->
                adbClient.execute(PutSetting("secure", "accessibility_display_daltonizer_enabled", "0"), serial)
            }
        } else {
            executeAction("Color simulation: $label") { serial ->
                adbClient.execute(PutSetting("secure", "accessibility_display_daltonizer_enabled", "1"), serial)
                adbClient.execute(PutSetting("secure", "accessibility_display_daltonizer", mode.toString()), serial)
            }
        }
    }

    private fun setRefreshRate(key: String, rate: String, label: String) {
        if (key == "peak_refresh_rate") {
            updateReadyState { copy(peakRefreshRate = rate) }
        } else {
            updateReadyState { copy(minRefreshRate = rate) }
        }
        executeAction("$label set to $rate Hz") { serial ->
            adbClient.execute(PutSetting("system", key, rate), serial)
        }
    }

    // --- Multi-window ---

    private fun toggleForceResizable(enabled: Boolean) {
        updateReadyState { copy(forceResizableActivities = enabled) }
        executeAction("Force resizable ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("global", "force_resizable_activities", if (enabled) "1" else "0"), serial)
        }
    }

    private fun toggleFreeformWindows(enabled: Boolean) {
        updateReadyState { copy(freeformWindows = enabled) }
        executeAction("Freeform windows ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("global", "enable_freeform_support", if (enabled) "1" else "0"), serial)
        }
    }

    // --- Network ---

    private fun toggleWifiVerboseLogging(enabled: Boolean) {
        updateReadyState { copy(wifiVerboseLogging = enabled) }
        executeAction("WiFi verbose logging ${if (enabled) "enabled" else "disabled"}") { serial ->
            adbClient.execute(PutSetting("global", "wifi_verbose_logging_enabled", if (enabled) "1" else "0"), serial)
        }
    }
}
