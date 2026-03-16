package com.ragaa.snapadb.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.navigation.Route
import com.ragaa.snapadb.core.navigation.Router
import com.ragaa.snapadb.core.theme.SnapMint
import com.ragaa.snapadb.core.theme.ThemeMode
import com.ragaa.snapadb.core.ui.sidebar.NavItem
import com.ragaa.snapadb.core.ui.sidebar.RightToolBar
import com.ragaa.snapadb.core.ui.sidebar.Sidebar
import kotlinx.coroutines.delay

data class DeviceInfo(
    val serial: String,
    val model: String,
    val state: String,
)

@Composable
fun MainShell(
    router: Router,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    isMirrorPanelOpen: Boolean,
    onToggleMirrorPanel: () -> Unit,
    devices: List<DeviceInfo>,
    selectedDevice: DeviceInfo?,
    onSelectDevice: (DeviceInfo) -> Unit,
    pinnedItems: List<NavItem>,
    overflowItems: List<NavItem>,
    onPinRoute: (Route) -> Unit,
    onUnpinRoute: (Route) -> Unit,
    globalMessage: String? = null,
    onDismissGlobalMessage: () -> Unit = {},
    mirrorPanelContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentRoute by router.currentRoute.collectAsState()

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) { Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopBar(
            selectedDevice = selectedDevice,
            devices = devices,
            onSelectDevice = onSelectDevice,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
        )

        // Main content row
        Row(modifier = Modifier.weight(1f)) {
            // Left sidebar
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Sidebar(
                currentRoute = currentRoute,
                onRouteSelected = { router.navigateTo(it) },
                pinnedItems = pinnedItems,
                overflowItems = overflowItems,
                onPinRoute = onPinRoute,
                onUnpinRoute = onUnpinRoute,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Main content area
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }

            // Right mirror panel (collapsible)
            AnimatedVisibility(
                visible = isMirrorPanelOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Surface(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        mirrorPanelContent()
                    }
                }
            }

            // Right tool bar
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            RightToolBar(
                isMirrorOpen = isMirrorPanelOpen,
                onToggleMirror = onToggleMirrorPanel,
            )
        }

        // Status bar
        StatusBar(selectedDevice = selectedDevice, deviceCount = devices.size)
        }

        // Global snackbar
        globalMessage?.let { message ->
            LaunchedEffect(message) {
                delay(4000)
                onDismissGlobalMessage()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onDismissGlobalMessage) {
                        Text("Dismiss")
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(message)
            }
        }
    } }
}

@Composable
private fun TopBar(
    selectedDevice: DeviceInfo?,
    devices: List<DeviceInfo>,
    onSelectDevice: (DeviceInfo) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surface,
    ) { Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App logo & name
        Icon(
            Icons.Outlined.FlashOn,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "SnapADB",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Device selector
        DeviceSelector(
            selectedDevice = selectedDevice,
            devices = devices,
            onSelectDevice = onSelectDevice,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Theme toggle
        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "Toggle theme",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } }
}

@Composable
private fun DeviceSelector(
    selectedDevice: DeviceInfo?,
    devices: List<DeviceInfo>,
    onSelectDevice: (DeviceInfo) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedDevice?.state == "device") SnapMint
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )

            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = selectedDevice?.let {
                    it.model.ifEmpty { it.serial }
                } ?: "No device",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "Select device",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No devices connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { expanded = false },
                    enabled = false,
                )
            }
            devices.forEach { device ->
                val isSelected = selectedDevice?.serial == device.serial
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (device.state == "device") SnapMint
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    ),
                            )
                            Text(
                                text = device.model.ifEmpty { device.serial },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    },
                    onClick = {
                        onSelectDevice(device)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    selectedDevice: DeviceInfo?,
    deviceCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Connection status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedDevice != null) SnapMint
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    ),
            )
            Text(
                text = if (selectedDevice != null) "Connected" else "No device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedDevice != null) {
            StatusDivider()
            Text(
                text = selectedDevice.model.ifEmpty { selectedDevice.serial },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (deviceCount > 1) {
            StatusDivider()
            Text(
                text = "$deviceCount devices",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } }
}

@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(12.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
