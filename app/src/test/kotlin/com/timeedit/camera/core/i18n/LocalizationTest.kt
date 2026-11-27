/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.i18n

import com.timeedit.camera.core.location.TimemarkLocationFormatter
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.DateFormatStyle
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {

    // 2026-11-25 is a Wednesday (Thứ Tư / Wednesday / 星期三)
    private val sampleEpochMillis: Long = ZonedDateTime.of(
        2026, 11, 25, 10, 30, 0, 0, ZoneId.of("UTC"),
    ).toInstant().toEpochMilli()
    private val utcZone: ZoneId = ZoneId.of("UTC")

    @Test
    fun appLanguageFromCodeResolvesAndFallsBackToVi() {
        assertEquals(AppLanguage.VI, AppLanguage.fromCode("vi"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ZH_CN, AppLanguage.fromCode("zh"))
        assertEquals(AppLanguage.VI, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.VI, AppLanguage.fromCode("unknown"))
    }

    @Test
    fun timeProviderFormatsRegionalDatesAndWeekdaysForAllThreeLanguages() {
        assertEquals(
            "25 Tháng 11,2026",
            TimeProvider.formatDateOnly(
                sampleEpochMillis,
                pattern = DateFormatStyle.VN_LONG.pattern,
                zoneId = utcZone,
                locale = AppLanguage.VI.toLocale(),
            ),
        )
        assertEquals(
            "Thứ Tư",
            TimeProvider.formatWeekday(sampleEpochMillis, full = true, zoneId = utcZone, locale = AppLanguage.VI.toLocale()),
        )

        assertEquals(
            "25 November 2026",
            TimeProvider.formatDateOnly(
                sampleEpochMillis,
                pattern = DateFormatStyle.EN_LONG.pattern,
                zoneId = utcZone,
                locale = AppLanguage.EN.toLocale(),
            ),
        )
        assertEquals(
            "Wednesday",
            TimeProvider.formatWeekday(sampleEpochMillis, full = true, zoneId = utcZone, locale = AppLanguage.EN.toLocale()),
        )

        assertEquals(
            "2026年11月25日",
            TimeProvider.formatDateOnly(
                sampleEpochMillis,
                pattern = DateFormatStyle.ZH_LONG.pattern,
                zoneId = utcZone,
                locale = AppLanguage.ZH_CN.toLocale(),
            ),
        )
        assertEquals(
            "星期三",
            TimeProvider.formatWeekday(sampleEpochMillis, full = true, zoneId = utcZone, locale = AppLanguage.ZH_CN.toLocale()),
        )
    }

    @Test
    fun appStringsProvideDistinctSubtitlesAndAvoidTechnicalExifJargon() {
        val vi = AppStringsProvider.forLanguage(AppLanguage.VI)
        val en = AppStringsProvider.forLanguage(AppLanguage.EN)
        val zh = AppStringsProvider.forLanguage(AppLanguage.ZH_CN)

        assertEquals("100% Chân thực", vi.brandAuthenticSubtitle)
        assertEquals("100% Authentic", en.brandAuthenticSubtitle)
        assertEquals("100% 真实", zh.brandAuthenticSubtitle)

        assertEquals("ĐÓNG DẤU ẢNH", vi.modeStampPhotos)
        assertEquals("STAMP PHOTOS", en.modeStampPhotos)
        assertEquals("相册水印", zh.modeStampPhotos)

        assertEquals("Thời gian", vi.wmTime)
        assertEquals("Time", en.wmTime)
        assertEquals("时间", zh.wmTime)

        assertEquals("Sáng", vi.themeLight)
        assertEquals("Light", en.themeLight)
        assertEquals("浅色", zh.themeLight)

        assertEquals("Cài đặt", vi.settingsTitle)
        assertEquals("Settings", en.settingsTitle)
        assertEquals("设置", zh.settingsTitle)

        listOf(vi, en, zh).forEach { strings ->
            assertFalse(strings.originalCaptureDate("2026").contains("EXIF", ignoreCase = true))
            assertFalse(strings.usePhotoOriginalTime("2026").contains("EXIF", ignoreCase = true))
            assertFalse(strings.themeLight.contains("Liquid Glass", ignoreCase = true))
            assertEquals("Time Edit v1.0.0", strings.versionFooter)
        }
    }

    @Test
    fun localizeTemplateForLanguageSwitchesDateFormatAndPreservesCustomEdits() {
        val initialVi = DefaultWatermarkTemplates.timeLocation(AppLanguage.VI).copy(
            showAddress = true,
            note = "User custom note",
        )

        val switchedEn = DefaultWatermarkTemplates.localizeTemplateForLanguage(initialVi, AppLanguage.EN)
        assertEquals(DateFormatStyle.EN_LONG, switchedEn.dateFormat)
        assertEquals("Time Stamp", switchedEn.name)
        assertTrue(switchedEn.showAddress)
        assertEquals("User custom note", switchedEn.note)
        assertEquals(BrandBadgePreset.VN_AUTHENTIC, switchedEn.brandBadgePreset)

        val switchedZh = DefaultWatermarkTemplates.localizeTemplateForLanguage(switchedEn, AppLanguage.ZH_CN)
        assertEquals(DateFormatStyle.ZH_LONG, switchedZh.dateFormat)
        assertEquals("经典时间", switchedZh.name)
        assertTrue(switchedZh.showAddress)
        assertEquals("User custom note", switchedZh.note)

        val switchedBackVi = DefaultWatermarkTemplates.localizeTemplateForLanguage(switchedZh, AppLanguage.VI)
        assertEquals(DateFormatStyle.VN_LONG, switchedBackVi.dateFormat)
        assertEquals("Thời gian", switchedBackVi.name)
        assertTrue(switchedBackVi.showAddress)
        assertEquals("User custom note", switchedBackVi.note)
        assertEquals(BrandBadgePreset.VN_AUTHENTIC, switchedBackVi.brandBadgePreset)

        val regionalOfficialVn = initialVi.copy(brandBadgePreset = BrandBadgePreset.ORIGINAL_VN)
        val regionalOfficialZh = DefaultWatermarkTemplates.localizeTemplateForLanguage(regionalOfficialVn, AppLanguage.ZH_CN)
        assertEquals(BrandBadgePreset.ORIGINAL_ZH, regionalOfficialZh.brandBadgePreset)
    }

    @Test
    fun formatWeatherTranslatesWmoCodesAcrossAllThreeLanguages() {
        assertEquals("28°C Quang đãng", TimemarkLocationFormatter.formatWeather(28.0, 0, language = AppLanguage.VI))
        assertEquals("28°C Clear", TimemarkLocationFormatter.formatWeather(28.0, 0, language = AppLanguage.EN))
        assertEquals("28°C 晴", TimemarkLocationFormatter.formatWeather(28.0, 0, language = AppLanguage.ZH_CN))
    }
}
