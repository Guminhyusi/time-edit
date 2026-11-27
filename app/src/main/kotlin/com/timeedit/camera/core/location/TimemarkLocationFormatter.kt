/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.location

import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.domain.watermark.CoordinateFormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class FormattedLocationAddress(
    val fullAddress: String,
    val smallPosition: String,
    val largePosition: String,
)

object TimemarkLocationFormatter {

    /**
     * Formats Android Geocoder or OSM Nominatim address components cleanly:
     * - Strips leading Open Location Code / Plus Codes (e.g. "QMQ2+56G, ")
     * - Strips trailing country name and postal code suffix
     * - Builds deduped `smallPosition` (feature name / street number + road)
     * - Builds deduped `largePosition` (ward/district, city, province/adminArea)
     */
    fun formatTimemarkAddress(
        rawAddressLine: String?,
        featureName: String? = null,
        subThoroughfare: String? = null,
        thoroughfare: String? = null,
        subLocality: String? = null,
        locality: String? = null,
        adminArea: String? = null,
        countryName: String? = null,
        countryCode: String? = null,
    ): FormattedLocationAddress {
        val cleanFeature = featureName?.trim()?.takeIf { it.isNotEmpty() && !it.contains("+") }
        val cleanSubThoroughfare = subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val cleanThoroughfare = thoroughfare?.trim()?.takeIf { it.isNotEmpty() }

        val streetCombined = when {
            cleanSubThoroughfare != null && cleanThoroughfare != null -> "$cleanSubThoroughfare $cleanThoroughfare"
            cleanThoroughfare != null -> cleanThoroughfare
            cleanSubThoroughfare != null -> cleanSubThoroughfare
            else -> null
        }

        val smallParts = mutableListOf<String>()
        if (cleanFeature != null && !cleanFeature.equals(cleanSubThoroughfare, ignoreCase = true)) {
            smallParts.add(cleanFeature)
        }
        val featureCoversStreet = cleanFeature != null &&
            !cleanFeature.equals(cleanSubThoroughfare, ignoreCase = true) &&
            (streetCombined == null || cleanFeature.contains(cleanThoroughfare ?: streetCombined, ignoreCase = true))
        if (streetCombined != null && !featureCoversStreet) {
            smallParts.add(streetCombined)
        }
        val smallPosition = smallParts.joinToString(", ")

        val largeParts = mutableListOf<String>()
        fun addLargePart(part: String?) {
            val clean = part?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (largeParts.none { it.equals(clean, ignoreCase = true) }) {
                largeParts.add(clean)
            }
        }
        addLargePart(subLocality)
        addLargePart(locality)
        addLargePart(adminArea)
        val largePosition = largeParts.joinToString(", ")

        var cleanedLine = rawAddressLine?.trim().orEmpty()
        if (cleanedLine.isNotEmpty()) {
            val rawFeature = featureName?.trim().orEmpty()
            if (rawFeature.contains("+") && cleanedLine.startsWith(rawFeature)) {
                cleanedLine = cleanedLine.removePrefix(rawFeature).trimStart(',', ' ')
            } else {
                // Also strip unnamed leading Plus Code tokens like "QMQ2+56G, " or "8P48QMQ2+56, "
                cleanedLine = cleanedLine.replace(Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}[,\\s]*"), "")
            }

            // Strip trailing postal code and country suffix
            val cleanCountry = countryName?.trim().orEmpty()
            if (cleanCountry.isNotEmpty() && !countryCode.equals("CN", ignoreCase = true)) {
                cleanedLine = cleanedLine
                    .replace(Regex(",\\s*${Regex.escape(cleanCountry)}$", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+${Regex.escape(cleanCountry)}$", RegexOption.IGNORE_CASE), "")
                    .trimEnd(',', ' ')
            }
            if (countryCode.equals("VN", ignoreCase = true) || cleanCountry.contains("Việt Nam", ignoreCase = true) || cleanCountry.contains("Vietnam", ignoreCase = true)) {
                cleanedLine = cleanedLine
                    .replace(Regex(",\\s*(Việt Nam|Vietnam)$", RegexOption.IGNORE_CASE), "")
                    .trimEnd(',', ' ')
            }
            // Strip trailing 5-6 digit postal code (e.g. Nominatim VN ", 50611")
            cleanedLine = cleanedLine.replace(Regex(",\\s*\\d{5,6}$"), "").trimEnd(',', ' ')
            if (cleanCountry.isNotEmpty() && !countryCode.equals("CN", ignoreCase = true)) {
                cleanedLine = cleanedLine
                    .replace(Regex(",\\s*${Regex.escape(cleanCountry)}$", RegexOption.IGNORE_CASE), "")
                    .trimEnd(',', ' ')
            }
        }

        val fullAddress = when {
            cleanedLine.isNotBlank() -> cleanedLine
            smallPosition.isNotBlank() && largePosition.isNotBlank() -> "$smallPosition, $largePosition"
            smallPosition.isNotBlank() -> smallPosition
            else -> largePosition
        }

        val resolvedSmall = smallPosition.ifBlank {
            fullAddress.substringBefore(",").trim().ifBlank { largePosition }
        }
        val resolvedLarge = largePosition.ifBlank {
            val afterFirst = fullAddress.substringAfter(",", "").trim()
            if (afterFirst != resolvedSmall) afterFirst else ""
        }

        return FormattedLocationAddress(
            fullAddress = fullAddress,
            smallPosition = resolvedSmall,
            largePosition = resolvedLarge,
        )
    }

    /**
     * Returns true when a reverse-geocoded result only captured broad administrative divisions
     * (e.g. "Hải Vân, Đà Nẵng") and lacks street, building, or neighbourhood detail.
     */
    fun isCoarseAddress(address: FormattedLocationAddress, hasStreetInfo: Boolean): Boolean {
        if (address.fullAddress.isBlank()) return true
        if (hasStreetInfo) return false
        val segmentCount = address.fullAddress
            .split(",")
            .map { it.trim() }
            .count { it.isNotEmpty() }
        return segmentCount <= 2
    }

    /**
     * Selects whichever formatted address provides richer street/POI detail when falling back
     * between Android system Geocoder and free OSM Nominatim.
     */
    fun selectRicherAddress(
        primary: FormattedLocationAddress?,
        primaryHasStreet: Boolean,
        fallback: FormattedLocationAddress?,
        fallbackHasStreet: Boolean,
    ): FormattedLocationAddress {
        val validPrimary = primary?.takeIf { it.fullAddress.isNotBlank() }
        val validFallback = fallback?.takeIf { it.fullAddress.isNotBlank() }
        if (validPrimary == null) return validFallback ?: FormattedLocationAddress("", "", "")
        if (validFallback == null) return validPrimary
        if (fallbackHasStreet && !primaryHasStreet) return validFallback
        if (primaryHasStreet && !fallbackHasStreet) return validPrimary

        val primarySegments = validPrimary.fullAddress.split(",").count { it.isNotBlank() }
        val fallbackSegments = validFallback.fullAddress.split(",").count { it.isNotBlank() }
        return if (fallbackSegments > primarySegments) validFallback else validPrimary
    }

    fun formatCoordinates(
        lat: Double,
        lng: Double,
        style: CoordinateFormatStyle = CoordinateFormatStyle.DECIMAL_NSEW,
    ): String {
        val latDir = if (lat >= 0.0) "N" else "S"
        val lngDir = if (lng >= 0.0) "E" else "W"
        val absLat = abs(lat)
        val absLng = abs(lng)

        return when (style) {
            CoordinateFormatStyle.DECIMAL_NSEW ->
                String.format(Locale.US, "%.5f°%s, %.5f°%s", absLat, latDir, absLng, lngDir)

            CoordinateFormatStyle.DM_NSEW -> {
                val latDeg = absLat.toInt()
                val latMin = (absLat - latDeg) * 60.0
                val lngDeg = absLng.toInt()
                val lngMin = (absLng - lngDeg) * 60.0
                String.format(Locale.US, "%d°%.4f'%s, %d°%.4f'%s", latDeg, latMin, latDir, lngDeg, lngMin, lngDir)
            }

            CoordinateFormatStyle.DECIMAL_SIGNED ->
                String.format(Locale.US, "%.5f, %.5f", lat, lng)
        }
    }

    /**
     * Formats azimuth degrees into 8 cardinal/intercardinal compass sectors,
     * returning e.g. "N 15°" or "SE 125°".
     */
    fun formatCompass(azimuthDegrees: Float): String {
        val normalized = ((azimuthDegrees % 360f) + 360f) % 360f
        val dir = when {
            normalized >= 337.5f || normalized < 22.5f -> "N"
            normalized < 67.5f -> "NE"
            normalized < 112.5f -> "E"
            normalized < 157.5f -> "SE"
            normalized < 202.5f -> "S"
            normalized < 247.5f -> "SW"
            normalized < 292.5f -> "W"
            else -> "NW"
        }
        return "$dir ${normalized.roundToInt() % 360}°"
    }

    fun formatAltitude(meters: Double): String = "${meters.roundToInt()}m"

    fun formatWeather(
        tempCelsius: Double,
        weatherCode: Int,
        includeCondition: Boolean = true,
        language: AppLanguage = AppLanguage.VI,
    ): String {
        val tempPart = "${tempCelsius.roundToInt()}°C"
        if (!includeCondition) return tempPart
        val desc = wmoCodeDescription(weatherCode, language)
        return "$tempPart $desc"
    }

    fun wmoCodeToVietnamese(code: Int): String = wmoCodeDescription(code, AppLanguage.VI)

    fun wmoCodeDescription(code: Int, language: AppLanguage): String = when (language) {
        AppLanguage.VI -> when (code) {
            0 -> "Quang đãng"
            1 -> "Ít mây"
            2 -> "Có mây"
            3 -> "Nhiều mây"
            45, 48 -> "Sương mù"
            51, 53, 55, 56, 57 -> "Mưa phùn"
            61, 63, 65, 66, 67 -> "Mưa"
            71, 73, 75, 77 -> "Tuyết"
            80, 81, 82 -> "Mưa rào"
            85, 86 -> "Mưa tuyết"
            95, 96, 99 -> "Dông bão"
            else -> "Có mây"
        }
        AppLanguage.EN -> when (code) {
            0 -> "Clear"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Cloudy"
        }
        AppLanguage.ZH_CN -> when (code) {
            0 -> "晴"
            1 -> "少云"
            2 -> "多云"
            3 -> "阴"
            45, 48 -> "雾"
            51, 53, 55, 56, 57 -> "毛毛雨"
            61, 63, 65, 66, 67 -> "雨"
            71, 73, 75, 77 -> "雪"
            80, 81, 82 -> "阵雨"
            85, 86 -> "雨夹雪"
            95, 96, 99 -> "雷暴"
            else -> "多云"
        }
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
