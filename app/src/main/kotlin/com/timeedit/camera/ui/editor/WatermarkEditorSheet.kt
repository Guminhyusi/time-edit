/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeMode
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.CoordinateFormatStyle
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.LogoPlacement
import com.timeedit.camera.domain.watermark.WatermarkCorner
import com.timeedit.camera.domain.watermark.WatermarkField
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.engine.renderer.WatermarkCanvasRenderer
import com.timeedit.camera.ui.camera.parseComposeColor
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import com.timeedit.camera.ui.theme.TimeEditPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private val templateThumbCache = LruCache<String, ImageBitmap>(48)

private val ACCENT_PALETTE = listOf(
    "#FFC233",
    "#FC9216",
    "#006CFF",
    "#0527AF",
    "#004B92",
    "#74DDA2",
    "#391A13",
    "#E60000",
    "#FFFFFF",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkEditorSheet(
    activeTemplate: WatermarkTemplate,
    customPresets: List<WatermarkTemplate>,
    timeConfig: TimeConfig,
    epochMillisFlow: StateFlow<Long>,
    onSelectTemplate: (WatermarkTemplate) -> Unit,
    onUpdateTemplate: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onUpdateCoordinateFormat: (CoordinateFormatStyle) -> Unit,
    onResetTemplateDefault: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenTimeDialog: () -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf(activeTemplate.name) }

    var showAddFieldDialog by remember { mutableStateOf(false) }
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUpdateTemplate { it.copy(logoUri = uri.toString()) }
        }
    }

    val customBrandPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onUpdateTemplate {
                it.copy(
                    brandBadgePreset = BrandBadgePreset.CUSTOM_IMAGE,
                    customBrandImageUri = uri.toString(),
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.glassDialogFill,
        scrimColor = Color.Black.copy(alpha = 0.25f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 490.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = colors.accent,
            ) {
                listOf(strings.wmTabTemplates, strings.wmTabContent, strings.wmTabStyle).forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (selectedTab) {
                    0 -> {
                        TemplatesTab(
                            activeTemplate = activeTemplate,
                            customPresets = customPresets,
                            epochMillisSnapshot = remember(epochMillisFlow) { epochMillisFlow.value },
                            onSelectTemplate = onSelectTemplate,
                            onOpenSavePreset = {
                                presetNameInput = activeTemplate.name
                                showSavePresetDialog = true
                            },
                            onDeletePreset = onDeletePreset,
                        )
                    }

                    1 -> {
                        val epochMillis by epochMillisFlow.collectAsStateWithLifecycle()
                        FieldsTab(
                            activeTemplate = activeTemplate,
                            timeConfig = timeConfig,
                            epochMillis = epochMillis,
                            onUpdateTemplate = onUpdateTemplate,
                            onUpdateCoordinateFormat = onUpdateCoordinateFormat,
                            onOpenLocationPicker = onOpenLocationPicker,
                            onOpenTimeDialog = onOpenTimeDialog,
                            onPickLogo = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onClearLogo = { onUpdateTemplate { it.copy(logoUri = null) } },
                            onOpenAddCustomField = {
                                newFieldLabel = ""
                                newFieldValue = ""
                                showAddFieldDialog = true
                            },
                        )
                    }

                    2 -> {
                        StyleTab(
                            activeTemplate = activeTemplate,
                            onUpdateTemplate = onUpdateTemplate,
                            onPickCustomBrand = {
                                customBrandPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onResetDefault = onResetTemplateDefault,
                        )
                    }
                }
            }
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = colors.glassDialogFill,
            title = { Text(strings.wmSavePresetTitle, color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text(strings.wmPresetNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePreset(presetNameInput)
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(strings.apply) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(strings.cancel) }
            },
        )
    }

    if (showAddFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            containerColor = colors.glassDialogFill,
            title = { Text(strings.wmAddCustomField, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFieldLabel,
                        onValueChange = { newFieldLabel = it },
                        label = { Text(strings.wmFieldTitle) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newFieldValue,
                        onValueChange = { newFieldValue = it },
                        label = { Text(strings.wmTabContent) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val label = newFieldLabel.trim()
                        if (label.isNotEmpty()) {
                            val field = WatermarkField(
                                id = "cf_${System.currentTimeMillis()}",
                                label = label,
                                value = newFieldValue.trim(),
                                enabled = true,
                                isCustom = true,
                            )
                            onUpdateTemplate { it.copy(customFields = it.customFields + field) }
                        }
                        showAddFieldDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) { Text(strings.apply) }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun TemplatesTab(
    activeTemplate: WatermarkTemplate,
    customPresets: List<WatermarkTemplate>,
    epochMillisSnapshot: Long,
    onSelectTemplate: (WatermarkTemplate) -> Unit,
    onOpenSavePreset: () -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val builtIns = remember(strings.language) { DefaultWatermarkTemplates.all(strings.language) }

    Text("${strings.wmBuiltInTemplates} (${builtIns.size})", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(
            items = builtIns,
            key = { it.id },
            contentType = { "builtin_template" },
        ) { item ->
            val selected = activeTemplate.id == item.id
            val accent = parseComposeColor(item.theme.accentColorHex, colors.accent)
            val cacheKey = "${item.id}_${strings.language.code}"
            val previewThumb by produceState<ImageBitmap?>(
                initialValue = templateThumbCache.get(cacheKey),
                item.id,
                strings.language,
            ) {
                if (value != null) return@produceState
                value = withContext(Dispatchers.Default) {
                    runCatching {
                        val w = 220
                        val h = 126
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                        val c = Canvas(bmp)
                        c.drawColor(AndroidColor.parseColor("#545D68"))
                        val sample = item.copy(
                            address = when (strings.language) {
                                AppLanguage.VI -> "104 Tô Hiệu, Đà Nẵng"
                                AppLanguage.EN -> "104 Market St, San Francisco"
                                AppLanguage.ZH_CN -> "北京市朝阳区建国路88号"
                            },
                            smallPosition = when (strings.language) {
                                AppLanguage.VI -> "104, Tô Hiệu"
                                AppLanguage.EN -> "Market St"
                                AppLanguage.ZH_CN -> "建国路88号"
                            },
                            largePosition = when (strings.language) {
                                AppLanguage.VI -> "Đà Nẵng"
                                AppLanguage.EN -> "San Francisco"
                                AppLanguage.ZH_CN -> "北京市"
                            },
                            coordinates = "16.06058°N, 108.16726°E",
                            compass = "N 355°",
                            weather = "28°C",
                            brandBadgePreset = BrandBadgePreset.OFF,
                            showPhotoCode = false,
                        )
                        WatermarkCanvasRenderer.drawWatermarkOnCanvas(
                            context = context,
                            canvas = c,
                            photoWidth = w,
                            photoHeight = h,
                            template = sample,
                            epochMillis = epochMillisSnapshot,
                            isPreview = false,
                            language = strings.language,
                        )
                        val rendered = bmp.asImageBitmap()
                        templateThumbCache.put(cacheKey, rendered)
                        rendered
                    }.getOrNull()
                }
            }
            LiquidGlassSurface(
                shape = RoundedCornerShape(12.dp),
                selected = selected,
                elevated = false,
                darkColor = if (selected) colors.accentContainer else colors.surface,
                darkBorder = BorderStroke(1.5.dp, if (selected) colors.accent else accent.copy(alpha = 0.45f)),
                onClick = { onSelectTemplate(item) },
                role = Role.Button,
                modifier = Modifier.width(142.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF545D68)),
                    ) {
                        if (previewThumb != null) {
                            Image(
                                bitmap = previewThumb!!,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.name,
                        color = if (selected) colors.accentSoft else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.style.localizedName(strings.language),
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (customPresets.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(strings.wmSavedPresets, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                items = customPresets,
                key = { it.id },
                contentType = { "custom_preset" },
            ) { preset ->
                val selected = activeTemplate.id == preset.id
                LiquidGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    selected = selected,
                    elevated = false,
                    darkColor = if (selected) colors.accentContainer else colors.surface,
                    darkBorder = BorderStroke(1.dp, if (selected) colors.accent else colors.borderStroke.copy(alpha = 0.5f)),
                    onClick = { onSelectTemplate(preset) },
                    role = Role.Button,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = preset.name,
                            color = if (selected) colors.accentSoft else colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = { onDeletePreset(preset.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = strings.cdRemovePhoto,
                                tint = colors.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
        onClick = onOpenSavePreset,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(strings.wmSaveCurrentPreset, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FieldsTab(
    activeTemplate: WatermarkTemplate,
    timeConfig: TimeConfig,
    epochMillis: Long,
    onUpdateTemplate: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onUpdateCoordinateFormat: (CoordinateFormatStyle) -> Unit,
    onOpenLocationPicker: () -> Unit,
    onOpenTimeDialog: () -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
    onOpenAddCustomField: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val isCustom = timeConfig.mode != TimeMode.LIVE
    val timeAccent = if (isCustom) colors.amber else colors.accent

    LiquidGlassSurface(
        shape = RoundedCornerShape(12.dp),
        selected = isCustom,
        elevated = false,
        darkColor = colors.surface,
        darkBorder = BorderStroke(1.dp, timeAccent.copy(alpha = 0.6f)),
        onClick = onOpenTimeDialog,
        role = Role.Button,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = timeAccent)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (timeConfig.mode) {
                            TimeMode.FIXED -> strings.timeModeFixed
                            TimeMode.OFFSET -> strings.timeModeOffset
                            TimeMode.LIVE -> strings.timeModeLive
                        },
                        color = timeAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${TimeProvider.formatTemplateClock(epochMillis, activeTemplate.use24Hour, activeTemplate.showSeconds)} • ${TimeProvider.formatTemplateDate(epochMillis, activeTemplate.dateFormat.pattern, locale = strings.language.toLocale())}",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(strings.wmEditClockBtn, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    LiquidGlassSurface(
        shape = RoundedCornerShape(12.dp),
        elevated = false,
        darkColor = colors.surface,
        darkBorder = BorderStroke(1.dp, colors.accent.copy(alpha = 0.55f)),
        onClick = onOpenLocationPicker,
        role = Role.Button,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.accent)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (activeTemplate.useLiveLocation) strings.locUseLiveGps else strings.wmSectionLocationGps,
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = activeTemplate.address.ifBlank { strings.wmTapEditHint },
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.btnEditLocation, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    val chipColors = themedFilterChipColors(colors)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = activeTemplate.showPhotoCode,
                onClick = { onUpdateTemplate { it.copy(showPhotoCode = !it.showPhotoCode) } },
                label = { Text(strings.wmFieldPhotoCode, fontSize = 12.sp) },
                colors = chipColors,
                border = themedFilterChipBorder(colors, activeTemplate.showPhotoCode),
            )
        }
        item {
            FilterChip(
                selected = activeTemplate.showVerifiedBadge,
                onClick = { onUpdateTemplate { it.copy(showVerifiedBadge = !it.showVerifiedBadge) } },
                label = { Text(strings.wmFieldVerifiedBadge, fontSize = 12.sp) },
                colors = chipColors,
                border = themedFilterChipBorder(colors, activeTemplate.showVerifiedBadge),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    if (activeTemplate.style.supportsTitle) {
        ToggleTextFieldRow(
            label = strings.wmFieldTitle,
            enabled = activeTemplate.showTitle,
            value = activeTemplate.title,
            onToggle = { onUpdateTemplate { t -> t.copy(showTitle = it) } },
            onValueChange = { onUpdateTemplate { t -> t.copy(title = it) } },
        )
    }
    if (activeTemplate.style.supportsSubtitle) {
        ToggleTextFieldRow(
            label = strings.wmFieldSubtitle,
            enabled = activeTemplate.showSubtitle,
            value = activeTemplate.subtitle,
            onToggle = { onUpdateTemplate { t -> t.copy(showSubtitle = it) } },
            onValueChange = { onUpdateTemplate { t -> t.copy(subtitle = it) } },
        )
    }
    ToggleTextFieldRow(
        label = strings.wmFieldStyledNote,
        enabled = activeTemplate.showStyledNote,
        value = activeTemplate.styledNote,
        onToggle = { onUpdateTemplate { t -> t.copy(showStyledNote = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(styledNote = it) } },
    )
    ToggleTextFieldRow(
        label = strings.wmToggleAddress,
        enabled = activeTemplate.showAddress,
        value = activeTemplate.address,
        onToggle = { onUpdateTemplate { t -> t.copy(showAddress = it) } },
        onValueChange = { newAddr ->
            onUpdateTemplate { t ->
                t.copy(
                    useLiveLocation = false,
                    address = newAddr,
                    smallPosition = newAddr.substringBefore(",").trim(),
                    largePosition = newAddr.substringAfter(",", "").trim(),
                )
            }
        },
    )
    ToggleTextFieldRow(
        label = strings.wmToggleCoordinates,
        enabled = activeTemplate.showCoordinates,
        value = activeTemplate.coordinates,
        onToggle = { onUpdateTemplate { t -> t.copy(showCoordinates = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(coordinates = it) } },
    )
    if (activeTemplate.showCoordinates) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CoordinateFormatStyle.entries, key = { it.name }) { fmt ->
                val selected = activeTemplate.coordinateFormat == fmt
                FilterChip(
                    selected = selected,
                    onClick = { onUpdateCoordinateFormat(fmt) },
                    label = { Text(fmt.label, fontSize = 11.sp) },
                    colors = chipColors,
                    border = themedFilterChipBorder(colors, selected),
                )
            }
        }
    }
    ToggleTextFieldRow(
        label = strings.wmFieldWeather,
        enabled = activeTemplate.showWeather,
        value = activeTemplate.weather,
        onToggle = { onUpdateTemplate { t -> t.copy(showWeather = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(weather = it) } },
    )
    ToggleTextFieldRow(
        label = strings.wmFieldAltitude,
        enabled = activeTemplate.showAltitude,
        value = activeTemplate.altitude,
        onToggle = { onUpdateTemplate { t -> t.copy(showAltitude = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(altitude = it) } },
    )
    ToggleTextFieldRow(
        label = strings.wmFieldCompass,
        enabled = activeTemplate.showCompass,
        value = activeTemplate.compass,
        onToggle = { onUpdateTemplate { t -> t.copy(showCompass = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(compass = it) } },
    )
    ToggleTextFieldRow(
        label = strings.wmFieldNote,
        enabled = activeTemplate.showNote,
        value = activeTemplate.note,
        onToggle = { onUpdateTemplate { t -> t.copy(showNote = it) } },
        onValueChange = { onUpdateTemplate { t -> t.copy(note = it) } },
    )

    activeTemplate.customFields.forEach { field ->
        ToggleTextFieldRow(
            label = field.label,
            enabled = field.enabled,
            value = field.value,
            onToggle = { checked ->
                onUpdateTemplate { t ->
                    t.copy(
                        customFields = t.customFields.map {
                            if (it.id == field.id) it.copy(enabled = checked) else it
                        },
                    )
                }
            },
            onValueChange = { newVal ->
                onUpdateTemplate { t ->
                    t.copy(
                        customFields = t.customFields.map {
                            if (it.id == field.id) it.copy(value = newVal) else it
                        },
                    )
                }
            },
            onDelete = if (field.isCustom) {
                {
                    onUpdateTemplate { t ->
                        t.copy(customFields = t.customFields.filterNot { it.id == field.id })
                    }
                }
            } else null,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onOpenAddCustomField, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.wmAddCustomField, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = onPickLogo, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.wmChooseLogo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (activeTemplate.logoUri != null) {
            TextButton(onClick = onClearLogo) { Text(strings.wmRemoveLogo, color = colors.error, fontSize = 12.sp) }
        }
    }

    if (activeTemplate.logoUri != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(strings.wmLogoPosition, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(LogoPlacement.entries, key = { it.name }) { placement ->
                FilterChip(
                    selected = activeTemplate.logoPosition == null && activeTemplate.logoPlacement == placement,
                    onClick = { onUpdateTemplate { it.copy(logoPlacement = placement, logoPosition = null) } },
                    label = { Text(placement.label, fontSize = 11.sp) },
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${strings.wmLogoSize}: ${(activeTemplate.logoScale * 100).toInt()}%",
            color = colors.textPrimary,
            fontSize = 12.sp,
        )
        Slider(
            value = activeTemplate.logoScale.coerceIn(0.12f, 0.45f),
            onValueChange = { s -> onUpdateTemplate { it.copy(logoScale = s) } },
            valueRange = 0.12f..0.45f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
        Text(
            text = "${strings.wmLogoOpacity}: ${(activeTemplate.logoAlpha * 100).toInt()}%",
            color = colors.textPrimary,
            fontSize = 12.sp,
        )
        Slider(
            value = activeTemplate.logoAlpha.coerceIn(0.15f, 1.0f),
            onValueChange = { a -> onUpdateTemplate { it.copy(logoAlpha = a) } },
            valueRange = 0.15f..1.0f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
    }
}

@Composable
private fun ToggleTextFieldRow(
    label: String,
    enabled: Boolean,
    value: String,
    onToggle: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "${strings.cdRemovePhoto}: $label",
                            tint = colors.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
        if (enabled) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StyleTab(
    activeTemplate: WatermarkTemplate,
    onUpdateTemplate: ((WatermarkTemplate) -> WatermarkTemplate) -> Unit,
    onPickCustomBrand: () -> Unit,
    onResetDefault: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    Text(strings.wmThemeAccent, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ACCENT_PALETTE) { hex ->
            val color = parseComposeColor(hex, colors.accent)
            val selected = activeTemplate.theme.accentColorHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) colors.textPrimary else colors.borderStroke,
                        shape = CircleShape,
                    )
                    .clickable(role = Role.Button) {
                        onUpdateTemplate { it.copy(theme = it.theme.copy(accentColorHex = hex)) }
                    },
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(strings.wmThemeText, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    val textColorChoices = when (strings.language) {
        AppLanguage.VI -> listOf("Trắng" to "#FFFFFF", "Đen" to "#222222")
        AppLanguage.EN -> listOf("White" to "#FFFFFF", "Black" to "#222222")
        AppLanguage.ZH_CN -> listOf("白色" to "#FFFFFF", "黑色" to "#222222")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        textColorChoices.forEach { (label, hex) ->
            FilterChip(
                selected = activeTemplate.theme.textColorHex.equals(hex, ignoreCase = true),
                onClick = {
                    onUpdateTemplate { it.copy(theme = it.theme.copy(textColorHex = hex)) }
                },
                label = { Text(label) },
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "${strings.wmThemeBgOpacity}: ${(activeTemplate.theme.backgroundAlpha * 100).toInt()}%",
        color = colors.textPrimary,
        fontSize = 13.sp,
    )
    Slider(
        value = activeTemplate.theme.backgroundAlpha,
        onValueChange = { alpha ->
            onUpdateTemplate { it.copy(theme = it.theme.copy(backgroundAlpha = alpha)) }
        },
        valueRange = 0f..0.95f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
    )

    Text(
        text = "${strings.wmThemeSize}: ${"%.2f".format(activeTemplate.theme.scale)}x",
        color = colors.textPrimary,
        fontSize = 13.sp,
    )
    Slider(
        value = activeTemplate.theme.scale,
        onValueChange = { s ->
            onUpdateTemplate { it.copy(theme = it.theme.copy(scale = s)) }
        },
        valueRange = 0.7f..1.5f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
    )

    Spacer(modifier = Modifier.height(6.dp))
    Text(strings.wmResetPosition, color = colors.textSecondary, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(6.dp))
    val cornerChoices = when (strings.language) {
        AppLanguage.VI -> listOf(
            "Trên trái" to WatermarkCorner.TOP_LEFT,
            "Trên phải" to WatermarkCorner.TOP_RIGHT,
            "Dưới trái" to WatermarkCorner.BOTTOM_LEFT,
            "Dưới phải" to WatermarkCorner.BOTTOM_RIGHT,
        )
        AppLanguage.EN -> listOf(
            "Top Left" to WatermarkCorner.TOP_LEFT,
            "Top Right" to WatermarkCorner.TOP_RIGHT,
            "Bottom Left" to WatermarkCorner.BOTTOM_LEFT,
            "Bottom Right" to WatermarkCorner.BOTTOM_RIGHT,
        )
        AppLanguage.ZH_CN -> listOf(
            "左上角" to WatermarkCorner.TOP_LEFT,
            "右上角" to WatermarkCorner.TOP_RIGHT,
            "左下角" to WatermarkCorner.BOTTOM_LEFT,
            "右下角" to WatermarkCorner.BOTTOM_RIGHT,
        )
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(cornerChoices, key = { it.second.name }) { (label, corner) ->
            FilterChip(
                selected = activeTemplate.position.corner == corner && activeTemplate.brandPosition == null && activeTemplate.logoPosition == null,
                onClick = {
                    onUpdateTemplate {
                        it.copy(
                            brandPosition = null,
                            logoPosition = null,
                            position = it.position.copy(
                                corner = corner,
                                xFraction = when (corner) {
                                    WatermarkCorner.TOP_LEFT, WatermarkCorner.BOTTOM_LEFT -> 0.022f
                                    else -> 0.55f
                                },
                                yFraction = when (corner) {
                                    WatermarkCorner.TOP_LEFT, WatermarkCorner.TOP_RIGHT -> 0.022f
                                    else -> 0.75f
                                },
                            ),
                        )
                    }
                },
                label = { Text(label, fontSize = 11.sp) },
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(strings.wmBrandRightTitle, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(BrandBadgePreset.entries, key = { it.name }) { preset ->
            FilterChip(
                selected = activeTemplate.brandBadgePreset == preset,
                onClick = { onUpdateTemplate { it.copy(brandBadgePreset = preset) } },
                label = { Text(preset.displayName(strings), fontSize = 11.sp) },
            )
        }
    }

    if (activeTemplate.brandBadgePreset == BrandBadgePreset.TIME_EDIT_OSS) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = activeTemplate.customBrandPrefix,
                onValueChange = { p -> onUpdateTemplate { it.copy(customBrandPrefix = p) } },
                label = { Text(strings.wmBrandPrefixHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = activeTemplate.customBrandSuffix,
                onValueChange = { s -> onUpdateTemplate { it.copy(customBrandSuffix = s) } },
                label = { Text(strings.wmBrandSuffixHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = activeTemplate.customBrandSubtitle,
            onValueChange = { sub -> onUpdateTemplate { it.copy(customBrandSubtitle = sub) } },
            label = { Text(strings.wmBrandSubtitleHint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(strings.brandAuthenticSubtitle, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (activeTemplate.brandBadgePreset == BrandBadgePreset.CUSTOM_IMAGE) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickCustomBrand, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.wmChooseBrandImage,
                fontSize = 12.sp,
            )
        }
    }

    if (activeTemplate.brandBadgePreset != BrandBadgePreset.OFF) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${strings.wmBrandScale}: ${"%.2f".format(activeTemplate.brandBadgeScale)}x",
            color = colors.textPrimary,
            fontSize = 12.sp,
        )
        Slider(
            value = activeTemplate.brandBadgeScale.coerceIn(0.6f, 1.5f),
            onValueChange = { s -> onUpdateTemplate { it.copy(brandBadgeScale = s) } },
            valueRange = 0.6f..1.5f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = onResetDefault,
        border = BorderStroke(1.dp, colors.borderStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(strings.wmRestoreDefault, color = colors.accentSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun themedFilterChipColors(colors: TimeEditPalette) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = colors.accentContainer,
    selectedLabelColor = colors.accentSoft,
    containerColor = colors.surface.copy(alpha = 0.72f),
    labelColor = colors.textSecondary,
)

@Composable
private fun themedFilterChipBorder(colors: TimeEditPalette, selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    selectedBorderColor = colors.accent,
    borderColor = colors.borderStroke.copy(alpha = 0.5f),
    selectedBorderWidth = 1.2.dp,
)
