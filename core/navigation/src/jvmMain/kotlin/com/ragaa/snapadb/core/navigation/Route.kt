package com.ragaa.snapadb.core.navigation

sealed class Route(val title: String) {
    data object Dashboard : Route("Dashboard")
    data object MultiDevice : Route("Devices")
    data object AppManager : Route("Apps")
    data object FileExplorer : Route("Files")
    data object Shell : Route("Shell")
    data object Logcat : Route("Logcat")
    data object ScreenMirror : Route("Mirror")
    data object DeviceControls : Route("Controls")
    data object Performance : Route("Performance")
    data object DeepLink : Route("Deep Links")
    data object Notification : Route("Notifications")
    data object AppData : Route("App Data")
    data object BugReporter : Route("Bug Report")
    data object DbInspector : Route("DB Inspector")
    data object Network : Route("Network")
    data object Monkey : Route("Monkey")
}
