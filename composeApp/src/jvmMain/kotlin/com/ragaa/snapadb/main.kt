package com.ragaa.snapadb

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ragaa.snapadb.core.di.appModules
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(appModules) }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SnapADB",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            App()
        }
    }
}
