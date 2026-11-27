/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
enum class TimeMode {
    LIVE,
    FIXED,
    OFFSET,
}

@Serializable
data class TimeConfig(
    val mode: TimeMode = TimeMode.LIVE,
    val fixedEpochMillis: Long? = null,
    val offsetMillis: Long = 0L,
)

object TimeProvider {
    val VIETNAM_LOCALE: Locale = Locale.forLanguageTag("vi-VN")
    val CHINESE_LOCALE: Locale = Locale.SIMPLIFIED_CHINESE
    private val clock24SecFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val clock24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val clock12SecFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.US)
    private val clock12Formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    private val hourMin24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val hourMin12Formatter = DateTimeFormatter.ofPattern("hh:mm", Locale.US)
    private val secondsFormatter = DateTimeFormatter.ofPattern("ss", Locale.US)
    private val amPmFormatter = DateTimeFormatter.ofPattern("a", Locale.US)
    private val weekdayShortFormatter = DateTimeFormatter.ofPattern("EEE")
    private val weekdayFullFormatter = DateTimeFormatter.ofPattern("EEEE")
    private val exifFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    fun resolveEpochMillis(nowMillis: Long, config: TimeConfig): Long =
        when (config.mode) {
            TimeMode.LIVE -> nowMillis
            TimeMode.FIXED -> config.fixedEpochMillis ?: nowMillis
            TimeMode.OFFSET -> nowMillis + config.offsetMillis
        }

    fun formatClock(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = formatTemplateClock(epochMillis, use24Hour = true, showSeconds = true, zoneId = zoneId)

    fun formatTemplateClock(
        epochMillis: Long,
        use24Hour: Boolean = true,
        showSeconds: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = when {
            use24Hour && showSeconds -> clock24SecFormatter
            use24Hour && !showSeconds -> clock24Formatter
            !use24Hour && showSeconds -> clock12SecFormatter
            else -> clock12Formatter
        }
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatTimeHourMinute(
        epochMillis: Long,
        use24Hour: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = if (use24Hour) hourMin24Formatter else hourMin12Formatter
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatSecondsOnly(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(secondsFormatter)

    fun formatAmPm(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(amPmFormatter).uppercase(Locale.US)

    fun formatWeekday(
        epochMillis: Long,
        full: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = VIETNAM_LOCALE,
    ): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        if (locale.language.equals("vi", ignoreCase = true)) {
            return when (zoned.dayOfWeek) {
                DayOfWeek.MONDAY -> if (full) "Thứ Hai" else "T2"
                DayOfWeek.TUESDAY -> if (full) "Thứ Ba" else "T3"
                DayOfWeek.WEDNESDAY -> if (full) "Thứ Tư" else "T4"
                DayOfWeek.THURSDAY -> if (full) "Thứ Năm" else "T5"
                DayOfWeek.FRIDAY -> if (full) "Thứ Sáu" else "T6"
                DayOfWeek.SATURDAY -> if (full) "Thứ Bảy" else "T7"
                DayOfWeek.SUNDAY -> if (full) "Chủ Nhật" else "CN"
            }
        }
        if (locale.language.equals("zh", ignoreCase = true)) {
            return when (zoned.dayOfWeek) {
                DayOfWeek.MONDAY -> if (full) "星期一" else "周一"
                DayOfWeek.TUESDAY -> if (full) "星期二" else "周二"
                DayOfWeek.WEDNESDAY -> if (full) "星期三" else "周三"
                DayOfWeek.THURSDAY -> if (full) "星期四" else "周四"
                DayOfWeek.FRIDAY -> if (full) "星期五" else "周五"
                DayOfWeek.SATURDAY -> if (full) "星期六" else "周六"
                DayOfWeek.SUNDAY -> if (full) "星期日" else "周日"
            }
        }
        val fmt = (if (full) weekdayFullFormatter else weekdayShortFormatter).withLocale(locale)
        return zoned.format(fmt)
    }

    fun formatDateOnly(
        epochMillis: Long,
        pattern: String = "dd 'Tháng' M,yyyy",
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = VIETNAM_LOCALE,
    ): String = formatTemplateDate(epochMillis, pattern = pattern, zoneId = zoneId, locale = locale)

    fun formatDate(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = VIETNAM_LOCALE,
    ): String {
        val pattern = when {
            locale.language.equals("zh", ignoreCase = true) -> "yyyy'年'MM'月'dd'日' EEEE"
            else -> "EEEE, yyyy/MM/dd"
        }
        return formatTemplateDate(epochMillis, pattern = pattern, zoneId = zoneId, locale = locale)
    }

    fun formatTemplateDate(
        epochMillis: Long,
        pattern: String = "dd 'Tháng' M,yyyy",
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = VIETNAM_LOCALE,
    ): String {
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(formatter)
    }

    fun formatExifDateTime(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(exifFormatter)
}
