package com.ragaa.snapadb.core.ui.sidebar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.ragaa.snapadb.core.navigation.Route

data class NavItem(val route: Route, val icon: ImageVector)

val allNavItems: List<NavItem> = listOf(
    NavItem(Route.Dashboard, Icons.Outlined.Dashboard),
    NavItem(Route.MultiDevice, Icons.Outlined.Devices),
    NavItem(Route.AppManager, Icons.Outlined.Apps),
    NavItem(Route.FileExplorer, Icons.Outlined.Folder),
    NavItem(Route.AppData, Icons.Outlined.Storage),
    NavItem(Route.DbInspector, Icons.Outlined.TableChart),
    NavItem(Route.Shell, Icons.Outlined.Terminal),
    NavItem(Route.Logcat, Icons.Outlined.BugReport),
    NavItem(Route.ScreenMirror, Icons.AutoMirrored.Outlined.ScreenShare),
    NavItem(Route.Performance, Icons.Outlined.Speed),
    NavItem(Route.DeviceControls, Icons.Outlined.SettingsRemote),
    NavItem(Route.Network, Icons.Outlined.Lan),
    NavItem(Route.DeepLink, Icons.Outlined.Link),
    NavItem(Route.Notification, Icons.Outlined.Notifications),
    NavItem(Route.BugReporter, Icons.Outlined.Summarize),
    NavItem(Route.Monkey, Icons.Outlined.Pets),
    NavItem(Route.DevOptions, Icons.Outlined.DeveloperMode),
)

val defaultPinnedRoutes: Set<String> = setOf(
    "Dashboard",
    "Devices",
    "Apps",
    "Files",
    "App Data",
    "DB Inspector",
    "Shell",
    "Logcat",
    "Mirror",
    "Controls",
)
