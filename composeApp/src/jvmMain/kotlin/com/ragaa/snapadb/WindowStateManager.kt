package com.ragaa.snapadb

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.util.prefs.Preferences

object WindowStateManager {

    private val prefs = Preferences.userNodeForPackage(WindowStateManager::class.java)

    private const val KEY_X = "window_x"
    private const val KEY_Y = "window_y"
    private const val KEY_WIDTH = "window_width"
    private const val KEY_HEIGHT = "window_height"
    private const val KEY_MAXIMIZED = "window_maximized"

    fun loadWindowState(): WindowState {
        val width = prefs.getFloat(KEY_WIDTH, 1200f).dp
        val height = prefs.getFloat(KEY_HEIGHT, 800f).dp
        val isMaximized = prefs.getBoolean(KEY_MAXIMIZED, false)

        val x = prefs.getFloat(KEY_X, Float.NaN)
        val y = prefs.getFloat(KEY_Y, Float.NaN)

        val position = if (x.isNaN() || y.isNaN()) {
            WindowPosition.PlatformDefault
        } else {
            WindowPosition(x.dp, y.dp)
        }

        return WindowState(
            placement = if (isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = position,
            size = DpSize(width, height),
        )
    }

    fun saveWindowState(state: WindowState) {
        val pos = state.position
        if (pos is WindowPosition.Absolute) {
            prefs.putFloat(KEY_X, pos.x.value)
            prefs.putFloat(KEY_Y, pos.y.value)
        }
        prefs.putFloat(KEY_WIDTH, state.size.width.value)
        prefs.putFloat(KEY_HEIGHT, state.size.height.value)
        prefs.putBoolean(KEY_MAXIMIZED, state.placement == WindowPlacement.Maximized)
        prefs.flush()
    }
}
