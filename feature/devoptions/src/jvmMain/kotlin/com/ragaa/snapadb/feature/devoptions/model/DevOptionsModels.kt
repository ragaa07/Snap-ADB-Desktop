package com.ragaa.snapadb.feature.devoptions.model

sealed class DevOptionsState {
    data object NoDevice : DevOptionsState()
    data object Loading : DevOptionsState()
    data class Error(val message: String) : DevOptionsState()
    data class Ready(
        // Debug Overlays
        val showLayoutBounds: Boolean = false,
        val gpuOverdraw: Boolean = false,
        val showTouches: Boolean = false,
        val pointerLocation: Boolean = false,
        val strictModeVisual: Boolean = false,
        // Rendering
        val profileGpuRendering: String = "false",
        val force4xMsaa: Boolean = false,
        // Process Management
        val backgroundProcessLimit: Int = -1,
        // Animation Scales
        val windowAnimationScale: String = "1.0",
        val transitionAnimationScale: String = "1.0",
        val animatorDurationScale: String = "1.0",
        // Display
        val stayAwake: Int = 0,
        val forceRtl: Boolean = false,
        val simulateColorSpace: Int = -1,
        val peakRefreshRate: String = "",
        val minRefreshRate: String = "",
        // Multi-window
        val forceResizableActivities: Boolean = false,
        val freeformWindows: Boolean = false,
        // Network
        val wifiVerboseLogging: Boolean = false,
    ) : DevOptionsState()
}

sealed class DevOptionsIntent {
    // Debug Overlays
    data class ToggleLayoutBounds(val enabled: Boolean) : DevOptionsIntent()
    data class ToggleGpuOverdraw(val enabled: Boolean) : DevOptionsIntent()
    data class ToggleShowTouches(val enabled: Boolean) : DevOptionsIntent()
    data class TogglePointerLocation(val enabled: Boolean) : DevOptionsIntent()
    data class ToggleStrictMode(val enabled: Boolean) : DevOptionsIntent()
    // Rendering
    data class SetProfileGpuRendering(val mode: String) : DevOptionsIntent()
    data class ToggleForce4xMsaa(val enabled: Boolean) : DevOptionsIntent()
    // Process Management
    data class SetBackgroundProcessLimit(val limit: Int) : DevOptionsIntent()
    // Animation Scales
    data class SetWindowAnimationScale(val scale: String) : DevOptionsIntent()
    data class SetTransitionAnimationScale(val scale: String) : DevOptionsIntent()
    data class SetAnimatorDurationScale(val scale: String) : DevOptionsIntent()
    // Display
    data class SetStayAwake(val mode: Int) : DevOptionsIntent()
    data class ToggleForceRtl(val enabled: Boolean) : DevOptionsIntent()
    data class SetSimulateColorSpace(val mode: Int) : DevOptionsIntent()
    data class SetPeakRefreshRate(val rate: String) : DevOptionsIntent()
    data class SetMinRefreshRate(val rate: String) : DevOptionsIntent()
    // Multi-window
    data class ToggleForceResizable(val enabled: Boolean) : DevOptionsIntent()
    data class ToggleFreeformWindows(val enabled: Boolean) : DevOptionsIntent()
    // Network
    data class ToggleWifiVerboseLogging(val enabled: Boolean) : DevOptionsIntent()
    // General
    data object Refresh : DevOptionsIntent()
    data object DismissResult : DevOptionsIntent()
}

sealed class DevOptionsResult {
    data class Success(val message: String) : DevOptionsResult()
    data class Failure(val message: String) : DevOptionsResult()
}
