package com.ragaa.snapadb.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun SnapAdbTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColors else LightColors
    val snapColors = if (isDark) DarkSnapColors else LightSnapColors

    CompositionLocalProvider(LocalSnapColors provides snapColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SnapAdbTypography,
            shapes = SnapAdbShapes,
            content = content,
        )
    }
}

/**
 * Central access point for all SnapADB theming.
 *
 * Usage from any composable:
 * ```
 * val color = SnapAdbTheme.colors.logError
 * val textStyle = SnapAdbTheme.typography.bodyMedium
 * val shape = SnapAdbTheme.shapes.medium
 * val materialColor = SnapAdbTheme.colorScheme.primary
 * ```
 */
object SnapAdbTheme {
    /** Extended SnapADB color palette (brand, semantic, log levels, charts). */
    val colors: SnapColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSnapColors.current

    /** Material3 color scheme. */
    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /** Material3 typography. */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** Material3 shapes. */
    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes
}
