/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode(val code: String) {
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromCode(code: String?): AppThemeMode =
            if (code.equals("light", ignoreCase = true)) LIGHT else DARK
    }
}

@Immutable
data class TimeEditPalette(
    val isLight: Boolean,
    val surface: Color,
    val surfaceElevated: Color,
    val scrim: Color,
    val cameraScaffoldBg: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentContainer: Color,
    val borderStroke: Color,
    val amber: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val error: Color,
    val onAccent: Color,
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassSelectedTop: Color,
    val glassSelectedBottom: Color,
    val glassHighlightRim: Color,
    val glassShadowRim: Color,
    val glassDialogFill: Color,
    val glassAmbientShadow: Color,
)

val TimeEditDarkPalette = TimeEditPalette(
    isLight = false,
    surface = CameraSurface,
    surfaceElevated = CameraSurfaceElevated,
    scrim = CameraScrim,
    cameraScaffoldBg = Color.Black,
    accent = CyanAccent,
    accentSoft = SoftAqua,
    accentContainer = DeepNavy,
    borderStroke = CobaltBlue,
    amber = CustomTimeAmber,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    error = ErrorRed,
    onAccent = CameraSurface,
    glassFillTop = CameraSurfaceElevated,
    glassFillBottom = CameraSurfaceElevated,
    glassSelectedTop = DeepNavy,
    glassSelectedBottom = DeepNavy,
    glassHighlightRim = CobaltBlue,
    glassShadowRim = CobaltBlue,
    glassDialogFill = CameraSurfaceElevated,
    glassAmbientShadow = Color(0x66000000),
)

val TimeEditLightPalette = TimeEditPalette(
    isLight = true,
    surface = ChampagneCream,
    surfaceElevated = FrostPink,
    scrim = Color(0x523A2229),
    cameraScaffoldBg = ChampagneCream,
    accent = LightAccentRose,
    accentSoft = LightAccentBerry,
    accentContainer = BlushRose,
    borderStroke = LightBorderRose,
    amber = LightAmber,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    error = LightError,
    onAccent = LightOnAccent,
    glassFillTop = Color(0xB8FFF4DE),
    glassFillBottom = Color(0xA6FFDDDD),
    glassSelectedTop = Color(0xD6FFCED8),
    glassSelectedBottom = Color(0xC7FFE8C4),
    glassHighlightRim = Color(0xEEFFFFFF),
    glassShadowRim = Color(0x99E5A4B4),
    glassDialogFill = Color(0xFFFFF6E6),
    glassAmbientShadow = Color(0x28D45276),
)

val LocalTimeEditColors = staticCompositionLocalOf { TimeEditDarkPalette }

private val TimeEditDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CameraSurface,
    primaryContainer = DeepNavy,
    onPrimaryContainer = SoftAqua,
    secondary = CobaltBlue,
    onSecondary = TextPrimary,
    secondaryContainer = CameraSurfaceElevated,
    onSecondaryContainer = SoftAqua,
    tertiary = CustomTimeAmber,
    background = CameraSurface,
    onBackground = TextPrimary,
    surface = CameraSurface,
    onSurface = TextPrimary,
    surfaceVariant = CameraSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.Black,
)

private val TimeEditLightColorScheme = lightColorScheme(
    primary = LightAccentRose,
    onPrimary = LightOnAccent,
    primaryContainer = BlushRose,
    onPrimaryContainer = LightTextPrimary,
    secondary = PeachApricot,
    onSecondary = LightTextPrimary,
    secondaryContainer = FrostPink,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightAmber,
    background = ChampagneCream,
    onBackground = LightTextPrimary,
    surface = ChampagneCream,
    onSurface = LightTextPrimary,
    surfaceVariant = FrostPink,
    onSurfaceVariant = LightTextSecondary,
    error = LightError,
    onError = LightOnAccent,
)

@Composable
fun TimeEditTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isLight = themeMode == AppThemeMode.LIGHT
    val palette = if (isLight) TimeEditLightPalette else TimeEditDarkPalette
    val colorScheme = if (isLight) TimeEditLightColorScheme else TimeEditDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = isLight
            insetsController.isAppearanceLightNavigationBars = isLight
        }
    }

    CompositionLocalProvider(LocalTimeEditColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
