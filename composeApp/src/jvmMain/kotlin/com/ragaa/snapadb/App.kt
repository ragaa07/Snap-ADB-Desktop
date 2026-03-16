package com.ragaa.snapadb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ragaa.snapadb.core.adb.AdbDeviceMonitor
import com.ragaa.snapadb.core.adb.AdbPath
import com.ragaa.snapadb.core.navigation.NavigationHost
import com.ragaa.snapadb.core.navigation.Route
import com.ragaa.snapadb.core.navigation.Router
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import com.ragaa.snapadb.core.theme.ThemeMode
import com.ragaa.snapadb.core.theme.ThemeRepository
import com.ragaa.snapadb.core.ui.DeviceInfo
import com.ragaa.snapadb.core.ui.MainShell
import com.ragaa.snapadb.core.ui.components.AdbSetupScreen
import com.ragaa.snapadb.feature.appmanager.LabelResolutionService
import com.ragaa.snapadb.core.sidebar.SidebarViewModel
import com.ragaa.snapadb.feature.screenmirror.MirrorPanel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    onNavigate: ((Route) -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    onToggleMirrorPanel: (() -> Unit)? = null,
) {
    val router = koinInject<Router>()
    val deviceMonitor = koinInject<AdbDeviceMonitor>()
    val themeRepo = koinInject<ThemeRepository>()
    val adbPath = koinInject<AdbPath>()
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var isMirrorPanelOpen by remember { mutableStateOf(false) }
    var adbAvailable by remember { mutableStateOf<Boolean?>(null) }
    var previousDeviceSerial by remember { mutableStateOf<String?>(null) }
    var deviceDisconnectMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        themeMode = themeRepo.getThemeMode()
        adbAvailable = adbPath.resolve() != null
    }

    // Store callbacks for keyboard shortcuts
    val navigateAction: (Route) -> Unit = { route ->
        router.navigateTo(route)
        onNavigate?.invoke(route)
    }
    val toggleThemeAction: () -> Unit = {
        themeMode = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        scope.launch { themeRepo.setThemeMode(themeMode) }
        onToggleTheme?.invoke()
    }
    val toggleMirrorAction: () -> Unit = {
        isMirrorPanelOpen = !isMirrorPanelOpen
        onToggleMirrorPanel?.invoke()
    }

    SnapAdbTheme(themeMode = themeMode) {
        when (adbAvailable) {
            null -> {} // Initial check in progress
            false -> AdbSetupScreen(onRetry = { adbAvailable = adbPath.resolve() != null })
            true -> {
                val labelResolutionService = koinInject<LabelResolutionService>()
                DisposableEffect(Unit) {
                    deviceMonitor.start()
                    labelResolutionService.start()
                    onDispose {
                        labelResolutionService.stop()
                        deviceMonitor.stop()
                    }
                }

                val selectedDevice by deviceMonitor.selectedDevice.collectAsState()
                val allDevices by deviceMonitor.devices.collectAsState()
                val monitorError by deviceMonitor.error.collectAsState()

                // Detect device disconnection
                LaunchedEffect(selectedDevice) {
                    val currentSerial = selectedDevice?.serial
                    if (previousDeviceSerial != null && currentSerial == null) {
                        deviceDisconnectMessage = "Device disconnected"
                    }
                    previousDeviceSerial = currentSerial
                }

                // Show monitor errors as disconnect messages
                LaunchedEffect(monitorError) {
                    monitorError?.let {
                        deviceDisconnectMessage = "ADB error: ${it.message ?: "Unknown error"}"
                    }
                }

                val devices = allDevices.map { device ->
                    DeviceInfo(
                        serial = device.serial,
                        model = device.model,
                        state = device.state.name.lowercase(),
                    )
                }

                val currentDevice = selectedDevice?.let { device ->
                    DeviceInfo(
                        serial = device.serial,
                        model = device.model,
                        state = device.state.name.lowercase(),
                    )
                }

                val sidebarViewModel: SidebarViewModel = koinViewModel()

                MainShell(
                    router = router,
                    themeMode = themeMode,
                    onToggleTheme = toggleThemeAction,
                    isMirrorPanelOpen = isMirrorPanelOpen,
                    onToggleMirrorPanel = toggleMirrorAction,
                    devices = devices,
                    selectedDevice = currentDevice,
                    onSelectDevice = { deviceInfo ->
                        val adbDevice = allDevices.find { it.serial == deviceInfo.serial }
                        adbDevice?.let { deviceMonitor.selectDevice(it) }
                    },
                    pinnedItems = sidebarViewModel.pinnedItems,
                    overflowItems = sidebarViewModel.overflowItems,
                    onPinRoute = sidebarViewModel::pinRoute,
                    onUnpinRoute = sidebarViewModel::unpinRoute,
                    globalMessage = deviceDisconnectMessage,
                    onDismissGlobalMessage = { deviceDisconnectMessage = null },
                    mirrorPanelContent = { MirrorPanel() },
                ) {
                    NavigationHost(router = router)
                }
            }
        }
    }
}
