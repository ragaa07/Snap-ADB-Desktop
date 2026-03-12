package com.ragaa.snapadb.core.navigation

sealed class Route(val title: String) {
    data object Dashboard : Route("Dashboard")
    data object MultiDevice : Route("Multi Device")
    data object AppManager : Route("App Manager")
    data object FileExplorer : Route("File Explorer")
    data object Shell : Route("Shell")
    data object ScreenMirror : Route("Screen Mirror")
    data object DeviceControls : Route("Device Controls")
    data object Performance : Route("Performance")
    data object DeepLink : Route("Deep Link")
    data object Notification : Route("Notification")
}
