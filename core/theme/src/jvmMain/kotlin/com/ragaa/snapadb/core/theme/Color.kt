package com.ragaa.snapadb.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// === Brand Colors ===
val SnapBlue = Color(0xFF6C8EFF)
val SnapBlueDark = Color(0xFF4A6BFF)
val SnapMint = Color(0xFF4ECDC4)
val SnapMintDark = Color(0xFF2BA89A)
val SnapPink = Color(0xFFFF6B9D)
val SnapPinkDark = Color(0xFFE5446D)
val SnapAmber = Color(0xFFFFB84D)
val SnapRed = Color(0xFFFF5252)
val SnapPurple = Color(0xFFC77DFF)

// === Semantic Log Colors ===
object LogColors {
    val verbose = Color(0xFF6B7280)
    val debug = Color(0xFF6C8EFF)
    val info = Color(0xFF4ECDC4)
    val warn = Color(0xFFFFB84D)
    val error = Color(0xFFFF5252)
    val fatal = Color(0xFFC77DFF)
}

// === Dark Theme — "Midnight Developer" ===
private val DarkBackground = Color(0xFF0F1117)
private val DarkSurface = Color(0xFF161822)
private val DarkSurfaceVariant = Color(0xFF1C1F2E)
private val DarkSurfaceBright = Color(0xFF242838)
private val DarkOutline = Color(0xFF2A2E3F)
private val DarkOutlineVariant = Color(0xFF1F2233)
private val DarkOnSurface = Color(0xFFE1E3EC)
private val DarkOnSurfaceVariant = Color(0xFF8B8FA3)

val DarkColors = darkColorScheme(
    primary = SnapBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E2A4A),
    onPrimaryContainer = Color(0xFFB8CAFF),
    secondary = SnapMint,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF1A3330),
    onSecondaryContainer = Color(0xFFADE8E2),
    tertiary = SnapPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3D1A28),
    onTertiaryContainer = Color(0xFFFFB8D0),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = SnapBlue,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = Color(0xFF0B0D13),
    surfaceContainerLow = Color(0xFF12141C),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceBright,
    error = SnapRed,
    onError = Color.White,
    errorContainer = Color(0xFF3D1414),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = Color(0xFFE1E3EC),
    inverseOnSurface = Color(0xFF1C1F2E),
    inversePrimary = SnapBlueDark,
)

// === Light Theme ===
private val LightBackground = Color(0xFFFAFBFE)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF0F1F7)
private val LightSurfaceBright = Color(0xFFE8EAF0)
private val LightOutline = Color(0xFFD8DAE3)
private val LightOutlineVariant = Color(0xFFE8EAF0)
private val LightOnSurface = Color(0xFF1A1C24)
private val LightOnSurfaceVariant = Color(0xFF5F6377)

val LightColors = lightColorScheme(
    primary = SnapBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001A6B),
    secondary = SnapMintDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F5F0),
    onSecondaryContainer = Color(0xFF002B26),
    tertiary = SnapPinkDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3F0019),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = SnapBlueDark,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6FB),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceBright,
    surfaceContainerHighest = Color(0xFFDFE1EA),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = Color(0xFF2E3140),
    inverseOnSurface = Color(0xFFF0F1F7),
    inversePrimary = SnapBlue,
)
