/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.domain.watermark

import com.google.common.truth.Truth.assertThat
import com.timeedit.camera.core.time.TimeProvider
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WatermarkSnapAndJsonTest {
    private val sampleNow = 1795615500000L // 2026-11-25T14:05:00Z

    @Test
    fun computeSnappedPosition_snapsToAllFourCornersAndSupportsFreeCenter() {
        val boxW = 0.40f
        val boxH = 0.20f

        val topLeft = computeSnappedPosition(0.01f, 0.04f, boxW, boxH)
        assertThat(topLeft.corner).isEqualTo(WatermarkCorner.TOP_LEFT)
        assertThat(topLeft.xFraction).isWithin(0.001f).of(0.022f)
        assertThat(topLeft.yFraction).isWithin(0.001f).of(0.022f)

        val topRight = computeSnappedPosition(0.59f, 0.02f, boxW, boxH)
        assertThat(topRight.corner).isEqualTo(WatermarkCorner.TOP_RIGHT)
        assertThat(topRight.xFraction).isWithin(0.001f).of(0.578f)

        val bottomLeft = computeSnappedPosition(0.04f, 0.78f, boxW, boxH)
        assertThat(bottomLeft.corner).isEqualTo(WatermarkCorner.BOTTOM_LEFT)
        assertThat(bottomLeft.yFraction).isWithin(0.001f).of(0.778f)

        val bottomRight = computeSnappedPosition(0.90f, 0.90f, boxW, boxH)
        assertThat(bottomRight.corner).isEqualTo(WatermarkCorner.BOTTOM_RIGHT)
        assertThat(bottomRight.xFraction).isWithin(0.001f).of(0.578f)
        assertThat(bottomRight.yFraction).isWithin(0.001f).of(0.778f)

        val center = computeSnappedPosition(0.25f, 0.35f, boxW, boxH)
        assertThat(center.corner).isEqualTo(WatermarkCorner.FREE)
        assertThat(center.xFraction).isWithin(0.001f).of(0.25f)
        assertThat(center.yFraction).isWithin(0.001f).of(0.35f)
    }

    @Test
    fun positionForCorner_placesBoxInsideMarginForEveryCorner() {
        val br = positionForCorner(WatermarkCorner.BOTTOM_RIGHT, boxWidthFraction = 0.5f, boxHeightFraction = 0.2f)
        assertThat(br.corner).isEqualTo(WatermarkCorner.BOTTOM_RIGHT)
        assertThat(br.xFraction).isWithin(0.001f).of(0.478f)
        assertThat(br.yFraction).isWithin(0.001f).of(0.778f)
    }

    @Test
    fun resolveWatermarkOrigin_anchorsCornersCleanlyRegardlessOfBoxExpansion() {
        val margin = 24f
        val (xSmall, ySmall) = resolveWatermarkOrigin(
            photoWidth = 1080,
            photoHeight = 1440,
            boxWidth = 420f,
            boxHeight = 180f,
            outerMargin = margin,
            position = WatermarkPosition(0.025f, 0.85f, WatermarkCorner.BOTTOM_LEFT),
        )
        val (xLarge, yLarge) = resolveWatermarkOrigin(
            photoWidth = 1080,
            photoHeight = 1440,
            boxWidth = 520f,
            boxHeight = 420f,
            outerMargin = margin,
            position = WatermarkPosition(0.025f, 0.85f, WatermarkCorner.BOTTOM_LEFT),
        )
        assertThat(xSmall).isWithin(0.01f).of(margin)
        assertThat(ySmall + 180f).isWithin(0.01f).of(1440f - margin)
        assertThat(xLarge).isWithin(0.01f).of(margin)
        assertThat(yLarge + 420f).isWithin(0.01f).of(1440f - margin)

        val (trX, trY) = resolveWatermarkOrigin(
            photoWidth = 1080,
            photoHeight = 1440,
            boxWidth = 300f,
            boxHeight = 160f,
            outerMargin = margin,
            position = WatermarkPosition(0.5f, 0.5f, WatermarkCorner.TOP_RIGHT),
        )
        assertThat(trX + 300f).isWithin(0.01f).of(1080f - margin)
        assertThat(trY).isWithin(0.01f).of(margin)

        val (fullX, fullY) = resolveWatermarkOrigin(
            photoWidth = 1080,
            photoHeight = 1440,
            boxWidth = 1032f,
            boxHeight = 220f,
            outerMargin = margin,
            position = WatermarkPosition(0.022f, 0.2f, WatermarkCorner.BOTTOM_RIGHT),
            isFullWidth = true,
        )
        assertThat(fullX).isWithin(0.01f).of(margin)
        assertThat(fullY + 220f).isWithin(0.01f).of(1440f - margin)
    }

    @Test
    fun styleCapabilities_hideDeadTitleAndSubtitleFields() {
        assertThat(WatermarkStyleId.TIME_LOCATION.supportsTitle).isFalse()
        assertThat(WatermarkStyleId.TIME_LOCATION.supportsSubtitle).isFalse()
        assertThat(WatermarkStyleId.LOCATION_BAR.supportsTitle).isFalse()
        assertThat(WatermarkStyleId.LOCATION_BAR.isFullWidth).isTrue()
        assertThat(WatermarkStyleId.WORK_REPORT.supportsTitle).isTrue()
        assertThat(WatermarkStyleId.WORK_REPORT.supportsSubtitle).isFalse()
        assertThat(WatermarkStyleId.COMPLETED.supportsSubtitle).isTrue()
        assertThat(WatermarkStyleId.SECURITY_GUARD.supportsSubtitle).isTrue()
    }

    @Test
    fun clampFreePosition_keepsElementAtExactDropCoordinatesWithinMargins() {
        val center = clampFreePosition(0.37f, 0.41f, boxWidthFraction = 0.25f, boxHeightFraction = 0.12f)
        assertThat(center.corner).isEqualTo(WatermarkCorner.FREE)
        assertThat(center.xFraction).isWithin(0.001f).of(0.37f)
        assertThat(center.yFraction).isWithin(0.001f).of(0.41f)

        val clampedBottomRight = clampFreePosition(0.95f, 0.98f, boxWidthFraction = 0.30f, boxHeightFraction = 0.15f)
        assertThat(clampedBottomRight.corner).isEqualTo(WatermarkCorner.FREE)
        assertThat(clampedBottomRight.xFraction).isWithin(0.001f).of(0.678f)
        assertThat(clampedBottomRight.yFraction).isWithin(0.001f).of(0.828f)
    }

    @Test
    fun hasCustomElementPositions_detectsMovedElementsAndResetsCleanly() {
        val base = DefaultWatermarkTemplates.timeLocation()
        assertThat(base.hasCustomElementPositions).isFalse()

        val movedMain = base.copy(position = WatermarkPosition(0.25f, 0.45f, WatermarkCorner.FREE))
        assertThat(movedMain.hasCustomElementPositions).isTrue()

        val movedBrand = base.copy(brandPosition = WatermarkPosition(0.15f, 0.20f, WatermarkCorner.FREE))
        assertThat(movedBrand.hasCustomElementPositions).isTrue()

        val movedLogo = base.copy(logoPosition = WatermarkPosition(0.55f, 0.15f, WatermarkCorner.FREE))
        assertThat(movedLogo.hasCustomElementPositions).isTrue()
    }

    @Test
    fun defaultTemplates_containsAll12TimemarkTemplatesAndRoundTripsJson() {
        val all = DefaultWatermarkTemplates.all()
        assertThat(all).hasSize(12)

        val tl = DefaultWatermarkTemplates.timeLocation()
        assertThat(tl.theme.backgroundAlpha).isWithin(0.001f).of(0.0f)
        assertThat(tl.theme.accentColorHex).isEqualTo("#FFC233")
        assertThat(tl.logoPlacement).isEqualTo(LogoPlacement.TOP_LEFT)
        assertThat(tl.brandBadgePreset).isEqualTo(BrandBadgePreset.VN_AUTHENTIC)
        assertThat(tl.dateFormat).isEqualTo(DateFormatStyle.VN_LONG)
        assertThat(tl.showPhotoCode).isFalse()
        assertThat(tl.useLiveLocation).isTrue()

        val pinnedSample = tl.copy(
            useLiveLocation = false,
            smallPosition = "25 Nguyễn Huệ",
            largePosition = "Bến Nghé, Quận 1",
            pinnedLatitude = 10.77491,
            pinnedLongitude = 106.69242,
            coordinateFormat = CoordinateFormatStyle.DM_NSEW,
            customBrandPrefix = "My",
            customBrandSuffix = "Cam",
            customBrandSubtitle = "Verified Field",
            brandPosition = WatermarkPosition(0.20f, 0.30f, WatermarkCorner.FREE),
            logoPosition = WatermarkPosition(0.60f, 0.15f, WatermarkCorner.FREE),
        )
        val pinnedRoundTrip = WatermarkJsonCodec.decodeTemplate(WatermarkJsonCodec.encodeTemplate(pinnedSample)).getOrNull()
        assertThat(pinnedRoundTrip).isEqualTo(pinnedSample)

        // Legacy saved template JSON containing "showMiniMap": true decodes without error
        val legacyJson = """{"id":"34","name":"Thời gian","style":"TIME_LOCATION","showMiniMap":true,"showAddress":true}"""
        val decodedLegacy = WatermarkJsonCodec.decodeTemplate(legacyJson).getOrNull()
        assertThat(decodedLegacy).isNotNull()
        assertThat(decodedLegacy!!.usesLocationFeature).isTrue()

        for (template in all) {
            assertThat(template.showPhotoCode).isFalse()
            assertThat(template.coordinates).isEmpty()
            val encoded = WatermarkJsonCodec.encodeTemplate(template)
            val decoded = WatermarkJsonCodec.decodeTemplate(encoded)
            assertThat(decoded.isSuccess).isTrue()
            assertThat(decoded.getOrNull()).isEqualTo(template)
        }

        for (preset in BrandBadgePreset.entries) {
            val withPreset = tl.copy(brandBadgePreset = preset)
            val decodedPreset = WatermarkJsonCodec.decodeTemplate(WatermarkJsonCodec.encodeTemplate(withPreset)).getOrNull()
            assertThat(decodedPreset?.brandBadgePreset).isEqualTo(preset)
        }

        val bad = WatermarkJsonCodec.decodeTemplate("{ invalid json")
        assertThat(bad.isFailure).isTrue()
    }

    @Test
    fun generatePhotoCode_isDeterministicAnd14CharsAlphanumeric() {
        val c1 = generatePhotoCode(sampleNow, "34")
        val c2 = generatePhotoCode(sampleNow, "34")
        val c3 = generatePhotoCode(sampleNow + 1000L, "34")

        assertThat(c1).isEqualTo(c2)
        assertThat(c1).isNotEqualTo(c3)
        assertThat(c1).hasLength(14)
        assertThat(c1).matches("^[1-9A-HJ-NP-Z]{14}$")
    }

    @Test
    fun timeProvider_formats12hAnd24hAndDatePatterns() {
        assertThat(TimeProvider.formatTemplateClock(sampleNow, use24Hour = true, showSeconds = false, zoneId = ZoneOffset.UTC))
            .isEqualTo("14:05")
        assertThat(TimeProvider.formatTemplateClock(sampleNow, use24Hour = false, showSeconds = true, zoneId = ZoneOffset.UTC))
            .isEqualTo("02:05:00 PM")
        assertThat(
            TimeProvider.formatTemplateDate(
                sampleNow,
                pattern = DateFormatStyle.YMD_DASH.pattern,
                zoneId = ZoneOffset.UTC,
                locale = Locale.US,
            ),
        ).isEqualTo("2026-11-25")
    }
}
