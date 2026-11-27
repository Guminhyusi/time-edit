/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.location

import com.google.common.truth.Truth.assertThat
import com.timeedit.camera.domain.watermark.CoordinateFormatStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TimemarkLocationFormatterTest {

    @Test
    fun formatTimemarkAddress_stripsPlusCodeAndCountryAndBuildsSmallLarge() {
        val result = TimemarkLocationFormatter.formatTimemarkAddress(
            rawAddressLine = "QMQ2+56G, 25 Nguyễn Huệ, Bến Nghé, Quận 1, Hồ Chí Minh, Việt Nam",
            featureName = "QMQ2+56G",
            subThoroughfare = "25",
            thoroughfare = "Đường Nguyễn Huệ",
            subLocality = "Bến Nghé",
            locality = "Quận 1",
            adminArea = "Hồ Chí Minh",
            countryName = "Việt Nam",
            countryCode = "VN",
        )

        assertThat(result.fullAddress).isEqualTo("25 Nguyễn Huệ, Bến Nghé, Quận 1, Hồ Chí Minh")
        assertThat(result.smallPosition).isEqualTo("25 Đường Nguyễn Huệ")
        assertThat(result.largePosition).isEqualTo("Bến Nghé, Quận 1, Hồ Chí Minh")
    }

    @Test
    fun formatTimemarkAddress_dedupesAndFallsBackWhenRawLineMissing() {
        val result = TimemarkLocationFormatter.formatTimemarkAddress(
            rawAddressLine = "",
            featureName = "Công viên Tao Đàn",
            subThoroughfare = null,
            thoroughfare = "Trương Định",
            subLocality = "Bến Thành",
            locality = "Quận 1",
            adminArea = "Quận 1",
            countryName = "Việt Nam",
            countryCode = "VN",
        )

        assertThat(result.smallPosition).isEqualTo("Công viên Tao Đàn, Trương Định")
        assertThat(result.largePosition).isEqualTo("Bến Thành, Quận 1")
        assertThat(result.fullAddress).isEqualTo("Công viên Tao Đàn, Trương Định, Bến Thành, Quận 1")
    }

    @Test
    fun formatCoordinates_formatsAllThreeStylesAccurately() {
        val dec = TimemarkLocationFormatter.formatCoordinates(10.77491, 106.69242, CoordinateFormatStyle.DECIMAL_NSEW)
        assertThat(dec).isEqualTo("10.77491°N, 106.69242°E")

        val dm = TimemarkLocationFormatter.formatCoordinates(-33.8688, -151.2093, CoordinateFormatStyle.DM_NSEW)
        assertThat(dm).isEqualTo("33°52.1280'S, 151°12.5580'W")

        val signed = TimemarkLocationFormatter.formatCoordinates(10.77491, 106.69242, CoordinateFormatStyle.DECIMAL_SIGNED)
        assertThat(signed).isEqualTo("10.77491, 106.69242")
    }

    @Test
    fun formatCompass_coversAll8DirectionsWithoutDeadZones() {
        assertThat(TimemarkLocationFormatter.formatCompass(0f)).isEqualTo("N 0°")
        assertThat(TimemarkLocationFormatter.formatCompass(22.5f)).isEqualTo("NE 23°")
        assertThat(TimemarkLocationFormatter.formatCompass(90f)).isEqualTo("E 90°")
        assertThat(TimemarkLocationFormatter.formatCompass(135f)).isEqualTo("SE 135°")
        assertThat(TimemarkLocationFormatter.formatCompass(180f)).isEqualTo("S 180°")
        assertThat(TimemarkLocationFormatter.formatCompass(225f)).isEqualTo("SW 225°")
        assertThat(TimemarkLocationFormatter.formatCompass(270f)).isEqualTo("W 270°")
        assertThat(TimemarkLocationFormatter.formatCompass(315f)).isEqualTo("NW 315°")
        assertThat(TimemarkLocationFormatter.formatCompass(359.6f)).isEqualTo("N 0°")
    }

    @Test
    fun formatAltitudeAndWeather_produceExpectedVietnameseStrings() {
        assertThat(TimemarkLocationFormatter.formatAltitude(15.6)).isEqualTo("16m")
        assertThat(TimemarkLocationFormatter.formatWeather(28.4, 2, includeCondition = true)).isEqualTo("28°C Có mây")
        assertThat(TimemarkLocationFormatter.formatWeather(28.4, 61, includeCondition = false)).isEqualTo("28°C")
        assertThat(TimemarkLocationFormatter.formatWeather(31.2, 95, includeCondition = true)).isEqualTo("31°C Dông bão")
    }

    @Test
    fun formatTimemarkAddress_stripsTrailingPostalCodeAndCountryFromNominatim() {
        val result = TimemarkLocationFormatter.formatTimemarkAddress(
            rawAddressLine = "Đường Trung Lập 7, Tổ dân phố Bàu Trâm 2, Phường Hải Vân, Thành phố Đà Nẵng, 50611, Việt Nam",
            featureName = "Đường Trung Lập 7",
            thoroughfare = "Đường Trung Lập 7",
            subLocality = "Phường Hải Vân",
            locality = null,
            adminArea = "Thành phố Đà Nẵng",
            countryName = "Việt Nam",
            countryCode = "VN",
        )

        assertThat(result.fullAddress).isEqualTo("Đường Trung Lập 7, Tổ dân phố Bàu Trâm 2, Phường Hải Vân, Thành phố Đà Nẵng")
        assertThat(result.smallPosition).isEqualTo("Đường Trung Lập 7")
        assertThat(result.largePosition).isEqualTo("Phường Hải Vân, Thành phố Đà Nẵng")
    }

    @Test
    fun selectRicherAddress_upgradesCoarseWardCityWithStreetDetail() {
        val coarseSystem = FormattedLocationAddress(
            fullAddress = "Hải Vân, Đà Nẵng",
            smallPosition = "Hải Vân",
            largePosition = "Đà Nẵng",
        )
        val detailedNominatim = FormattedLocationAddress(
            fullAddress = "Đường Trung Lập 7, Tổ dân phố Bàu Trâm 2, Phường Hải Vân, Thành phố Đà Nẵng",
            smallPosition = "Đường Trung Lập 7",
            largePosition = "Phường Hải Vân, Thành phố Đà Nẵng",
        )

        assertThat(TimemarkLocationFormatter.isCoarseAddress(coarseSystem, hasStreetInfo = false)).isTrue()
        assertThat(TimemarkLocationFormatter.isCoarseAddress(detailedNominatim, hasStreetInfo = true)).isFalse()

        val upgraded = TimemarkLocationFormatter.selectRicherAddress(
            primary = coarseSystem,
            primaryHasStreet = false,
            fallback = detailedNominatim,
            fallbackHasStreet = true,
        )
        assertThat(upgraded).isEqualTo(detailedNominatim)

        val preservedSystem = TimemarkLocationFormatter.selectRicherAddress(
            primary = detailedNominatim,
            primaryHasStreet = true,
            fallback = coarseSystem,
            fallbackHasStreet = false,
        )
        assertThat(preservedSystem).isEqualTo(detailedNominatim)
    }
}
