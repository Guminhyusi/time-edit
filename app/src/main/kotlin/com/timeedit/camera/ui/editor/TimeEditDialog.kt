/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeMode
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive

private enum class TimeInputStyle {
    WHEEL,
    MANUAL,
}

@Composable
fun TimeEditDialog(
    initialConfig: TimeConfig,
    photoOriginalEpochMillis: Long? = null,
    onApply: (TimeConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val zoneId = remember { ZoneId.systemDefault() }
    var selectedMode by remember { mutableStateOf(initialConfig.mode) }
    var inputStyle by remember { mutableStateOf(TimeInputStyle.WHEEL) }

    val seedZdt = remember {
        val baseMillis = initialConfig.fixedEpochMillis
            ?: (System.currentTimeMillis() + initialConfig.offsetMillis)
        Instant.ofEpochMilli(baseMillis).atZone(zoneId).toLocalDateTime()
    }

    // Source-of-truth Fixed date/time values
    var year by remember { mutableIntStateOf(seedZdt.year.coerceIn(2000, 2099)) }
    var month by remember { mutableIntStateOf(seedZdt.monthValue.coerceIn(1, 12)) }
    var day by remember { mutableIntStateOf(seedZdt.dayOfMonth) }
    var hour by remember { mutableIntStateOf(seedZdt.hour) }
    var minute by remember { mutableIntStateOf(seedZdt.minute) }
    var second by remember { mutableIntStateOf(seedZdt.second) }

    // Manual text drafts for Fixed mode
    var yearInput by remember { mutableStateOf(year.toString()) }
    var monthInput by remember { mutableStateOf("%02d".format(month)) }
    var dayInput by remember { mutableStateOf("%02d".format(day)) }
    var hourInput by remember { mutableStateOf("%02d".format(hour)) }
    var minuteInput by remember { mutableStateOf("%02d".format(minute)) }
    var secondInput by remember { mutableStateOf("%02d".format(second)) }
    var manualError by remember { mutableStateOf<String?>(null) }

    fun syncManualFromFixed(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int) {
        yearInput = y.toString()
        monthInput = "%02d".format(m)
        dayInput = "%02d".format(d)
        hourInput = "%02d".format(h)
        minuteInput = "%02d".format(min)
        secondInput = "%02d".format(s)
        manualError = null
    }

    // Source-of-truth Offset components
    val seedOffsetMins = initialConfig.offsetMillis / 60_000L
    val seedAbs = abs(seedOffsetMins)
    var isOffsetNegative by remember { mutableStateOf(seedOffsetMins < 0L) }
    var offsetDays by remember { mutableIntStateOf((seedAbs / 1440L).toInt().coerceIn(0, 365)) }
    var offsetHours by remember { mutableIntStateOf(((seedAbs % 1440L) / 60L).toInt().coerceIn(0, 23)) }
    var offsetMins by remember { mutableIntStateOf((seedAbs % 60L).toInt().coerceIn(0, 59)) }

    var offDaysInput by remember { mutableStateOf(offsetDays.toString()) }
    var offHoursInput by remember { mutableStateOf(offsetHours.toString()) }
    var offMinsInput by remember { mutableStateOf(offsetMins.toString()) }

    fun syncManualFromOffset(d: Int, h: Int, m: Int) {
        offDaysInput = d.toString()
        offHoursInput = h.toString()
        offMinsInput = m.toString()
        manualError = null
    }

    var tickerNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            tickerNow = System.currentTimeMillis()
            delay(500L)
        }
    }

    val maxDaysInMonth = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    LaunchedEffect(maxDaysInMonth) {
        if (day > maxDaysInMonth) {
            day = maxDaysInMonth
            dayInput = "%02d".format(day)
        }
    }

    val safeFixedMillis = remember(year, month, day, hour, minute, second) {
        val clampedDay = day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
        LocalDateTime.of(year, month, clampedDay, hour, minute, second)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    val signedOffsetMinutes = remember(isOffsetNegative, offsetDays, offsetHours, offsetMins) {
        val total = offsetDays.toLong() * 1440L + offsetHours.toLong() * 60L + offsetMins.toLong()
        if (isOffsetNegative) -total else total
    }

    val draftConfig = when (selectedMode) {
        TimeMode.LIVE -> TimeConfig(mode = TimeMode.LIVE)
        TimeMode.FIXED -> TimeConfig(mode = TimeMode.FIXED, fixedEpochMillis = safeFixedMillis)
        TimeMode.OFFSET -> TimeConfig(mode = TimeMode.OFFSET, offsetMillis = signedOffsetMinutes * 60_000L)
    }
    val previewMillis = TimeProvider.resolveEpochMillis(tickerNow, draftConfig)

    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(20.dp),
            opaqueModal = true,
            darkColor = colors.glassDialogFill,
            darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.7f)),
            modifier = Modifier.widthIn(max = 370.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Text(
                    text = strings.timeDialogTitle,
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Live Preview Box
                val previewAccent = if (selectedMode == TimeMode.LIVE) colors.accent else colors.amber
                LiquidGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    selected = selectedMode != TimeMode.LIVE,
                    elevated = false,
                    darkColor = colors.surface,
                    darkBorder = BorderStroke(1.dp, previewAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = TimeProvider.formatClock(previewMillis),
                            color = colors.textPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = TimeProvider.formatDate(previewMillis, locale = strings.language.toLocale()),
                            color = previewAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Tabs: Live | Fixed | Offset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                manualError = null
                            },
                            label = {
                                Text(
                                    text = when (mode) {
                                        TimeMode.LIVE -> strings.timeModeLive
                                        TimeMode.FIXED -> strings.timeModeFixed
                                        TimeMode.OFFSET -> strings.timeModeOffset
                                    },
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentContainer,
                                selectedLabelColor = colors.accentSoft,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (photoOriginalEpochMillis != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val origDateText = remember(photoOriginalEpochMillis, strings.language) {
                        val d = TimeProvider.formatTemplateDate(photoOriginalEpochMillis, "dd/MM/yyyy", locale = strings.language.toLocale())
                        val t = TimeProvider.formatTemplateClock(photoOriginalEpochMillis, use24Hour = true, showSeconds = false)
                        "$d $t"
                    }
                    OutlinedButton(
                        onClick = {
                            val origDt = Instant.ofEpochMilli(photoOriginalEpochMillis)
                                .atZone(zoneId)
                                .toLocalDateTime()
                            selectedMode = TimeMode.FIXED
                            year = origDt.year.coerceIn(2000, 2099)
                            month = origDt.monthValue.coerceIn(1, 12)
                            day = origDt.dayOfMonth
                            hour = origDt.hour
                            minute = origDt.minute
                            second = origDt.second
                            syncManualFromFixed(year, month, day, hour, minute, second)
                        },
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = strings.usePhotoOriginalTime(origDateText),
                            color = colors.accentSoft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (selectedMode != TimeMode.LIVE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.inputStyleLabel, color = colors.textSecondary, fontSize = 12.sp)
                        FilterChip(
                            selected = inputStyle == TimeInputStyle.WHEEL,
                            onClick = { inputStyle = TimeInputStyle.WHEEL },
                            label = { Text(strings.inputWheel, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentContainer,
                                selectedLabelColor = colors.accent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = inputStyle == TimeInputStyle.MANUAL,
                            onClick = {
                                if (selectedMode == TimeMode.FIXED) {
                                    syncManualFromFixed(year, month, day, hour, minute, second)
                                } else {
                                    syncManualFromOffset(offsetDays, offsetHours, offsetMins)
                                }
                                inputStyle = TimeInputStyle.MANUAL
                            },
                            label = { Text(strings.inputManual, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentContainer,
                                selectedLabelColor = colors.accent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedMode) {
                    TimeMode.LIVE -> {
                        Text(
                            text = strings.liveModeHint,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }

                    TimeMode.FIXED -> {
                        if (inputStyle == TimeInputStyle.WHEEL) {
                            Text(strings.wheelDateLabel, color = colors.textSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WheelPickerColumn(
                                    label = strings.dayLabel,
                                    items = (1..maxDaysInMonth).toList(),
                                    selectedItem = day.coerceIn(1, maxDaysInMonth),
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        day = it
                                        dayInput = "%02d".format(it)
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.monthLabel,
                                    items = (1..12).toList(),
                                    selectedItem = month,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        month = it
                                        monthInput = "%02d".format(it)
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.yearLabel,
                                    items = (2000..2099).toList(),
                                    selectedItem = year,
                                    formatItem = { it.toString() },
                                    onItemSelected = {
                                        year = it
                                        yearInput = it.toString()
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1.2f),
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(strings.wheelTimeLabel, color = colors.textSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WheelPickerColumn(
                                    label = strings.hourLabel,
                                    items = (0..23).toList(),
                                    selectedItem = hour,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        hour = it
                                        hourInput = "%02d".format(it)
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.minuteLabel,
                                    items = (0..59).toList(),
                                    selectedItem = minute,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        minute = it
                                        minuteInput = "%02d".format(it)
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.secondLabel,
                                    items = (0..59).toList(),
                                    selectedItem = second,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        second = it
                                        secondInput = "%02d".format(it)
                                    },
                                    onCenterClick = {
                                        syncManualFromFixed(year, month, day, hour, minute, second)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            // Direct Manual Numeric Input Mode
                            Text(strings.manualDateLabel, color = colors.textSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ManualNumberBox(
                                    label = strings.dayLabel,
                                    value = dayInput,
                                    maxLen = 2,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        dayInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 1..maxDaysInMonth) {
                                            day = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidDayRange(maxDaysInMonth)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.monthLabel,
                                    value = monthInput,
                                    maxLen = 2,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        monthInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 1..12) {
                                            month = parsed
                                            val nextMax = YearMonth.of(year, parsed).lengthOfMonth()
                                            if (day > nextMax) {
                                                day = nextMax
                                                dayInput = "%02d".format(day)
                                            }
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidMonthRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.yearLabel,
                                    value = yearInput,
                                    maxLen = 4,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(4)
                                        yearInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 2000..2099) {
                                            year = parsed
                                            manualError = null
                                        } else if (digits.length == 4) {
                                            manualError = strings.invalidYearRange
                                        }
                                    },
                                    modifier = Modifier.weight(1.25f),
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(strings.manualTimeLabel, color = colors.textSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ManualNumberBox(
                                    label = strings.hourLabel,
                                    value = hourInput,
                                    maxLen = 2,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        hourInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..23) {
                                            hour = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidHourRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.minuteLabel,
                                    value = minuteInput,
                                    maxLen = 2,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        minuteInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..59) {
                                            minute = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidMinuteRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.secondLabel,
                                    value = secondInput,
                                    maxLen = 2,
                                    imeAction = ImeAction.Done,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        secondInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..59) {
                                            second = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidSecondRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (manualError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = manualError.orEmpty(), color = colors.error, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val yesterday = Instant.ofEpochMilli(safeFixedMillis - 86_400_000L)
                                        .atZone(zoneId).toLocalDateTime()
                                    year = yesterday.year.coerceIn(2000, 2099)
                                    month = yesterday.monthValue
                                    day = yesterday.dayOfMonth
                                    syncManualFromFixed(year, month, day, hour, minute, second)
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(strings.yesterday, fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = {
                                    val nowDt = LocalDateTime.now(zoneId)
                                    year = nowDt.year.coerceIn(2000, 2099)
                                    month = nowDt.monthValue
                                    day = nowDt.dayOfMonth
                                    hour = nowDt.hour
                                    minute = nowDt.minute
                                    second = nowDt.second
                                    syncManualFromFixed(year, month, day, hour, minute, second)
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(strings.toNow, fontSize = 12.sp) }
                        }
                    }

                    TimeMode.OFFSET -> {
                        val signLabel = if (isOffsetNegative) "-" else "+"
                        Text(
                            text = strings.offsetSummary(signLabel, offsetDays, offsetHours, offsetMins),
                            color = colors.accentSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !isOffsetNegative,
                                onClick = { isOffsetNegative = false },
                                label = { Text(strings.offsetAhead, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = isOffsetNegative,
                                onClick = { isOffsetNegative = true },
                                label = { Text(strings.offsetBehind, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (inputStyle == TimeInputStyle.WHEEL) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WheelPickerColumn(
                                    label = strings.dayLabel,
                                    items = (0..365).toList(),
                                    selectedItem = offsetDays,
                                    formatItem = { it.toString() },
                                    onItemSelected = {
                                        offsetDays = it
                                        offDaysInput = it.toString()
                                    },
                                    onCenterClick = {
                                        syncManualFromOffset(offsetDays, offsetHours, offsetMins)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.hourLabel,
                                    items = (0..23).toList(),
                                    selectedItem = offsetHours,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        offsetHours = it
                                        offHoursInput = it.toString()
                                    },
                                    onCenterClick = {
                                        syncManualFromOffset(offsetDays, offsetHours, offsetMins)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                WheelPickerColumn(
                                    label = strings.minuteLabel,
                                    items = (0..59).toList(),
                                    selectedItem = offsetMins,
                                    formatItem = { "%02d".format(it) },
                                    onItemSelected = {
                                        offsetMins = it
                                        offMinsInput = it.toString()
                                    },
                                    onCenterClick = {
                                        syncManualFromOffset(offsetDays, offsetHours, offsetMins)
                                        inputStyle = TimeInputStyle.MANUAL
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ManualNumberBox(
                                    label = strings.dayLabel,
                                    value = offDaysInput,
                                    maxLen = 3,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(3)
                                        offDaysInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..365) {
                                            offsetDays = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidOffsetDays
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.hourLabel,
                                    value = offHoursInput,
                                    maxLen = 2,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        offHoursInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..23) {
                                            offsetHours = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidHourRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                ManualNumberBox(
                                    label = strings.minuteLabel,
                                    value = offMinsInput,
                                    maxLen = 2,
                                    imeAction = ImeAction.Done,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(2)
                                        offMinsInput = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed in 0..59) {
                                            offsetMins = parsed
                                            manualError = null
                                        } else if (digits.isNotEmpty()) {
                                            manualError = strings.invalidMinuteRange
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (manualError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = manualError.orEmpty(), color = colors.error, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                isOffsetNegative = false
                                offsetDays = 0
                                offsetHours = 0
                                offsetMins = 0
                                syncManualFromOffset(0, 0, 0)
                            },
                        ) {
                            Text(strings.resetOffsetZero)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(draftConfig) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    ) {
                        Text(strings.apply, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelPickerColumn(
    label: String,
    items: List<Int>,
    selectedItem: Int,
    formatItem: (Int) -> String,
    onItemSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 36.dp
    val selectedIndex = remember(items, selectedItem) {
        items.indexOf(selectedItem).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val latestOnSelected by rememberUpdatedState(onItemSelected)
    val latestItems by rememberUpdatedState(items)
    val latestSelectedItem by rememberUpdatedState(selectedItem)

    val centerIndex by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) {
                selectedIndex
            } else {
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: selectedIndex
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centerIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .filter { idx -> idx in latestItems.indices }
            .collect { idx ->
                val value = latestItems[idx]
                if (value != latestSelectedItem) {
                    latestOnSelected(value)
                }
            }
    }

    val colors = LocalTimeEditColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 3)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface),
        ) {
            // Highlight band on center row
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(colors.accentContainer.copy(alpha = 0.72f))
                    .clickable(onClick = onCenterClick),
            ) {
                HorizontalDivider(
                    color = colors.accent.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                HorizontalDivider(
                    color = colors.accent.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    count = items.size,
                    key = { idx -> items[idx] },
                ) { idx ->
                    val isCenter = idx == centerIndex
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                    ) {
                        Text(
                            text = formatItem(items[idx]),
                            color = if (isCenter) colors.accentSoft else colors.textSecondary.copy(alpha = 0.45f),
                            fontSize = if (isCenter) 18.sp else 14.sp,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualNumberBox(
    label: String,
    value: String,
    maxLen: Int,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTimeEditColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(3.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLen + 1) onValueChange(it) },
            singleLine = true,
            textStyle = TextStyle(
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.borderStroke.copy(alpha = 0.6f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
