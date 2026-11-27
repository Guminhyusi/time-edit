/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.location.LiveLocationSnapshot
import com.timeedit.camera.core.location.WeatherSnapshot
import com.timeedit.camera.core.storage.MediaStoreSaver
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.TimeLayoutVariant
import com.timeedit.camera.domain.watermark.WatermarkPosition
import com.timeedit.camera.domain.watermark.WatermarkStyleId
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.engine.camera.PhotoQuality
import com.timeedit.camera.engine.renderer.ExtractedPhotoExif
import com.timeedit.camera.engine.renderer.PhotoStampProcessor
import com.timeedit.camera.ui.camera.WatermarkOverlay
import com.timeedit.camera.ui.editor.TimeEditDialog
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import com.timeedit.camera.ui.theme.liquidGlassBackdrop
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BatchStampMode {
    SAME_TIME,
    MANUAL_PER_PHOTO,
}

data class BatchPhotoItem(
    val uri: Uri,
    val exif: ExtractedPhotoExif,
    val customTimeConfig: TimeConfig? = null,
    val isSaved: Boolean = false,
)

internal fun progressPopupAlignmentForRotation(uiRotationDegrees: Int): Alignment =
    when (((uiRotationDegrees % 360) + 360) % 360) {
        90 -> Alignment.CenterEnd
        180 -> Alignment.BottomCenter
        270 -> Alignment.CenterStart
        else -> Alignment.TopCenter
    }

internal fun isQuarterTurnRotation(uiRotationDegrees: Int): Boolean {
    val norm = ((uiRotationDegrees % 360) + 360) % 360
    return norm == 90 || norm == 270
}

@Composable
fun GalleryBatchStamperScreen(
    activeTemplate: WatermarkTemplate,
    timeConfig: TimeConfig,
    epochMillisFlow: StateFlow<Long>,
    photoQuality: PhotoQuality,
    uiRotationDegrees: Int,
    liveLocation: LiveLocationSnapshot?,
    liveWeather: WeatherSnapshot?,
    onWatermarkPositionCommitted: (WatermarkPosition) -> Unit,
    onBrandPositionCommitted: (WatermarkPosition) -> Unit = {},
    onLogoPositionCommitted: (WatermarkPosition) -> Unit = {},
    onResetWatermarkPositions: () -> Unit = {},
    onSyncTimeConfig: (TimeConfig) -> Unit,
    onToggleTimeLayout: () -> Unit = {},
    onOpenWatermark: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onBatchCompleted: (lastSavedUri: Uri?) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<BatchPhotoItem>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var batchMode by remember { mutableStateOf(BatchStampMode.SAME_TIME) }
    var sharedTimeConfig by remember { mutableStateOf(timeConfig) }
    var showLocalTimeDialog by remember { mutableStateOf(false) }

    var isLoadingSelection by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProcessedCount by remember { mutableIntStateOf(0) }
    var exportTotalCount by remember { mutableIntStateOf(1) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var didAutoLaunchPicker by remember { mutableStateOf(false) }
    var manualLastSavedUri by remember { mutableStateOf<Uri?>(null) }

    val previewCache = remember { LruCache<String, Bitmap>(6) }
    DisposableEffect(Unit) {
        onDispose { previewCache.evictAll() }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isLoadingSelection = true
            val existingUris = items.mapTo(mutableSetOf()) { it.uri }
            val newUris = uris.filterNot { it in existingUris }
            val added = withContext(Dispatchers.IO) {
                newUris.map { uri ->
                    BatchPhotoItem(
                        uri = uri,
                        exif = PhotoStampProcessor.extractPhotoMetadata(context, uri),
                    )
                }
            }
            if (added.isNotEmpty()) {
                val combined = items + added
                items = combined
                selectedIndex = selectedIndex.coerceIn(0, combined.lastIndex)
            }
            isLoadingSelection = false
        }
    }

    fun launchPicker() {
        runCatching {
            pickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }.onFailure {
            onNotify(strings.errorCouldNotOpenGallery)
        }
    }

    LaunchedEffect(Unit) {
        if (!didAutoLaunchPicker && items.isEmpty()) {
            didAutoLaunchPicker = true
            launchPicker()
        }
    }

    BackHandler(enabled = !isExporting) {
        onDismiss()
    }

    val selectedItem = items.getOrNull(selectedIndex)
    val isMultiPhoto = items.size >= 2
    val activeLatLng = resolveActiveTemplateLatLng(activeTemplate, liveLocation)
    val activeAlt = if (activeTemplate.useLiveLocation) {
        liveLocation?.altitudeMeters
    } else {
        activeTemplate.pinnedAltitude ?: liveLocation?.altitudeMeters
    }

    val blockThroughInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassBackdrop(colors, darkColor = colors.surface)
            .clickable(interactionSource = blockThroughInteraction, indication = null) {}
            .safeDrawingPadding(),
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = {
                    if (isExporting) {
                        exportJob?.cancel()
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.cdBack,
                    tint = colors.textPrimary,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val headerTitle = when {
                    items.isEmpty() -> strings.stampTitleEmpty
                    items.size == 1 -> strings.stampTitleSingle
                    batchMode == BatchStampMode.MANUAL_PER_PHOTO -> strings.stampTitleStep(selectedIndex + 1, items.size)
                    else -> strings.stampTitleMulti(items.size)
                }
                Text(
                    text = headerTitle,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = strings.stampHint,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            TextButton(
                onClick = { launchPicker() },
                enabled = !isExporting,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.addPhotos, color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        // Main Preview or Empty State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingSelection) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(strings.loadingPhotos, color = colors.textSecondary, fontSize = 13.sp)
                }
            } else if (selectedItem == null) {
                EmptyGalleryState(
                    onPickPhotos = { launchPicker() },
                    onBackToCamera = onDismiss,
                )
            } else {
                val uriKey = selectedItem.uri.toString()
                val previewBitmap by produceState<Bitmap?>(initialValue = previewCache.get(uriKey), selectedItem.uri) {
                    val cached = previewCache.get(uriKey)?.takeIf { !it.isRecycled }
                    if (cached != null) {
                        value = cached
                        return@produceState
                    }
                    val loaded = withContext(Dispatchers.IO) {
                        PhotoStampProcessor.decodeOrientedPreviewBitmap(context, selectedItem.uri, 1440)
                    }
                    if (loaded != null) {
                        previewCache.put(uriKey, loaded)
                    }
                    value = loaded
                }

                val photoAspect = remember(previewBitmap, selectedItem.exif) {
                    val bmp = previewBitmap
                    val w = bmp?.width ?: selectedItem.exif.width
                    val h = bmp?.height ?: selectedItem.exif.height
                    if (w > 0 && h > 0) (w.toFloat() / h.toFloat()).coerceIn(0.35f, 2.8f) else 0.75f
                }

                val activeItemConfig = if (isMultiPhoto && batchMode == BatchStampMode.MANUAL_PER_PHOTO) {
                    selectedItem.customTimeConfig ?: sharedTimeConfig
                } else {
                    sharedTimeConfig
                }

                var previewContainerSize by remember { mutableStateOf(IntSize.Zero) }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val originalPhotoTime = selectedItem.exif.epochMillis
                    val isTimeTemplate = activeTemplate.style == WatermarkStyleId.TIME_LOCATION
                    if (originalPhotoTime != null || isTimeTemplate) {
                        val headerRowModifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                        if (originalPhotoTime == null) {
                            Box(modifier = headerRowModifier) {
                                val isCompact = activeTemplate.timeLayoutVariant == TimeLayoutVariant.COMPACT_BAR
                                LiquidGlassSurface(
                                    shape = RoundedCornerShape(16.dp),
                                    selected = isCompact,
                                    elevated = false,
                                    darkColor = colors.surfaceElevated,
                                    darkBorder = BorderStroke(
                                        1.dp,
                                        if (isCompact) colors.accent else colors.borderStroke.copy(alpha = 0.55f),
                                    ),
                                    onClick = onToggleTimeLayout,
                                    role = Role.Button,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ViewAgenda,
                                            contentDescription = strings.cdSwitchTimeLayout,
                                            tint = if (isCompact) colors.accent else colors.textSecondary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = if (isCompact) strings.timeLayoutVariant2 else strings.timeLayoutVariant1,
                                            color = if (isCompact) colors.accent else colors.textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = headerRowModifier,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val origDate = TimeProvider.formatTemplateDate(originalPhotoTime, activeTemplate.dateFormat.pattern, locale = strings.language.toLocale())
                                    val origClock = TimeProvider.formatTemplateClock(originalPhotoTime, use24Hour = true, showSeconds = false)
                                    LiquidGlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        elevated = false,
                                        darkColor = colors.surfaceElevated,
                                        darkBorder = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
                                    ) {
                                        Text(
                                            text = strings.originalCaptureDate("$origDate $origClock"),
                                            color = colors.accentSoft,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                if (isTimeTemplate) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val isCompact = activeTemplate.timeLayoutVariant == TimeLayoutVariant.COMPACT_BAR
                                    LiquidGlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        selected = isCompact,
                                        elevated = false,
                                        darkColor = colors.surfaceElevated,
                                        darkBorder = BorderStroke(
                                            1.dp,
                                            if (isCompact) colors.accent else colors.borderStroke.copy(alpha = 0.55f),
                                        ),
                                        onClick = onToggleTimeLayout,
                                        role = Role.Button,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ViewAgenda,
                                                contentDescription = strings.cdSwitchTimeLayout,
                                                tint = if (isCompact) colors.accent else colors.textSecondary,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Text(
                                                text = if (isCompact) strings.timeLayoutVariant2 else strings.timeLayoutVariant1,
                                                color = if (isCompact) colors.accent else colors.textPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(photoAspect)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .onSizeChanged { previewContainerSize = it },
                        ) {
                            val bmp = previewBitmap
                            if (bmp != null && !bmp.isRecycled) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = strings.cdPreviewStamped,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }

                            if (previewContainerSize.width > 0 && previewContainerSize.height > 0) {
                                val currentEpoch by epochMillisFlow.collectAsStateWithLifecycle()
                                val previewEpoch = TimeProvider.resolveEpochMillis(
                                    nowMillis = currentEpoch,
                                    config = activeItemConfig,
                                )
                                WatermarkOverlay(
                                    template = activeTemplate,
                                    epochMillis = previewEpoch,
                                    containerSize = previewContainerSize,
                                    weatherCode = liveWeather?.weatherCode,
                                    uiRotationDegrees = uiRotationDegrees,
                                    language = strings.language,
                                    onPositionCommitted = onWatermarkPositionCommitted,
                                    onBrandPositionCommitted = onBrandPositionCommitted,
                                    onLogoPositionCommitted = onLogoPositionCommitted,
                                    onResetPositions = onResetWatermarkPositions,
                                    onTapTime = { showLocalTimeDialog = true },
                                    onTapWatermark = onOpenWatermark,
                                )
                            }

                            val popupAlignment = progressPopupAlignmentForRotation(uiRotationDegrees)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isExporting,
                                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
                                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150)),
                                modifier = Modifier
                                    .align(popupAlignment)
                                    .padding(10.dp),
                            ) {
                                OrientationAwareProgressPill(
                                    processed = exportProcessedCount,
                                    total = exportTotalCount,
                                    uiRotationDegrees = uiRotationDegrees,
                                    cancelText = strings.cancel,
                                    onCancel = if (exportTotalCount > 1) {
                                        { exportJob?.cancel() }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thumbnail strip + Action Dock
        if (items.isNotEmpty()) {
            if (isMultiPhoto) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(items, key = { _, item -> item.uri.toString() }) { index, item ->
                        BatchThumbnailTile(
                            uri = item.uri,
                            isSelected = index == selectedIndex,
                            isSaved = item.isSaved,
                            enabled = !isExporting,
                            onSelect = { selectedIndex = index },
                            onRemove = {
                                val next = items.toMutableList().apply { removeAt(index) }
                                items = next
                                selectedIndex = if (next.isEmpty()) 0 else selectedIndex.coerceIn(0, next.lastIndex)
                            },
                        )
                    }
                }
            }

            LiquidGlassSurface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                darkColor = colors.surfaceElevated,
                darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Multi-photo workflow switch: Same Time vs Manual per Photo
                    if (isMultiPhoto) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BatchModePill(
                                text = strings.modeSameTime,
                                selected = batchMode == BatchStampMode.SAME_TIME,
                                enabled = !isExporting,
                                onClick = { batchMode = BatchStampMode.SAME_TIME },
                                modifier = Modifier.weight(1f),
                            )
                            BatchModePill(
                                text = strings.modePerPhoto,
                                selected = batchMode == BatchStampMode.MANUAL_PER_PHOTO,
                                enabled = !isExporting,
                                onClick = { batchMode = BatchStampMode.MANUAL_PER_PHOTO },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Large customize buttons (Location button only appears if template uses location)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LargeToolButton(
                            icon = Icons.Rounded.AutoAwesomeMosaic,
                            label = strings.btnTemplate,
                            enabled = !isExporting,
                            onClick = onOpenWatermark,
                            modifier = Modifier.weight(1f),
                        )
                        LargeToolButton(
                            icon = Icons.Rounded.Schedule,
                            label = strings.btnEditTime,
                            enabled = !isExporting,
                            onClick = { showLocalTimeDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                        if (activeTemplate.usesLocationFeature) {
                            LargeToolButton(
                                icon = Icons.Rounded.LocationOn,
                                label = strings.btnEditLocation,
                                enabled = !isExporting,
                                onClick = onOpenLocationPicker,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Export / Save Section (progress displays as compact floating pill at top of preview frame)
                    if (!isMultiPhoto || batchMode == BatchStampMode.SAME_TIME) {
                        Button(
                            enabled = !isExporting,
                            onClick = {
                                val snapshotItems = items
                                exportJob = scope.launch {
                                    isExporting = true
                                    exportProcessedCount = 0
                                    exportTotalCount = snapshotItems.size.coerceAtLeast(1)
                                    var lastUri: Uri? = null
                                    val lockedBatchEpoch = TimeProvider.resolveEpochMillis(
                                        nowMillis = System.currentTimeMillis(),
                                        config = sharedTimeConfig,
                                    )
                                    try {
                                        for ((idx, item) in snapshotItems.withIndex()) {
                                            if (!isActive) break
                                            val tempOut = File.createTempFile("te_batch_", ".png", context.cacheDir)
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    PhotoStampProcessor.stampGalleryUriToFile(
                                                        context = context,
                                                        uri = item.uri,
                                                        epochMillis = lockedBatchEpoch,
                                                        template = activeTemplate,
                                                        weatherCode = liveWeather?.weatherCode,
                                                        gpsLatitude = activeLatLng?.first,
                                                        gpsLongitude = activeLatLng?.second,
                                                        gpsAltitude = activeAlt,
                                                        targetFile = tempOut,
                                                        maxDimension = photoQuality.maxDimension,
                                                        language = strings.language,
                                                    )
                                                }
                                                val suffix = if (snapshotItems.size > 1) "_${idx + 1}" else ""
                                                val saved = MediaStoreSaver.savePngFile(
                                                    context = context,
                                                    sourceFile = tempOut,
                                                    epochMillis = lockedBatchEpoch,
                                                    isOriginal = false,
                                                    suffixOverride = suffix,
                                                )
                                                if (saved.isSuccess) {
                                                    lastUri = saved.getOrNull()
                                                }
                                            } finally {
                                                tempOut.delete()
                                            }
                                            exportProcessedCount = idx + 1
                                        }
                                        isExporting = false
                                        onBatchCompleted(lastUri)
                                        onDismiss()
                                    } catch (_: CancellationException) {
                                        isExporting = false
                                        onNotify(strings.cancelledSave)
                                    } catch (t: Throwable) {
                                        isExporting = false
                                        onNotify(t.message ?: strings.errorCouldNotSavePhoto)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(
                                text = if (items.size == 1) {
                                    strings.btnSavePhoto
                                } else {
                                    strings.btnSaveAll(items.size)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    } else {
                        // Manual per-photo flow (>= 2 items)
                        val unsavedAfterCurrent = items.indices.firstOrNull { idx ->
                            idx != selectedIndex && !items[idx].isSaved
                        }
                        val isLastRemaining = unsavedAfterCurrent == null
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                enabled = !isExporting,
                                onClick = {
                                    if (unsavedAfterCurrent != null) {
                                        selectedIndex = unsavedAfterCurrent
                                    } else {
                                        val savedCount = items.count { it.isSaved }
                                        if (savedCount > 0) {
                                            onBatchCompleted(manualLastSavedUri)
                                        }
                                        onDismiss()
                                    }
                                },
                                border = BorderStroke(1.dp, colors.borderStroke),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Text(
                                    text = if (isLastRemaining) strings.btnDone else strings.btnSkip,
                                    color = colors.accentSoft,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Button(
                                enabled = !isExporting,
                                onClick = {
                                    val targetIdx = selectedIndex
                                    val currentItem = items.getOrNull(targetIdx) ?: return@Button
                                    exportJob = scope.launch {
                                        isExporting = true
                                        exportProcessedCount = 0
                                        exportTotalCount = 1
                                        val targetEpoch = PhotoStampProcessor.resolveStampEpochMillis(
                                            customTimeConfig = currentItem.customTimeConfig,
                                            sharedTimeConfig = sharedTimeConfig,
                                            nowMillis = System.currentTimeMillis(),
                                        )
                                        val tempOut = File.createTempFile("te_single_", ".png", context.cacheDir)
                                        try {
                                            withContext(Dispatchers.IO) {
                                                PhotoStampProcessor.stampGalleryUriToFile(
                                                    context = context,
                                                    uri = currentItem.uri,
                                                    epochMillis = targetEpoch,
                                                    template = activeTemplate,
                                                    weatherCode = liveWeather?.weatherCode,
                                                    gpsLatitude = activeLatLng?.first,
                                                    gpsLongitude = activeLatLng?.second,
                                                    gpsAltitude = activeAlt,
                                                    targetFile = tempOut,
                                                    maxDimension = photoQuality.maxDimension,
                                                    language = strings.language,
                                                )
                                            }
                                            val saved = MediaStoreSaver.savePngFile(
                                                context = context,
                                                sourceFile = tempOut,
                                                epochMillis = targetEpoch,
                                                isOriginal = false,
                                                suffixOverride = "_${targetIdx + 1}",
                                            )
                                            exportProcessedCount = 1
                                            isExporting = false
                                            if (saved.isSuccess) {
                                                val savedUri = saved.getOrNull()
                                                manualLastSavedUri = savedUri
                                                val updated = items.mapIndexed { idx, it ->
                                                    if (idx == targetIdx) it.copy(isSaved = true) else it
                                                }
                                                items = updated
                                                val nextUnsaved = updated.indices.firstOrNull { !updated[it].isSaved }
                                                if (nextUnsaved != null) {
                                                    selectedIndex = nextUnsaved
                                                } else {
                                                    onBatchCompleted(savedUri)
                                                    onDismiss()
                                                }
                                            } else {
                                                onNotify(strings.errorCouldNotSavePhoto)
                                            }
                                        } catch (t: Throwable) {
                                            isExporting = false
                                            onNotify(t.message ?: strings.errorCouldNotSavePhoto)
                                        } finally {
                                            tempOut.delete()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.7f)
                                    .height(48.dp),
                            ) {
                                Text(
                                    text = if (isLastRemaining) strings.btnSaveFinish else strings.btnSaveNext,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLocalTimeDialog) {
        val currentConfig = if (isMultiPhoto && batchMode == BatchStampMode.MANUAL_PER_PHOTO && selectedItem != null) {
            selectedItem.customTimeConfig ?: sharedTimeConfig
        } else {
            sharedTimeConfig
        }
        TimeEditDialog(
            initialConfig = currentConfig,
            photoOriginalEpochMillis = selectedItem?.exif?.epochMillis,
            onApply = { newConfig ->
                if (isMultiPhoto && batchMode == BatchStampMode.MANUAL_PER_PHOTO) {
                    items = items.mapIndexed { idx, item ->
                        if (idx == selectedIndex) item.copy(customTimeConfig = newConfig) else item
                    }
                } else {
                    sharedTimeConfig = newConfig
                    onSyncTimeConfig(newConfig)
                }
                showLocalTimeDialog = false
            },
            onDismiss = { showLocalTimeDialog = false },
        )
    }
}

@Composable
private fun EmptyGalleryState(
    onPickPhotos: () -> Unit,
    onBackToCamera: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(20.dp),
        darkColor = colors.surfaceElevated,
        darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.emptyGalleryTitle,
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.emptyGalleryBody,
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onPickPhotos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.pickFromGallery, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBackToCamera,
                border = BorderStroke(1.dp, colors.borderStroke),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.back, color = colors.accentSoft)
            }
        }
    }
}

@Composable
private fun BatchThumbnailTile(
    uri: Uri,
    isSelected: Boolean,
    isSaved: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colors.accent else colors.borderStroke.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(colors.surfaceElevated)
            .clickable(enabled = enabled, role = Role.Button, onClick = onSelect),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isSaved) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = strings.cdSavedBadge,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.scrim)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.cdRemovePhoto,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun BatchModePill(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTimeEditColors.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(12.dp),
        selected = selected,
        elevated = false,
        darkColor = if (selected) colors.accentContainer else colors.surface,
        darkBorder = BorderStroke(1.dp, if (selected) colors.accent else colors.borderStroke.copy(alpha = 0.4f)),
        onClick = onClick,
        enabled = enabled,
        role = Role.Tab,
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = if (selected) colors.accentSoft else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun LargeToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTimeEditColors.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(14.dp),
        elevated = false,
        darkColor = colors.surface,
        darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.55f)),
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OrientationAwareProgressPill(
    processed: Int,
    total: Int,
    uiRotationDegrees: Int,
    cancelText: String,
    onCancel: (() -> Unit)?,
) {
    val colors = LocalTimeEditColors.current
    val normalizedRot = ((uiRotationDegrees % 360) + 360) % 360
    val quarterTurn = isQuarterTurnRotation(normalizedRot)

    Layout(
        content = {
            LiquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                opaqueModal = true,
                darkColor = colors.glassDialogFill.copy(alpha = 0.94f),
                darkBorder = BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = if (onCancel != null) 8.dp else 14.dp,
                        top = 7.dp,
                        bottom = 7.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "$processed/$total",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (onCancel != null) {
                        Text(
                            text = cancelText,
                            color = colors.amber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClick = onCancel)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.first().measure(loose)
        val outW = if (quarterTurn) placeable.height else placeable.width
        val outH = if (quarterTurn) placeable.width else placeable.height
        layout(outW, outH) {
            placeable.placeRelativeWithLayer(
                x = (outW - placeable.width) / 2,
                y = (outH - placeable.height) / 2,
            ) {
                rotationZ = normalizedRot.toFloat()
            }
        }
    }
}

private fun resolveActiveTemplateLatLng(
    template: WatermarkTemplate,
    liveLocation: LiveLocationSnapshot?,
): Pair<Double, Double>? {
    if (!template.useLiveLocation && template.pinnedLatitude != null && template.pinnedLongitude != null) {
        return template.pinnedLatitude to template.pinnedLongitude
    }
    return liveLocation?.let { it.latitude to it.longitude }
}
