/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.AppStringsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun appThemeModeParsesDarkAndLightWithDarkDefault() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromCode("dark"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromCode("light"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromCode("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromCode(null))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromCode("system"))
        assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT), AppThemeMode.entries)
    }

    @Test
    fun darkPaletteMatchesOriginalHexTokensUnchanged() {
        assertFalse(TimeEditDarkPalette.isLight)
        assertEquals(Color(0xFF0A111A), TimeEditDarkPalette.surface)
        assertEquals(Color(0xFF121E2E), TimeEditDarkPalette.surfaceElevated)
        assertEquals(Color(0xFF60C1E5), TimeEditDarkPalette.accent)
        assertEquals(Color(0xFFA0E0EF), TimeEditDarkPalette.accentSoft)
        assertEquals(Color(0xFF1B365D), TimeEditDarkPalette.accentContainer)
        assertEquals(Color(0xFF005A9C), TimeEditDarkPalette.borderStroke)
        assertEquals(Color(0xFFF4F8FC), TimeEditDarkPalette.textPrimary)
        assertEquals(Color(0xFFB4C6D8), TimeEditDarkPalette.textSecondary)
    }

    @Test
    fun lightPaletteUsesExactUserPastelPaletteWithHighTextContrast() {
        assertTrue(TimeEditLightPalette.isLight)
        assertEquals(Color(0xFFFFCED8), BlushRose)
        assertEquals(Color(0xFFFFDDDD), FrostPink)
        assertEquals(Color(0xFFFFF4DE), ChampagneCream)
        assertEquals(Color(0xFFFFE8C4), PeachApricot)

        assertEquals(ChampagneCream, TimeEditLightPalette.surface)
        assertEquals(FrostPink, TimeEditLightPalette.surfaceElevated)
        assertEquals(BlushRose, TimeEditLightPalette.accentContainer)

        val bgLum = ChampagneCream.luminance()
        val fgLum = TimeEditLightPalette.textPrimary.luminance()
        val contrast = (bgLum + 0.05f) / (fgLum + 0.05f)
        assertTrue("Expected high WCAG contrast >= 7.0, was $contrast", contrast >= 7.0f)
    }

    @Test
    fun themeStringsLocalizedAcrossAllThreeLanguages() {
        val vi = AppStringsProvider.forLanguage(AppLanguage.VI)
        val en = AppStringsProvider.forLanguage(AppLanguage.EN)
        val zh = AppStringsProvider.forLanguage(AppLanguage.ZH_CN)

        assertEquals("Giao diện", vi.themeLabel)
        assertEquals("Tối", vi.themeDark)
        assertTrue(vi.themeLight.contains("Sáng"))

        assertEquals("Appearance", en.themeLabel)
        assertEquals("Dark", en.themeDark)
        assertTrue(en.themeLight.contains("Light"))

        assertEquals("界面主题", zh.themeLabel)
        assertEquals("深色", zh.themeDark)
        assertTrue(zh.themeLight.contains("浅色"))
    }
}
