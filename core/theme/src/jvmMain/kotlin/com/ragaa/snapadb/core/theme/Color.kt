package com.ragaa.snapadb.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Teal200 = Color(0xFF80CBC4)
private val Teal400 = Color(0xFF26A69A)
private val Teal700 = Color(0xFF00796B)
private val Cyan200 = Color(0xFF80DEEA)
private val Cyan700 = Color(0xFF0097A7)

private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2D2D2D)
private val LightBackground = Color(0xFFF5F5F5)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE0E0E0)

val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal200,
    onPrimaryContainer = Color(0xFF002020),
    secondary = Cyan700,
    onSecondary = Color.White,
    secondaryContainer = Cyan200,
    onSecondaryContainer = Color(0xFF001F24),
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Teal700,
    onPrimaryContainer = Teal200,
    secondary = Cyan200,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Cyan700,
    onSecondaryContainer = Cyan200,
    background = DarkBackground,
    onBackground = Color(0xFFE6E1E5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)
