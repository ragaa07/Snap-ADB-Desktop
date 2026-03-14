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
import com.ragaa.snapadb.core.navigation.NavigationHost
import com.ragaa.snapadb.core.navigation.Router
import com.ragaa.snapadb.core.theme.SnapAdbTheme
import com.ragaa.snapadb.core.theme.ThemeMode
import com.ragaa.snapadb.core.theme.ThemeRepository
import com.ragaa.snapadb.core.ui.DeviceInfo
import com.ragaa.snapadb.core.ui.MainShell
import com.ragaa.snapadb.feature.screenmirror.MirrorPanel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun App() {
    val router = koinInject<Router>()
    val deviceMonitor = koinInject<AdbDeviceMonitor>()
    val themeRepo = koinInject<ThemeRepository>()
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var isMirrorPanelOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        themeMode = themeRepo.getThemeMode()
    }

    DisposableEffect(Unit) {
        deviceMonitor.start()
        onDispose { deviceMonitor.stop() }
    }

    val selectedDevice by deviceMonitor.selectedDevice.collectAsState()
    val allDevices by deviceMonitor.devices.collectAsState()

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

    SnapAdbTheme(themeMode = themeMode) {
        MainShell(
            router = router,
            themeMode = themeMode,
            onToggleTheme = {
                themeMode = when (themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.SYSTEM
                }
                scope.launch { themeRepo.setThemeMode(themeMode) }
            },
            isMirrorPanelOpen = isMirrorPanelOpen,
            onToggleMirrorPanel = { isMirrorPanelOpen = !isMirrorPanelOpen },
            devices = devices,
            selectedDevice = currentDevice,
            onSelectDevice = { deviceInfo ->
                val adbDevice = allDevices.find { it.serial == deviceInfo.serial }
                adbDevice?.let { deviceMonitor.selectDevice(it) }
            },
            mirrorPanelContent = { MirrorPanel() },
        ) {
            NavigationHost(router = router)
        }
    }
}
