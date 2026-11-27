/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.renderer

import com.google.common.truth.Truth.assertThat
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeMode
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PhotoStampProcessorTest {

    @Test
    fun parseExifDateTime_parsesExifAndIsoFormats() {
        val parsedExif = PhotoStampProcessor.parseExifDateTime("2026:11:25 14:05:00", ZoneOffset.UTC)
        val parsedIso = PhotoStampProcessor.parseExifDateTime("2026-11-25 14:05:00", ZoneOffset.UTC)
        assertThat(parsedExif).isEqualTo(1795615500000L)
        assertThat(parsedIso).isEqualTo(1795615500000L)

        assertThat(PhotoStampProcessor.parseExifDateTime(null, ZoneOffset.UTC)).isNull()
        assertThat(PhotoStampProcessor.parseExifDateTime("  ", ZoneOffset.UTC)).isNull()
        assertThat(PhotoStampProcessor.parseExifDateTime("not-a-date", ZoneOffset.UTC)).isNull()
    }

    @Test
    fun computeInSampleSize_downsamplesOversizedImagesByPowersOfTwo() {
        assertThat(PhotoStampProcessor.computeInSampleSize(4000, 3000, maxDimension = 4096)).isEqualTo(1)
        assertThat(PhotoStampProcessor.computeInSampleSize(12000, 9000, maxDimension = 4096)).isEqualTo(4)
        assertThat(PhotoStampProcessor.computeInSampleSize(6000, 4000, maxDimension = 4096)).isEqualTo(2)
        assertThat(PhotoStampProcessor.computeInSampleSize(0, 0, maxDimension = 4096)).isEqualTo(1)
    }

    @Test
    fun resolveStampEpochMillis_prefersCustomPerPhotoConfigOrFallsBackToShared() {
        val nowEpoch = 1795615500000L
        val sharedFixed = 1750000000000L
        val customFixed = 1710000000000L
        val sharedConfig = TimeConfig(mode = TimeMode.FIXED, fixedEpochMillis = sharedFixed)
        val customConfig = TimeConfig(mode = TimeMode.FIXED, fixedEpochMillis = customFixed)

        assertThat(
            PhotoStampProcessor.resolveStampEpochMillis(
                customTimeConfig = customConfig,
                sharedTimeConfig = sharedConfig,
                nowMillis = nowEpoch,
            ),
        ).isEqualTo(customFixed)

        assertThat(
            PhotoStampProcessor.resolveStampEpochMillis(
                customTimeConfig = null,
                sharedTimeConfig = sharedConfig,
                nowMillis = nowEpoch,
            ),
        ).isEqualTo(sharedFixed)

        assertThat(
            PhotoStampProcessor.resolveStampEpochMillis(
                customTimeConfig = null,
                sharedTimeConfig = TimeConfig(mode = TimeMode.LIVE),
                nowMillis = nowEpoch,
            ),
        ).isEqualTo(nowEpoch)
    }

    @Test
    fun defaultTimeLocationTemplate_isTimeFirstWithoutForcedLocation() {
        val defaultTemplate = DefaultWatermarkTemplates.timeLocation()
        assertThat(defaultTemplate.showTime).isTrue()
        assertThat(defaultTemplate.showAddress).isFalse()
        assertThat(defaultTemplate.usesLocationFeature).isFalse()

        val withAddress = defaultTemplate.copy(showAddress = true)
        assertThat(withAddress.usesLocationFeature).isTrue()

        val withCoordinates = defaultTemplate.copy(showCoordinates = true)
        assertThat(withCoordinates.usesLocationFeature).isTrue()
    }
}
