/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.camera

import androidx.compose.ui.Alignment
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.domain.watermark.TimeLayoutVariant
import com.timeedit.camera.domain.watermark.WatermarkCorner
import com.timeedit.camera.domain.watermark.WatermarkJsonCodec
import com.timeedit.camera.domain.watermark.WatermarkPosition
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.resolveWatermarkOrigin
import com.timeedit.camera.engine.renderer.PhotoStampProcessor
import com.timeedit.camera.engine.renderer.WatermarkCanvasRenderer
import com.timeedit.camera.ui.gallery.isQuarterTurnRotation
import com.timeedit.camera.ui.gallery.progressPopupAlignmentForRotation
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAndPipelineTest {

    @Test
    fun photoQualityFromCodeParsesAndFallsBackToHd() {
        assertEquals(PhotoQuality.SD, PhotoQuality.fromCode("sd"))
        assertEquals(PhotoQuality.HD, PhotoQuality.fromCode("hd"))
        assertEquals(PhotoQuality.FHD, PhotoQuality.fromCode("FHD"))
        assertEquals(PhotoQuality.HD, PhotoQuality.fromCode("unknown"))
        assertEquals(PhotoQuality.HD, PhotoQuality.fromCode(null))
        assertEquals(960, PhotoQuality.SD.maxDimension)
        assertEquals(1280, PhotoQuality.HD.maxDimension)
        assertEquals(1920, PhotoQuality.FHD.maxDimension)
    }

    @Test
    fun computeScaledDimensionsDownscalesPreservingAspectAndNeverUpscales() {
        assertEquals(960 to 720, PhotoStampProcessor.computeScaledDimensions(4000, 3000, PhotoQuality.SD.maxDimension))
        assertEquals(1280 to 960, PhotoStampProcessor.computeScaledDimensions(4000, 3000, PhotoQuality.HD.maxDimension))
        assertEquals(1920 to 1440, PhotoStampProcessor.computeScaledDimensions(4000, 3000, PhotoQuality.FHD.maxDimension))

        assertEquals(1280 to 720, PhotoStampProcessor.computeScaledDimensions(4000, 2250, PhotoQuality.HD.maxDimension))
        assertEquals(960 to 1280, PhotoStampProcessor.computeScaledDimensions(3000, 4000, PhotoQuality.HD.maxDimension))

        // Smaller gallery photos must keep their original resolution
        assertEquals(800 to 600, PhotoStampProcessor.computeScaledDimensions(800, 600, PhotoQuality.HD.maxDimension))
        assertEquals(1280 to 960, PhotoStampProcessor.computeScaledDimensions(1280, 960, PhotoQuality.FHD.maxDimension))
    }

    @Test
    fun computeSupportedVideoPresetsFiltersByCameraFpsAndCodecCapability() {
        // Device HAL locked to 30fps (e.g., Redmi Note 11 Pro+ 5G Camera2 normal video stream)
        val only30 = CameraController.computeSupportedVideoPresets(
            hasHdQuality = true,
            hasFhdQuality = true,
            supportedFpsRanges = listOf(10 to 10, 15 to 20, 5 to 30, 30 to 30),
            supportsAvcAt = { _, _, _ -> true },
        )
        assertEquals(listOf(VideoQualityPreset.HD_30, VideoQualityPreset.FHD_30), only30)

        // Device HAL + AVC encoder supporting 60fps across both 720p and 1080p
        val full60 = CameraController.computeSupportedVideoPresets(
            hasHdQuality = true,
            hasFhdQuality = true,
            supportedFpsRanges = listOf(30 to 30, 60 to 60),
            supportsAvcAt = { _, _, _ -> true },
        )
        assertEquals(
            listOf(
                VideoQualityPreset.HD_30,
                VideoQualityPreset.HD_60,
                VideoQualityPreset.FHD_30,
                VideoQualityPreset.FHD_60,
            ),
            full60,
        )

        // Device HAL reports 60fps, but hardware AVC encoder rejects 1080p@60fps
        val partial60 = CameraController.computeSupportedVideoPresets(
            hasHdQuality = true,
            hasFhdQuality = true,
            supportedFpsRanges = listOf(30 to 30, 30 to 60),
            supportsAvcAt = { w, _, fps -> !(w >= 1920 && fps >= 60) },
        )
        assertEquals(
            listOf(VideoQualityPreset.HD_30, VideoQualityPreset.HD_60, VideoQualityPreset.FHD_30),
            partial60,
        )
    }

    @Test
    fun resolveEffectiveVideoQualityStepsDownCleanlyWhenCameraLacksCapability() {
        val supportedOnly30 = listOf(VideoQualityPreset.HD_30, VideoQualityPreset.FHD_30)
        assertEquals(
            VideoQualityPreset.FHD_30,
            VideoQualityPreset.resolveEffectiveVideoQuality(VideoQualityPreset.FHD_60, supportedOnly30),
        )
        assertEquals(
            VideoQualityPreset.HD_30,
            VideoQualityPreset.resolveEffectiveVideoQuality(VideoQualityPreset.HD_60, supportedOnly30),
        )

        val supportedHdOnly = listOf(VideoQualityPreset.HD_30)
        assertEquals(
            VideoQualityPreset.HD_30,
            VideoQualityPreset.resolveEffectiveVideoQuality(VideoQualityPreset.FHD_60, supportedHdOnly),
        )
    }

    @Test
    fun progressPopupTracksPhysicalTopEdgeAcrossAllOrientations() {
        assertEquals(Alignment.TopCenter, progressPopupAlignmentForRotation(0))
        assertEquals(Alignment.CenterEnd, progressPopupAlignmentForRotation(90))
        assertEquals(Alignment.BottomCenter, progressPopupAlignmentForRotation(180))
        assertEquals(Alignment.CenterStart, progressPopupAlignmentForRotation(270))

        assertFalse(isQuarterTurnRotation(0))
        assertTrue(isQuarterTurnRotation(90))
        assertFalse(isQuarterTurnRotation(180))
        assertTrue(isQuarterTurnRotation(270))
    }

    @Test
    fun alignUpToMultipleAlignsVideoDimensionsForHardwareEncoders() {
        assertEquals(1088, CameraController.alignUpToMultiple(1080, 16))
        assertEquals(1920, CameraController.alignUpToMultiple(1920, 16))
        assertEquals(720, CameraController.alignUpToMultiple(720, 16))
        assertEquals(1080, CameraController.alignUpToMultiple(1080, 1))
    }

    @Test
    fun timeLayoutVariantTogglesAndFormatsCompactBarAcrossLanguages() {
        assertEquals(TimeLayoutVariant.COMPACT_BAR, TimeLayoutVariant.CLASSIC_LARGE.next())
        assertEquals(TimeLayoutVariant.CLASSIC_LARGE, TimeLayoutVariant.COMPACT_BAR.next())

        val utc = ZoneId.of("UTC")
        // 2026-11-25 10:30 UTC (Wednesday / Thứ Tư / 星期三)
        val epoch = ZonedDateTime.of(2026, 11, 25, 10, 30, 0, 0, utc).toInstant().toEpochMilli()

        assertEquals(
            "Thứ Tư, 25 Tháng 11,2026 10:30",
            WatermarkCanvasRenderer.formatCompactBarDateTimeLine(
                epochMillis = epoch,
                template = DefaultWatermarkTemplates.timeLocation(AppLanguage.VI),
                language = AppLanguage.VI,
                zoneId = utc,
            ),
        )
        assertEquals(
            "Wednesday, 25 November 2026 10:30",
            WatermarkCanvasRenderer.formatCompactBarDateTimeLine(
                epochMillis = epoch,
                template = DefaultWatermarkTemplates.timeLocation(AppLanguage.EN),
                language = AppLanguage.EN,
                zoneId = utc,
            ),
        )
        assertEquals(
            "2026年11月25日 星期三 10:30",
            WatermarkCanvasRenderer.formatCompactBarDateTimeLine(
                epochMillis = epoch,
                template = DefaultWatermarkTemplates.timeLocation(AppLanguage.ZH_CN),
                language = AppLanguage.ZH_CN,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun resolveWatermarkOriginSupportsDistinctVerticalMarginAndLegacyJsonFallback() {
        val (x, y) = resolveWatermarkOrigin(
            photoWidth = 1000,
            photoHeight = 2000,
            boxWidth = 300f,
            boxHeight = 100f,
            outerMargin = 18f,
            position = WatermarkPosition(0.025f, 0.85f, WatermarkCorner.BOTTOM_LEFT),
            outerMarginVertical = 30f,
        )
        assertEquals(18f, x, 0.01f)
        assertEquals(1870f, y, 0.01f)

        val encoded = WatermarkJsonCodec.encodeTemplate(DefaultWatermarkTemplates.timeLocation())
        val legacyJson = encoded.replace(Regex(""""timeLayoutVariant"\s*:\s*"[^"]+"\s*,?\s*"""), "")
        val decoded = WatermarkJsonCodec.decodeTemplate(legacyJson).getOrThrow()
        assertEquals(TimeLayoutVariant.CLASSIC_LARGE, decoded.timeLayoutVariant)
    }
}
