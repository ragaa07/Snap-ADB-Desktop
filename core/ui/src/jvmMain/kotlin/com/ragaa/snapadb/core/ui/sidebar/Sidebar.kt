package com.ragaa.snapadb.core.ui.sidebar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ragaa.snapadb.core.navigation.Route

private fun Route.icon(): ImageVector = when (this) {
    Route.Dashboard -> Icons.Outlined.Dashboard
    Route.MultiDevice -> Icons.Outlined.Devices
    Route.AppManager -> Icons.Outlined.Apps
    Route.FileExplorer -> Icons.Outlined.Folder
    Route.Shell -> Icons.Outlined.Terminal
    Route.ScreenMirror -> Icons.AutoMirrored.Outlined.ScreenShare
    Route.DeviceControls -> Icons.Outlined.SettingsRemote
    Route.Performance -> Icons.Outlined.Speed
    Route.DeepLink -> Icons.Outlined.Link
    Route.Notification -> Icons.Outlined.Notifications
}

private val allRoutes: List<Route> = listOf(
    Route.Dashboard,
    Route.MultiDevice,
    Route.AppManager,
    Route.FileExplorer,
    Route.Shell,
    Route.ScreenMirror,
    Route.DeviceControls,
    Route.Performance,
    Route.DeepLink,
    Route.Notification,
)

@Composable
fun Sidebar(
    currentRoute: Route,
    onRouteSelected: (Route) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    NavigationRail {
        allRoutes.forEach { route ->
            NavigationRailItem(
                selected = currentRoute == route,
                onClick = { onRouteSelected(route) },
                icon = { Icon(route.icon(), contentDescription = route.title) },
                label = { Text(route.title) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
            )
        }
    }
}
