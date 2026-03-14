package com.ragaa.snapadb.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended color palette for SnapADB that goes beyond Material3's built-in slots.
 * Access via `SnapAdbTheme.colors` from any composable.
 *
 * Includes: brand accent colors, semantic device/connection colors,
 * log level colors, and chart/graph colors.
 */
@Immutable
data class SnapColors(
    // Brand accents
    val accent: Color,
    val accentVariant: Color,
    val mint: Color,
    val mintVariant: Color,
    val pink: Color,
    val pinkVariant: Color,
    val amber: Color,
    val purple: Color,

    // Semantic — device/connection
    val connected: Color,
    val disconnected: Color,
    val unauthorized: Color,

    // Log levels
    val logVerbose: Color,
    val logDebug: Color,
    val logInfo: Color,
    val logWarn: Color,
    val logError: Color,
    val logFatal: Color,

    // Charts
    val chartBlue: Color,
    val chartGreen: Color,
    val chartOrange: Color,
    val chartRed: Color,
    val chartPurple: Color,

    // Terminal
    val terminalCommand: Color,
    val terminalStdout: Color,
    val terminalStderr: Color,
)

val DarkSnapColors = SnapColors(
    accent = SnapBlue,
    accentVariant = Color(0xFF4A6BFF),
    mint = SnapMint,
    mintVariant = Color(0xFF2BA89A),
    pink = SnapPink,
    pinkVariant = Color(0xFFE5446D),
    amber = SnapAmber,
    purple = SnapPurple,
    connected = SnapMint,
    disconnected = Color(0xFF6B7280),
    unauthorized = SnapPink,
    logVerbose = Color(0xFF6B7280),
    logDebug = SnapBlue,
    logInfo = SnapMint,
    logWarn = SnapAmber,
    logError = SnapRed,
    logFatal = SnapPurple,
    chartBlue = SnapBlue,
    chartGreen = SnapMint,
    chartOrange = SnapAmber,
    chartRed = SnapRed,
    chartPurple = SnapPurple,
    terminalCommand = SnapBlue,
    terminalStdout = Color(0xFFE1E3EC),
    terminalStderr = SnapRed,
)

val LightSnapColors = SnapColors(
    accent = SnapBlueDark,
    accentVariant = SnapBlue,
    mint = SnapMintDark,
    mintVariant = SnapMint,
    pink = SnapPinkDark,
    pinkVariant = SnapPink,
    amber = Color(0xFFE6A030),
    purple = Color(0xFFA855F7),
    connected = SnapMintDark,
    disconnected = Color(0xFF9CA3AF),
    unauthorized = SnapPinkDark,
    logVerbose = Color(0xFF6B7280),
    logDebug = SnapBlueDark,
    logInfo = SnapMintDark,
    logWarn = Color(0xFFE6A030),
    logError = Color(0xFFD32F2F),
    logFatal = Color(0xFF9333EA),
    chartBlue = SnapBlueDark,
    chartGreen = SnapMintDark,
    chartOrange = Color(0xFFE6A030),
    chartRed = Color(0xFFD32F2F),
    chartPurple = Color(0xFF9333EA),
    terminalCommand = SnapBlueDark,
    terminalStdout = Color(0xFF1A1C24),
    terminalStderr = Color(0xFFD32F2F),
)

val LocalSnapColors = staticCompositionLocalOf { DarkSnapColors }
