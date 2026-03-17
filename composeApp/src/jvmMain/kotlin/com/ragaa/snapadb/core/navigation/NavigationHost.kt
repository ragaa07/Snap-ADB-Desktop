package com.ragaa.snapadb.core.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ragaa.snapadb.feature.appmanager.AppManagerScreen
import com.ragaa.snapadb.feature.dashboard.DashboardScreen
import com.ragaa.snapadb.feature.deeplink.DeepLinkScreen
import com.ragaa.snapadb.feature.devicecontrols.DeviceControlsScreen
import com.ragaa.snapadb.feature.fileexplorer.FileExplorerScreen
import com.ragaa.snapadb.feature.multidevice.MultiDeviceScreen
import com.ragaa.snapadb.feature.notification.NotificationScreen
import com.ragaa.snapadb.feature.performance.PerformanceScreen
import com.ragaa.snapadb.feature.screenmirror.ScreenMirrorScreen
import com.ragaa.snapadb.feature.appdata.AppDataScreen
import com.ragaa.snapadb.feature.bugreporter.BugReporterScreen
import com.ragaa.snapadb.feature.dbinspector.DbInspectorScreen
import com.ragaa.snapadb.feature.devoptions.DevOptionsScreen
import com.ragaa.snapadb.feature.monkey.MonkeyScreen
import com.ragaa.snapadb.feature.network.NetworkScreen
import com.ragaa.snapadb.feature.shell.LogcatOnlyScreen
import com.ragaa.snapadb.feature.shell.TerminalOnlyScreen

@Composable
fun NavigationHost(router: Router, modifier: Modifier = Modifier) {
    val currentRoute by router.currentRoute.collectAsState()

    AnimatedContent(
        targetState = currentRoute,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "NavigationHost",
    ) { route ->
        when (route) {
            is Route.Dashboard -> DashboardScreen()
            is Route.MultiDevice -> MultiDeviceScreen()
            is Route.AppManager -> AppManagerScreen()
            is Route.FileExplorer -> FileExplorerScreen()
            is Route.Shell -> TerminalOnlyScreen()
            is Route.Logcat -> LogcatOnlyScreen()
            is Route.ScreenMirror -> ScreenMirrorScreen()
            is Route.DeviceControls -> DeviceControlsScreen()
            is Route.Performance -> PerformanceScreen()
            is Route.DeepLink -> DeepLinkScreen()
            is Route.Notification -> NotificationScreen()
            is Route.AppData -> AppDataScreen()
            is Route.BugReporter -> BugReporterScreen()
            is Route.DbInspector -> DbInspectorScreen()
            is Route.Network -> NetworkScreen()
            is Route.Monkey -> MonkeyScreen()
            is Route.DevOptions -> DevOptionsScreen()
        }
    }
}
