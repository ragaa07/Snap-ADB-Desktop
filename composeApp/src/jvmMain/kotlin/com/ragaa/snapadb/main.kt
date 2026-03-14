package com.ragaa.snapadb

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ragaa.snapadb.core.di.appModules
import com.ragaa.snapadb.core.navigation.Route
import com.ragaa.snapadb.core.navigation.Router
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val shortcutRoutes = listOf(
    Route.Dashboard,     // Cmd+1
    Route.MultiDevice,   // Cmd+2
    Route.AppManager,    // Cmd+3
    Route.FileExplorer,  // Cmd+4
    Route.Shell,         // Cmd+5
    Route.Logcat,        // Cmd+6
    Route.ScreenMirror,  // Cmd+7
    Route.Performance,   // Cmd+8
    Route.DeviceControls,// Cmd+9
)

fun main() {
    startKoin { modules(appModules) }

    val windowState = WindowStateManager.loadWindowState()

    application {
        // Save window state on changes
        LaunchedEffect(Unit) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .collect { WindowStateManager.saveWindowState(windowState) }
        }

        val router = getKoin().get<Router>()

        Window(
            onCloseRequest = {
                WindowStateManager.saveWindowState(windowState)
                exitApplication()
            },
            title = "SnapADB",
            state = windowState,
            onKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                if (!event.isMetaPressed) return@Window false

                when (event.key) {
                    Key.One -> { router.navigateTo(shortcutRoutes[0]); true }
                    Key.Two -> { router.navigateTo(shortcutRoutes[1]); true }
                    Key.Three -> { router.navigateTo(shortcutRoutes[2]); true }
                    Key.Four -> { router.navigateTo(shortcutRoutes[3]); true }
                    Key.Five -> { router.navigateTo(shortcutRoutes[4]); true }
                    Key.Six -> { router.navigateTo(shortcutRoutes[5]); true }
                    Key.Seven -> { router.navigateTo(shortcutRoutes[6]); true }
                    Key.Eight -> { router.navigateTo(shortcutRoutes[7]); true }
                    Key.Nine -> { router.navigateTo(shortcutRoutes[8]); true }
                    else -> false
                }
            },
        ) {
            App()
        }
    }
}
