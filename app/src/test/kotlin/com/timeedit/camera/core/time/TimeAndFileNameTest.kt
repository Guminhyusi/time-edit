/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.time

import com.google.common.truth.Truth.assertThat
import com.timeedit.camera.core.storage.MediaStoreSaver
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TimeAndFileNameTest {
    private val sampleNow = 1795615500000L // 2026-11-25T14:05:00Z

    @Test
    fun resolveEpochMillis_respectsLiveFixedAndOffsetModes() {
        assertThat(TimeProvider.resolveEpochMillis(sampleNow, TimeConfig(mode = TimeMode.LIVE)))
            .isEqualTo(sampleNow)

        val fixed = 1700000000000L
        assertThat(
            TimeProvider.resolveEpochMillis(
                sampleNow,
                TimeConfig(mode = TimeMode.FIXED, fixedEpochMillis = fixed),
            ),
        ).isEqualTo(fixed)

        assertThat(
            TimeProvider.resolveEpochMillis(
                sampleNow,
                TimeConfig(mode = TimeMode.OFFSET, offsetMillis = 3_600_000L),
            ),
        ).isEqualTo(sampleNow + 3_600_000L)

        assertThat(
            TimeProvider.resolveEpochMillis(
                sampleNow,
                TimeConfig(mode = TimeMode.OFFSET, offsetMillis = -86_400_000L),
            ),
        ).isEqualTo(sampleNow - 86_400_000L)
    }

    @Test
    fun formatters_produceExpectedClockDateAndExifFormats() {
        assertThat(TimeProvider.formatClock(sampleNow, ZoneOffset.UTC)).isEqualTo("14:05:00")
        assertThat(TimeProvider.formatTimeHourMinute(sampleNow, use24Hour = true, zoneId = ZoneOffset.UTC))
            .isEqualTo("14:05")
        assertThat(TimeProvider.formatTimeHourMinute(sampleNow, use24Hour = false, zoneId = ZoneOffset.UTC))
            .isEqualTo("02:05")
        assertThat(TimeProvider.formatSecondsOnly(sampleNow, ZoneOffset.UTC)).isEqualTo("00")
        assertThat(TimeProvider.formatAmPm(sampleNow, ZoneOffset.UTC)).isEqualTo("PM")
        assertThat(TimeProvider.formatWeekday(sampleNow, full = true, zoneId = ZoneOffset.UTC, locale = Locale.US))
            .isEqualTo("Wednesday")
        assertThat(TimeProvider.formatWeekday(sampleNow, full = true, zoneId = ZoneOffset.UTC))
            .isEqualTo("Thứ Tư")
        assertThat(TimeProvider.formatWeekday(sampleNow + 4 * 86_400_000L, full = true, zoneId = ZoneOffset.UTC))
            .isEqualTo("Chủ Nhật")
        assertThat(TimeProvider.formatDateOnly(sampleNow, "dd 'Tháng' M,yyyy", ZoneOffset.UTC))
            .isEqualTo("25 Tháng 11,2026")
        assertThat(TimeProvider.formatExifDateTime(sampleNow, ZoneOffset.UTC))
            .isEqualTo("2026:11:25 14:05:00")
        assertThat(TimeProvider.formatDate(sampleNow, ZoneOffset.UTC, Locale.US))
            .isEqualTo("Wednesday, 2026/11/25")
    }

    @Test
    fun buildPhotoAndVideoFileNames_matchExpectedStampFormats() {
        assertThat(MediaStoreSaver.buildPhotoFileName(sampleNow, zoneId = ZoneOffset.UTC))
            .isEqualTo("IMG_20261125_140500.png")
        assertThat(
            MediaStoreSaver.buildPhotoFileName(sampleNow, suffix = "_orig", zoneId = ZoneOffset.UTC),
        ).isEqualTo("IMG_20261125_140500_orig.png")
        assertThat(
            MediaStoreSaver.buildPhotoFileName(sampleNow, suffix = "_2", zoneId = ZoneOffset.UTC),
        ).isEqualTo("IMG_20261125_140500_2.png")
        assertThat(MediaStoreSaver.buildVideoFileName(sampleNow, zoneId = ZoneOffset.UTC))
            .isEqualTo("VID_20261125_140500.mp4")
    }
}
