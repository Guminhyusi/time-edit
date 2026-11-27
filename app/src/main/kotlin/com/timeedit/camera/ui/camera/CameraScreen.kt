/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.AppStringsProvider
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.location.LocationFixState
import com.timeedit.camera.core.sensors.OrientationMath
import com.timeedit.camera.core.time.TimeMode
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.TimeLayoutVariant
import com.timeedit.camera.domain.watermark.WatermarkPosition
import com.timeedit.camera.domain.watermark.WatermarkStyleId
import com.timeedit.camera.engine.camera.CameraAspectRatio
import com.timeedit.camera.engine.camera.CameraFlashMode
import com.timeedit.camera.engine.camera.CameraUiState
import com.timeedit.camera.engine.camera.CaptureMode
import com.timeedit.camera.engine.camera.CaptureTimer
import com.timeedit.camera.engine.camera.PhotoQuality
import com.timeedit.camera.engine.camera.VideoQualityPreset
import com.timeedit.camera.engine.camera.formatRecordingDuration
import com.timeedit.camera.engine.camera.formatZoomLabel
import com.timeedit.camera.ui.editor.LocationPickerDialog
import com.timeedit.camera.ui.editor.TimeEditDialog
import com.timeedit.camera.ui.editor.WatermarkEditorSheet
import com.timeedit.camera.ui.gallery.GalleryBatchStamperScreen
import com.timeedit.camera.ui.theme.AppThemeMode
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import com.timeedit.camera.ui.theme.TimeEditTheme
import com.timeedit.camera.ui.theme.liquidGlassBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@Composable
fun CameraRoute(
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    TimeEditTheme(themeMode = uiState.themeMode) {
    CompositionLocalProvider(LocalAppStrings provides AppStringsProvider.forLanguage(uiState.appLanguage)) {
        val strings = LocalAppStrings.current
        val colors = LocalTimeEditColors.current

        var showTimeDialog by remember { mutableStateOf(false) }
    var showWatermarkSheet by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showGalleryStamper by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        viewModel.startOrStopVideoRecording(hasAudioPermission = granted)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        val fine = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            hasLocationPermission = true
            viewModel.startLiveSensors()
        }
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasLocationPermission) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.bindCamera(lifecycleOwner)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startLiveSensors()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopLiveSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var activeToastText by remember { mutableStateOf<String?>(null) }
    var activeToastIsError by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        activeToastIsError = true
        activeToastText = msg
        delay(2600L)
        if (activeToastText == msg) activeToastText = null
        viewModel.dismissError()
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        activeToastIsError = false
        activeToastText = msg
        delay(1600L)
        if (activeToastText == msg) activeToastText = null
        viewModel.dismissStatus()
    }

    if (!hasCameraPermission) {
        PermissionGateScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        )
        return@CompositionLocalProvider
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassBackdrop(colors),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
        ) { innerPadding ->
            CameraScreenContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiState = uiState,
                surfaceRequest = surfaceRequest,
                epochMillisFlow = viewModel.currentEpochMillis,
                onToggleFlash = { viewModel.cycleFlash() },
                onToggleAspectRatio = { viewModel.cycleAspectRatio(lifecycleOwner) },
                onToggleTimer = { viewModel.cycleTimer() },
                onSelectPhotoQuality = { viewModel.setPhotoQuality(it) },
                onSelectVideoQuality = { viewModel.setVideoQuality(it, lifecycleOwner) },
                onToggleAudio = { viewModel.toggleAudioEnabled() },
                onSwitchCamera = { viewModel.switchCamera(lifecycleOwner) },
                onSelectZoom = { viewModel.setZoomRatio(it) },
                onPinchZoom = { viewModel.scaleZoomBy(it) },
                onTapToFocus = { x, y, w, h -> viewModel.focusAt(x, y, w, h) },
                onWatermarkPositionCommitted = { viewModel.updateWatermarkPosition(it) },
                onBrandPositionCommitted = { viewModel.updateBrandPosition(it) },
                onLogoPositionCommitted = { viewModel.updateLogoPosition(it) },
                onResetWatermarkPositions = { viewModel.resetWatermarkPositions() },
                onOpenTimeEdit = { showTimeDialog = true },
                onOpenLocationPicker = { showLocationPicker = true },
                onResetLiveTime = { viewModel.resetToLiveTime() },
                onToggleTimeLayout = { viewModel.toggleTimeLayoutVariant() },
                onSelectCaptureMode = { mode -> viewModel.setCaptureMode(mode, lifecycleOwner) },
                onOpenGalleryStamper = {
                    if (!uiState.isRecording && !uiState.isCapturing) {
                        showGalleryStamper = true
                    }
                },
                onShutterClick = {
                    if (uiState.captureMode == CaptureMode.PHOTO) {
                        viewModel.onShutterClicked()
                    } else {
                        if (uiState.isRecording) {
                            viewModel.startOrStopVideoRecording(hasAudioPermission)
                        } else if (uiState.isAudioEnabled && !hasAudioPermission) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.startOrStopVideoRecording(hasAudioPermission)
                        }
                    }
                },
                onOpenLastPhoto = { uri ->
                    val intent = if (uri != null) {
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (uiState.lastCapturedIsVideo) "video/*" else "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        viewModel.showStatus(strings.galleryNotFound)
                    }
                },
                onOpenWatermark = { showWatermarkSheet = true },
                onOpenSettings = { showQuickSettings = true },
            )
        }

        if (showGalleryStamper) {
            GalleryBatchStamperScreen(
                activeTemplate = uiState.activeTemplate,
                timeConfig = uiState.timeConfig,
                epochMillisFlow = viewModel.currentEpochMillis,
                photoQuality = uiState.photoQuality,
                uiRotationDegrees = uiState.uiRotationDegrees,
                liveLocation = uiState.liveLocation,
                liveWeather = uiState.liveWeather,
                onWatermarkPositionCommitted = { viewModel.updateWatermarkPosition(it) },
                onBrandPositionCommitted = { viewModel.updateBrandPosition(it) },
                onLogoPositionCommitted = { viewModel.updateLogoPosition(it) },
                onResetWatermarkPositions = { viewModel.resetWatermarkPositions() },
                onSyncTimeConfig = { viewModel.updateTimeConfig(it) },
                onToggleTimeLayout = { viewModel.toggleTimeLayoutVariant() },
                onOpenWatermark = { showWatermarkSheet = true },
                onOpenLocationPicker = { showLocationPicker = true },
                onBatchCompleted = { lastUri ->
                    viewModel.onBatchPhotosSaved(lastUri)
                },
                onNotify = { viewModel.showStatus(it) },
                onDismiss = { showGalleryStamper = false },
            )
        }

        FloatingStatusToast(
            message = activeToastText,
            isError = activeToastIsError,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 56.dp, start = 20.dp, end = 20.dp),
        )
    }

    if (showWatermarkSheet) {
        WatermarkEditorSheet(
            activeTemplate = uiState.activeTemplate,
            customPresets = uiState.customPresets,
            timeConfig = uiState.timeConfig,
            epochMillisFlow = viewModel.currentEpochMillis,
            onSelectTemplate = { viewModel.selectTemplate(it) },
            onUpdateTemplate = { viewModel.updateActiveTemplate(it) },
            onUpdateCoordinateFormat = { viewModel.updateCoordinateFormat(it) },
            onResetTemplateDefault = { viewModel.resetCurrentTemplateToDefault() },
            onOpenLocationPicker = {
                showWatermarkSheet = false
                showLocationPicker = true
            },
            onOpenTimeDialog = { showTimeDialog = true },
            onSavePreset = { viewModel.saveCurrentAsPreset(it) },
            onDeletePreset = { viewModel.deletePreset(it) },
            onDismiss = { showWatermarkSheet = false },
        )
    }

    if (showLocationPicker) {
        val activeT = uiState.activeTemplate
        val initialLat = if (!activeT.useLiveLocation && activeT.pinnedLatitude != null) activeT.pinnedLatitude else uiState.liveLocation?.latitude
        val initialLng = if (!activeT.useLiveLocation && activeT.pinnedLongitude != null) activeT.pinnedLongitude else uiState.liveLocation?.longitude
        LocationPickerDialog(
            initialLat = initialLat,
            initialLng = initialLng,
            initialSmallPosition = activeT.smallPosition,
            initialFullAddress = activeT.address,
            initialLargePosition = activeT.largePosition,
            coordinateFormat = activeT.coordinateFormat,
            onCoordinateFormatChanged = { viewModel.updateCoordinateFormat(it) },
            onSearchPlaces = { q -> viewModel.searchPlaces(q) },
            onReverseGeocode = { lat, lng -> viewModel.reverseGeocodePin(lat, lng) },
            onResumeLiveGps = {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
                viewModel.resumeLiveGps()
            },
            onApplyPinnedLocation = { lat, lng, addr ->
                viewModel.applyManualLocation(lat, lng, addr)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
        )
    }

    if (showTimeDialog) {
        TimeEditDialog(
            initialConfig = uiState.timeConfig,
            onApply = { config ->
                viewModel.updateTimeConfig(config)
                showTimeDialog = false
            },
            onDismiss = { showTimeDialog = false },
        )
    }

    if (showQuickSettings) {
        QuickSettingsDialog(
            selectedLanguage = uiState.appLanguage,
            selectedThemeMode = uiState.themeMode,
            photoQuality = uiState.photoQuality,
            effectiveVideoQuality = uiState.effectiveVideoQuality,
            supportedVideoQualities = uiState.supportedVideoQualities,
            showGrid = uiState.showGrid,
            saveOriginalPhoto = uiState.saveOriginalPhoto,
            mirrorFrontCamera = uiState.mirrorFrontCamera,
            onSelectLanguage = { viewModel.setAppLanguage(it) },
            onSelectThemeMode = { viewModel.setThemeMode(it) },
            onSelectPhotoQuality = { viewModel.setPhotoQuality(it) },
            onSelectVideoQuality = { viewModel.setVideoQuality(it, lifecycleOwner) },
            onToggleShowGrid = { viewModel.setShowGrid(it) },
            onToggleSaveOriginal = { viewModel.setSaveOriginalPhoto(it) },
            onToggleMirrorFront = { viewModel.setMirrorFrontCamera(it) },
            onDismiss = { showQuickSettings = false },
        )
    }
    }
    }
}

@Composable
fun CameraScreenContent(
    uiState: CameraUiState,
    surfaceRequest: androidx.camera.core.SurfaceRequest?,
    epochMillisFlow: StateFlow<Long>,
    onToggleFlash: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleTimer: () -> Unit,
    onSelectPhotoQuality: (PhotoQuality) -> Unit,
    onSelectVideoQuality: (VideoQualityPreset) -> Unit,
    onToggleAudio: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSelectZoom: (Float) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onTapToFocus: (Float, Float, Float, Float) -> Unit,
    onWatermarkPositionCommitted: (WatermarkPosition) -> Unit,
    onBrandPositionCommitted: (WatermarkPosition) -> Unit,
    onLogoPositionCommitted: (WatermarkPosition) -> Unit,
    onResetWatermarkPositions: () -> Unit,
    onOpenTimeEdit: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    onResetLiveTime: () -> Unit,
    onToggleTimeLayout: () -> Unit,
    onSelectCaptureMode: (CaptureMode) -> Unit,
    onOpenGalleryStamper: () -> Unit,
    onShutterClick: () -> Unit,
    onOpenLastPhoto: (Uri?) -> Unit,
    onOpenWatermark: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewfinderAspect = if (uiState.captureMode == CaptureMode.VIDEO) {
        CameraAspectRatio.RATIO_16_9.portraitRatio
    } else {
        uiState.aspectRatio.portraitRatio
    }

    var targetIconAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(uiState.uiRotationDegrees) {
        targetIconAngle = OrientationMath.computeShortestRotationTarget(
            currentAngle = targetIconAngle,
            targetDegrees = uiState.uiRotationDegrees,
        )
    }
    val iconRotation by animateFloatAsState(
        targetValue = targetIconAngle,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "iconRotation",
    )

    val coordinateTransformer = remember { MutableCoordinateTransformer() }
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }
    val focusScale = remember { Animatable(1.35f) }
    val shutterFlashAlpha = remember { Animatable(0f) }

    LaunchedEffect(uiState.isCapturing) {
        if (uiState.isCapturing) {
            shutterFlashAlpha.snapTo(0.45f)
            shutterFlashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 120))
        }
    }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            focusAlpha.snapTo(1f)
            focusScale.snapTo(1.35f)
            focusScale.animateTo(1.0f, animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            delay(750L)
            focusAlpha.animateTo(0f, animationSpec = tween(durationMillis = 260))
            focusTapOffset = null
        }
    }

    val colors = LocalTimeEditColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .liquidGlassBackdrop(colors),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CameraTopBar(
            captureMode = uiState.captureMode,
            isRecording = uiState.isRecording,
            recordingDurationSeconds = uiState.recordingDurationSeconds,
            flashMode = uiState.flashMode,
            hasFlashUnit = uiState.hasFlashUnit,
            aspectRatio = uiState.aspectRatio,
            timer = uiState.timer,
            photoQuality = uiState.photoQuality,
            effectiveVideoQuality = uiState.effectiveVideoQuality,
            supportedVideoQualities = uiState.supportedVideoQualities,
            isAudioEnabled = uiState.isAudioEnabled,
            iconRotation = iconRotation,
            onToggleFlash = onToggleFlash,
            onToggleAspectRatio = onToggleAspectRatio,
            onToggleTimer = onToggleTimer,
            onSelectPhotoQuality = onSelectPhotoQuality,
            onSelectVideoQuality = onSelectVideoQuality,
            onToggleAudio = onToggleAudio,
            onOpenSettings = onOpenSettings,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (colors.isLight) Modifier.padding(horizontal = 8.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val focusRingSizePx = with(LocalDensity.current) { 64.dp.toPx() }
            val viewfinderShape = RoundedCornerShape(if (colors.isLight) 20.dp else 14.dp)
            Box(
                modifier = Modifier
                    .aspectRatio(viewfinderAspect)
                    .fillMaxSize()
                    .clip(viewfinderShape)
                    .background(if (colors.isLight) Color(0xFF1A1417) else colors.surface)
                    .then(
                        if (colors.isLight) {
                            Modifier.border(1.2.dp, colors.glassHighlightRim, viewfinderShape)
                        } else Modifier,
                    )
                    .onSizeChanged { viewfinderSize = it }
                    .pointerInput(surfaceRequest) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (abs(zoom - 1f) > 0.005f) {
                                onPinchZoom(zoom)
                            }
                        }
                    }
                    .pointerInput(surfaceRequest) {
                        detectTapGestures { offset ->
                            focusTapOffset = offset
                            val req = surfaceRequest ?: return@detectTapGestures
                            val transformed = with(coordinateTransformer) { offset.transform() }
                            onTapToFocus(
                                transformed.x,
                                transformed.y,
                                req.resolution.width.toFloat(),
                                req.resolution.height.toFloat(),
                            )
                        }
                    },
            ) {
                if (surfaceRequest != null) {
                    CameraXViewfinder(
                        surfaceRequest = surfaceRequest,
                        modifier = Modifier.fillMaxSize(),
                        coordinateTransformer = coordinateTransformer,
                        contentScale = ContentScale.Crop,
                    )
                }

                if (uiState.showGrid) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val lineColor = Color.White.copy(alpha = 0.35f)
                                val stroke = 1.dp.toPx()
                                val thirdW = size.width / 3f
                                val thirdH = size.height / 3f
                                drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), stroke)
                                drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), stroke)
                                drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), stroke)
                                drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), stroke)
                            },
                    )
                }

                if (focusTapOffset != null) {
                    Box(
                        modifier = Modifier
                            .offset {
                                val pos = focusTapOffset ?: Offset.Zero
                                IntOffset(
                                    x = (pos.x - focusRingSizePx / 2f).roundToInt(),
                                    y = (pos.y - focusRingSizePx / 2f).roundToInt(),
                                )
                            }
                            .size(64.dp)
                            .graphicsLayer {
                                alpha = focusAlpha.value
                                scaleX = focusScale.value
                                scaleY = focusScale.value
                            }
                            .border(2.dp, colors.accent, CircleShape),
                    )
                }

                // Draggable Live Watermark Overlay rotated seamlessly with device tilt
                if (viewfinderSize.width > 0 && viewfinderSize.height > 0) {
                    LiveWatermarkOverlay(
                        template = uiState.activeTemplate,
                        epochMillisFlow = epochMillisFlow,
                        containerSize = viewfinderSize,
                        weatherCode = uiState.liveWeather?.weatherCode,
                        uiRotationDegrees = uiState.uiRotationDegrees,
                        language = uiState.appLanguage,
                        onPositionCommitted = onWatermarkPositionCommitted,
                        onBrandPositionCommitted = onBrandPositionCommitted,
                        onLogoPositionCommitted = onLogoPositionCommitted,
                        onResetPositions = onResetWatermarkPositions,
                        onTapTime = onOpenTimeEdit,
                        onTapWatermark = onOpenWatermark,
                    )
                }

                // Shutter flash pulse
                if (shutterFlashAlpha.value > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = shutterFlashAlpha.value)),
                    )
                }

                // Countdown Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.countdownSeconds > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = uiState.countdownSeconds.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentSoft,
                        modifier = Modifier
                            .background(colors.scrim, CircleShape)
                            .padding(horizontal = 32.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // Time-first Status Pills Row: keep TimeStatusPill dead-center when alone,
        // and anchor TimeLayoutSwitchPill on the right edge cleanly.
        val showLocationPill = uiState.activeTemplate.usesLocationFeature
        val showTimeLayoutPill = uiState.activeTemplate.style == WatermarkStyleId.TIME_LOCATION
        val pillRowModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)

        if (!showLocationPill) {
            Box(modifier = pillRowModifier) {
                TimeStatusPill(
                    timeMode = uiState.timeConfig.mode,
                    epochMillisFlow = epochMillisFlow,
                    onClick = onOpenTimeEdit,
                    onResetLive = onResetLiveTime,
                    modifier = Modifier.align(Alignment.Center),
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showTimeLayoutPill,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    TimeLayoutSwitchPill(
                        variant = uiState.activeTemplate.timeLayoutVariant,
                        onClick = onToggleTimeLayout,
                    )
                }
            }
        } else {
            Row(
                modifier = pillRowModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeStatusPill(
                        timeMode = uiState.timeConfig.mode,
                        epochMillisFlow = epochMillisFlow,
                        onClick = onOpenTimeEdit,
                        onResetLive = onResetLiveTime,
                    )

                    LocationStatusPill(
                        locationState = uiState.locationState,
                        shortLabel = uiState.activeTemplate.smallPosition.ifBlank {
                            uiState.activeTemplate.address.substringBefore(",").trim()
                        },
                        onClick = onOpenLocationPicker,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                AnimatedVisibility(
                    visible = showTimeLayoutPill,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(180)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TimeLayoutSwitchPill(
                            variant = uiState.activeTemplate.timeLayoutVariant,
                            onClick = onToggleTimeLayout,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            ZoomSelectorBar(
                presets = uiState.zoomPresets,
                currentZoom = uiState.zoomRatio,
                iconRotation = iconRotation,
                onSelectZoom = onSelectZoom,
            )
        }

        AnimatedVisibility(
            visible = !uiState.isRecording && uiState.countdownSeconds <= 0,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
        ) {
            CaptureModeSelectorBar(
                captureMode = uiState.captureMode,
                onSelectCaptureMode = onSelectCaptureMode,
                onOpenGalleryStamper = onOpenGalleryStamper,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        CameraBottomDock(
            captureMode = uiState.captureMode,
            isCapturing = uiState.isCapturing,
            isRecording = uiState.isRecording,
            lastCapturedUri = uiState.lastCapturedUri,
            lastCapturedIsVideo = uiState.lastCapturedIsVideo,
            iconRotation = iconRotation,
            onOpenLastPhoto = { onOpenLastPhoto(uiState.lastCapturedUri) },
            onShutterClick = onShutterClick,
            onSwitchCamera = onSwitchCamera,
            onOpenWatermark = onOpenWatermark,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun TimeLayoutSwitchPill(
    variant: TimeLayoutVariant,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val isCompact = variant == TimeLayoutVariant.COMPACT_BAR
    val accent = colors.accent

    LiquidGlassSurface(
        shape = RoundedCornerShape(20.dp),
        selected = isCompact,
        darkColor = colors.surfaceElevated.copy(alpha = 0.9f),
        darkBorder = BorderStroke(
            width = 1.dp,
            color = if (isCompact) accent.copy(alpha = 0.8f) else colors.borderStroke.copy(alpha = 0.55f),
        ),
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.semantics {
            contentDescription = strings.cdSwitchTimeLayout
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ViewAgenda,
                contentDescription = null,
                tint = if (isCompact) accent else colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = if (isCompact) strings.timeLayoutVariant2 else strings.timeLayoutVariant1,
                color = if (isCompact) accent else colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun LocationStatusPill(
    locationState: LocationFixState,
    shortLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val isPinned = locationState == LocationFixState.MANUAL_PIN
    val isLocked = locationState == LocationFixState.LOCKED || isPinned
    val accent = when {
        isPinned -> colors.amber
        isLocked -> colors.accent
        else -> colors.textSecondary
    }
    val icon = when (locationState) {
        LocationFixState.PERMISSION_NEEDED, LocationFixState.UNAVAILABLE -> Icons.Rounded.LocationOff
        LocationFixState.SEARCHING, LocationFixState.IDLE -> Icons.Rounded.LocationSearching
        LocationFixState.LOCKED, LocationFixState.MANUAL_PIN -> Icons.Rounded.LocationOn
    }
    val text = when {
        shortLabel.isNotBlank() -> shortLabel
        locationState == LocationFixState.SEARCHING -> strings.gettingLocation
        else -> strings.tapToPickLocation
    }

    LiquidGlassSurface(
        shape = RoundedCornerShape(20.dp),
        selected = isPinned || isLocked,
        darkColor = colors.surfaceElevated.copy(alpha = 0.9f),
        darkBorder = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        onClick = onClick,
        role = Role.Button,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = strings.cdPickLocation,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = if (shortLabel.isNotBlank()) colors.textPrimary else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveWatermarkOverlay(
    template: com.timeedit.camera.domain.watermark.WatermarkTemplate,
    epochMillisFlow: StateFlow<Long>,
    containerSize: IntSize,
    weatherCode: Int?,
    uiRotationDegrees: Int,
    language: AppLanguage,
    onPositionCommitted: (WatermarkPosition) -> Unit,
    onBrandPositionCommitted: (WatermarkPosition) -> Unit,
    onLogoPositionCommitted: (WatermarkPosition) -> Unit,
    onResetPositions: () -> Unit,
    onTapTime: () -> Unit,
    onTapWatermark: () -> Unit,
) {
    val epochMillis by epochMillisFlow.collectAsStateWithLifecycle()
    WatermarkOverlay(
        template = template,
        epochMillis = epochMillis,
        containerSize = containerSize,
        weatherCode = weatherCode,
        uiRotationDegrees = uiRotationDegrees,
        language = language,
        onPositionCommitted = onPositionCommitted,
        onBrandPositionCommitted = onBrandPositionCommitted,
        onLogoPositionCommitted = onLogoPositionCommitted,
        onResetPositions = onResetPositions,
        onTapTime = onTapTime,
        onTapWatermark = onTapWatermark,
    )
}

@Composable
private fun TimeStatusPill(
    timeMode: TimeMode,
    epochMillisFlow: StateFlow<Long>,
    onClick: () -> Unit,
    onResetLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val epochMillis by epochMillisFlow.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val isCustom = timeMode != TimeMode.LIVE
    val accent = if (isCustom) colors.amber else colors.accent

    LiquidGlassSurface(
        shape = RoundedCornerShape(20.dp),
        selected = isCustom,
        darkColor = colors.surfaceElevated.copy(alpha = 0.9f),
        darkBorder = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
        onClick = onClick,
        role = Role.Button,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (isCustom) 4.dp else 10.dp,
                top = 5.dp,
                bottom = 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isCustom) strings.timeCustom else strings.timeLive,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = TimeProvider.formatClock(epochMillis),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            if (isCustom) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = strings.cdResetLiveTime,
                    tint = accent,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onResetLive)
                        .padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    captureMode: CaptureMode,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    flashMode: CameraFlashMode,
    hasFlashUnit: Boolean,
    aspectRatio: CameraAspectRatio,
    timer: CaptureTimer,
    photoQuality: PhotoQuality,
    effectiveVideoQuality: VideoQualityPreset,
    supportedVideoQualities: List<VideoQualityPreset>,
    isAudioEnabled: Boolean,
    iconRotation: Float,
    onToggleFlash: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleTimer: () -> Unit,
    onSelectPhotoQuality: (PhotoQuality) -> Unit,
    onSelectVideoQuality: (VideoQualityPreset) -> Unit,
    onToggleAudio: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (isRecording) {
        val pulseTransition = rememberInfiniteTransition(label = "recPulse")
        val dotAlpha by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "recDotAlpha",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Surface(
                color = Color(0xFFB71C1C).copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFFF8A80)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(Color.White, CircleShape),
                    )
                    Text(
                        text = "REC  ${formatRecordingDuration(recordingDurationSeconds)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (hasFlashUnit) {
                val colors = LocalTimeEditColors.current
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = if (flashMode == CameraFlashMode.TORCH) {
                            Icons.Rounded.FlashlightOn
                        } else {
                            Icons.Rounded.FlashOff
                        },
                        contentDescription = LocalAppStrings.current.cdTorchRecording,
                        tint = if (flashMode == CameraFlashMode.TORCH) colors.accent else colors.textPrimary,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }
        }
        return
    }

    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val flashText = when (flashMode) {
        CameraFlashMode.OFF -> strings.flashOff
        CameraFlashMode.AUTO -> strings.flashAuto
        CameraFlashMode.ON -> strings.flashOn
        CameraFlashMode.TORCH -> strings.flashTorch
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val flashIcon = when (flashMode) {
            CameraFlashMode.OFF -> Icons.Rounded.FlashOff
            CameraFlashMode.AUTO -> Icons.Rounded.FlashAuto
            CameraFlashMode.ON -> Icons.Rounded.FlashOn
            CameraFlashMode.TORCH -> Icons.Rounded.FlashlightOn
        }
        if (colors.isLight) {
            LiquidGlassSurface(
                shape = CircleShape,
                selected = flashMode != CameraFlashMode.OFF,
                elevated = false,
                onClick = onToggleFlash,
                role = Role.Button,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "${strings.cdFlash}: $flashText",
                        tint = if (flashMode == CameraFlashMode.OFF) colors.textPrimary else colors.accent,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }
        } else {
            IconButton(onClick = onToggleFlash) {
                Icon(
                    imageVector = flashIcon,
                    contentDescription = "${strings.cdFlash}: $flashText",
                    tint = if (flashMode == CameraFlashMode.OFF) colors.textPrimary else colors.accent,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }

        if (captureMode == CaptureMode.PHOTO) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                darkColor = colors.surfaceElevated,
                darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.6f)),
                onClick = onToggleAspectRatio,
                role = Role.Button,
            ) {
                Text(
                    text = aspectRatio.label,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .graphicsLayer { rotationZ = iconRotation },
                )
            }

            val timerText = if (timer == CaptureTimer.OFF) strings.timerOff else timer.label
            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                selected = timer != CaptureTimer.OFF,
                darkColor = colors.surfaceElevated,
                onClick = onToggleTimer,
                role = Role.Button,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = strings.cdTimer,
                        tint = if (timer == CaptureTimer.OFF) colors.textSecondary else colors.accent,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = iconRotation },
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    InPlaceRotatedLabel(
                        text = timerText,
                        rotationDegrees = iconRotation,
                        color = if (timer == CaptureTimer.OFF) colors.textSecondary else colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            QualityMenuPill(
                label = photoQuality.label,
                contentDescription = strings.cdPhotoQuality,
                iconRotation = iconRotation,
                options = PhotoQuality.entries,
                selectedOption = photoQuality,
                optionLabel = { it.label },
                onSelectOption = onSelectPhotoQuality,
            )
        } else {
            QualityMenuPill(
                label = effectiveVideoQuality.shortLabel,
                contentDescription = strings.cdVideoQuality,
                iconRotation = iconRotation,
                options = supportedVideoQualities,
                selectedOption = effectiveVideoQuality,
                optionLabel = { it.label },
                onSelectOption = onSelectVideoQuality,
            )

            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                selected = isAudioEnabled,
                darkColor = colors.surfaceElevated,
                darkBorder = if (isAudioEnabled) BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)) else null,
                onClick = onToggleAudio,
                role = Role.Button,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (isAudioEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = strings.cdMic,
                        tint = if (isAudioEnabled) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = iconRotation },
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    InPlaceRotatedLabel(
                        text = if (isAudioEnabled) strings.micOn else strings.micOff,
                        rotationDegrees = iconRotation,
                        color = if (isAudioEnabled) colors.accent else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        if (colors.isLight) {
            LiquidGlassSurface(
                shape = CircleShape,
                elevated = false,
                onClick = onOpenSettings,
                role = Role.Button,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.cdSettings,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }
        } else {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = strings.cdSettings,
                    tint = colors.textPrimary,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
    }
}

@Composable
private fun <T> QualityMenuPill(
    label: String,
    contentDescription: String,
    iconRotation: Float,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelectOption: (T) -> Unit,
) {
    val colors = LocalTimeEditColors.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        LiquidGlassSurface(
            shape = RoundedCornerShape(18.dp),
            selected = expanded,
            darkColor = colors.surfaceElevated,
            darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.6f)),
            onClick = { if (options.size > 1) expanded = true },
            role = Role.Button,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            ) {
                InPlaceRotatedLabel(
                    text = label,
                    rotationDegrees = iconRotation,
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.glassDialogFill,
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            color = if (isSelected) colors.accent else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectOption(option)
                    },
                )
            }
        }
    }
}

internal fun splitInPlaceRotationTokens(text: String): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (' ' in trimmed) {
        return trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    val hasCjk = trimmed.any { ch -> Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN }
    if (hasCjk && trimmed.length >= 3) {
        return trimmed.chunked(2)
    }
    return listOf(trimmed)
}

@Composable
private fun InPlaceRotatedLabel(
    text: String,
    rotationDegrees: Float,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
) {
    val tokens = remember(text) { splitInPlaceRotationTokens(text) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tokens.forEach { token ->
            Text(
                text = token,
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer { rotationZ = rotationDegrees },
            )
        }
    }
}

@Composable
private fun CaptureModeSelectorBar(
    captureMode: CaptureMode,
    onSelectCaptureMode: (CaptureMode) -> Unit,
    onOpenGalleryStamper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val activeIndex = if (captureMode == CaptureMode.VIDEO) 0 else 1
    val gapPx = with(LocalDensity.current) { 20.dp.roundToPx() }
    val dragThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val centerXAnim = remember { Animatable(0f) }
    var targetCenterX by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(targetCenterX) {
        if (targetCenterX >= 0f) {
            if (centerXAnim.value == 0f && abs(centerXAnim.value - targetCenterX) > 1f) {
                centerXAnim.snapTo(targetCenterX)
            } else {
                centerXAnim.animateTo(
                    targetValue = targetCenterX,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val swipeModifier = Modifier.pointerInput(captureMode) {
        var totalDrag = 0f
        var triggered = false
        detectHorizontalDragGestures(
            onDragStart = {
                totalDrag = 0f
                triggered = false
            },
            onHorizontalDrag = { _, dragAmount ->
                if (triggered) return@detectHorizontalDragGestures
                totalDrag += dragAmount
                if (totalDrag < -dragThresholdPx) {
                    triggered = true
                    if (captureMode == CaptureMode.VIDEO) {
                        onSelectCaptureMode(CaptureMode.PHOTO)
                    } else {
                        onOpenGalleryStamper()
                    }
                } else if (totalDrag > dragThresholdPx) {
                    triggered = true
                    if (captureMode == CaptureMode.PHOTO) {
                        onSelectCaptureMode(CaptureMode.VIDEO)
                    }
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(swipeModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                CarouselModeLabel(
                    text = strings.modeVideo,
                    selected = activeIndex == 0,
                    onClick = { onSelectCaptureMode(CaptureMode.VIDEO) },
                )
                CarouselModeLabel(
                    text = strings.modePhoto,
                    selected = activeIndex == 1,
                    onClick = { onSelectCaptureMode(CaptureMode.PHOTO) },
                )
                CarouselModeLabel(
                    text = strings.modeStampPhotos,
                    selected = false,
                    onClick = onOpenGalleryStamper,
                )
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val maxH = placeables.maxOfOrNull { it.height } ?: 0

            val xs = IntArray(placeables.size)
            val centers = FloatArray(placeables.size)
            var cursorX = 0
            placeables.forEachIndexed { idx, p ->
                xs[idx] = cursorX
                centers[idx] = cursorX + p.width / 2f
                cursorX += p.width + gapPx
            }
            val desiredCenter = centers[activeIndex]
            if (abs(targetCenterX - desiredCenter) > 0.5f) {
                targetCenterX = desiredCenter
            }
            val effectiveCenter = if (centerXAnim.value > 0f) centerXAnim.value else desiredCenter
            val shiftPx = ((constraints.maxWidth / 2f) - effectiveCenter).roundToInt()

            layout(constraints.maxWidth, maxH) {
                placeables.forEachIndexed { idx, p ->
                    val y = (maxH - p.height) / 2
                    p.placeRelative(xs[idx] + shiftPx, y)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(colors.accent, CircleShape),
        )
    }
}

@Composable
private fun CarouselModeLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTimeEditColors.current
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 180),
        label = "carouselTextColor",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (selected) 14.sp else 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ZoomSelectorBar(
    presets: List<Float>,
    currentZoom: Float,
    iconRotation: Float,
    onSelectZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTimeEditColors.current
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        elevated = false,
        darkColor = colors.surfaceElevated.copy(alpha = 0.85f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (preset in presets) {
                val isSelected = abs(currentZoom - preset) < 0.15f
                if (isSelected) {
                    LiquidGlassSurface(
                        shape = CircleShape,
                        selected = true,
                        elevated = false,
                        darkColor = colors.accentContainer,
                        darkBorder = BorderStroke(1.dp, colors.accent),
                        onClick = { onSelectZoom(preset) },
                        role = Role.Button,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = formatZoomLabel(currentZoom),
                                color = colors.accentSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                            )
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onSelectZoom(preset) },
                    ) {
                        Text(
                            text = formatZoomLabel(preset),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraBottomDock(
    captureMode: CaptureMode,
    isCapturing: Boolean,
    isRecording: Boolean,
    lastCapturedUri: Uri?,
    lastCapturedIsVideo: Boolean,
    iconRotation: Float,
    onOpenLastPhoto: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenWatermark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shutterInteraction = remember { MutableInteractionSource() }
    val isPressed by shutterInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "shutterScale",
    )

    val context = LocalContext.current
    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, lastCapturedUri, lastCapturedIsVideo) {
        value = if (lastCapturedUri != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (lastCapturedIsVideo) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, lastCapturedUri)
                            retriever.getFrameAtTime(0L)?.asImageBitmap()
                        } finally {
                            runCatching { retriever.release() }
                        }
                    } else {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                        context.contentResolver.openInputStream(lastCapturedUri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                        }
                    }
                }.getOrNull()
            }
        } else null
    }

    val colors = LocalTimeEditColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(14.dp),
            selected = lastCapturedUri != null,
            darkColor = colors.surfaceElevated,
            darkBorder = BorderStroke(
                width = 1.5.dp,
                color = if (lastCapturedUri != null) colors.accent else colors.borderStroke.copy(alpha = 0.5f),
            ),
            onClick = onOpenLastPhoto,
            enabled = !isRecording,
            role = Role.Button,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(50.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = iconRotation },
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!,
                        contentDescription = if (lastCapturedIsVideo) LocalAppStrings.current.cdOpenLastVideo else LocalAppStrings.current.cdOpenLastPhoto,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (lastCapturedIsVideo) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.scrim),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = LocalAppStrings.current.cdOpenGallery,
                        tint = if (lastCapturedUri != null) colors.accentSoft else colors.textSecondary,
                    )
                }
            }
        }

        val recordRed = Color(0xFFFF3B30)
        val outerRingColor by animateColorAsState(
            targetValue = if (captureMode == CaptureMode.VIDEO && isRecording) recordRed else colors.accent,
            animationSpec = tween(durationMillis = 180),
            label = "outerRingColor",
        )
        val innerColor by animateColorAsState(
            targetValue = when {
                isCapturing -> colors.accentSoft.copy(alpha = 0.5f)
                captureMode == CaptureMode.VIDEO -> recordRed
                colors.isLight -> Color(0xFFFFFBF7)
                else -> Color.White
            },
            animationSpec = tween(durationMillis = 180),
            label = "shutterInnerColor",
        )
        val innerSize by animateDpAsState(
            targetValue = if (captureMode == CaptureMode.VIDEO && isRecording) 28.dp else 61.dp,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "shutterInnerSize",
        )
        val innerCorner by animateDpAsState(
            targetValue = if (captureMode == CaptureMode.VIDEO && isRecording) 7.dp else 31.dp,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "shutterInnerCorner",
        )
        val strings = LocalAppStrings.current
        val shutterCd = when {
            captureMode == CaptureMode.PHOTO -> strings.cdShutterPhoto
            isRecording -> strings.cdStopVideo
            else -> strings.cdStartVideo
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (colors.isLight) Modifier.background(colors.accentContainer.copy(alpha = 0.55f), CircleShape)
                    else Modifier,
                )
                .border(3.5.dp, outerRingColor, CircleShape)
                .padding(6.dp)
                .semantics { contentDescription = shutterCd }
                .clickable(
                    interactionSource = shutterInteraction,
                    indication = null,
                    enabled = !isCapturing,
                    role = Role.Button,
                    onClick = onShutterClick,
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(innerColor)
                    .then(
                        if (colors.isLight && captureMode == CaptureMode.PHOTO) {
                            Modifier.border(1.dp, colors.borderStroke.copy(alpha = 0.65f), RoundedCornerShape(innerCorner))
                        } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = colors.accent,
                        strokeWidth = 3.dp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassSurface(
                shape = CircleShape,
                darkColor = colors.surfaceElevated,
                onClick = onOpenWatermark,
                role = Role.Button,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMosaic,
                        contentDescription = LocalAppStrings.current.cdWatermarkTemplates,
                        tint = colors.accent,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }

            LiquidGlassSurface(
                shape = CircleShape,
                darkColor = colors.surfaceElevated,
                onClick = onSwitchCamera,
                enabled = !isRecording,
                role = Role.Button,
                modifier = Modifier
                    .size(46.dp)
                    .graphicsLayer { alpha = if (isRecording) 0.35f else 1f },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = LocalAppStrings.current.cdSwitchCamera,
                        tint = colors.textPrimary,
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingStatusToast(
    message: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTimeEditColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { -it / 2 },
        exit = fadeOut(animationSpec = tween(220)) + slideOutVertically(animationSpec = tween(220)) { -it / 2 },
        modifier = modifier,
    ) {
        val accent = if (isError) colors.error else colors.accent
        LiquidGlassSurface(
            shape = RoundedCornerShape(24.dp),
            darkColor = colors.surfaceElevated.copy(alpha = 0.92f),
            darkBorder = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = message.orEmpty(),
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickSettingsDialog(
    selectedLanguage: AppLanguage,
    selectedThemeMode: AppThemeMode,
    photoQuality: PhotoQuality,
    effectiveVideoQuality: VideoQualityPreset,
    supportedVideoQualities: List<VideoQualityPreset>,
    showGrid: Boolean,
    saveOriginalPhoto: Boolean,
    mirrorFrontCamera: Boolean,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectPhotoQuality: (PhotoQuality) -> Unit,
    onSelectVideoQuality: (VideoQualityPreset) -> Unit,
    onToggleShowGrid: (Boolean) -> Unit,
    onToggleSaveOriginal: (Boolean) -> Unit,
    onToggleMirrorFront: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.glassDialogFill,
        title = { Text(strings.settingsTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.languageLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val isSelected = lang == selectedLanguage
                            LiquidGlassSurface(
                                shape = RoundedCornerShape(10.dp),
                                selected = isSelected,
                                elevated = false,
                                darkColor = if (isSelected) colors.accentContainer else colors.surface,
                                darkBorder = BorderStroke(
                                    width = if (isSelected) 1.4.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.borderStroke.copy(alpha = 0.45f),
                                ),
                                onClick = { onSelectLanguage(lang) },
                                role = Role.RadioButton,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = lang.nativeName,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.themeLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val isSelected = mode == selectedThemeMode
                            val label = if (mode == AppThemeMode.DARK) strings.themeDark else strings.themeLight
                            LiquidGlassSurface(
                                shape = RoundedCornerShape(10.dp),
                                selected = isSelected,
                                elevated = false,
                                darkColor = if (isSelected) colors.accentContainer else colors.surface,
                                darkBorder = BorderStroke(
                                    width = if (isSelected) 1.4.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.borderStroke.copy(alpha = 0.45f),
                                ),
                                onClick = { onSelectThemeMode(mode) },
                                role = Role.RadioButton,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.photoQualityLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PhotoQuality.entries.forEach { quality ->
                            val isSelected = quality == photoQuality
                            LiquidGlassSurface(
                                shape = RoundedCornerShape(10.dp),
                                selected = isSelected,
                                elevated = false,
                                darkColor = if (isSelected) colors.accentContainer else colors.surface,
                                darkBorder = BorderStroke(
                                    width = if (isSelected) 1.4.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.borderStroke.copy(alpha = 0.45f),
                                ),
                                onClick = { onSelectPhotoQuality(quality) },
                                role = Role.RadioButton,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = quality.label,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.videoQualityLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    supportedVideoQualities.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = preset == effectiveVideoQuality
                                LiquidGlassSurface(
                                    shape = RoundedCornerShape(10.dp),
                                    selected = isSelected,
                                    elevated = false,
                                    darkColor = if (isSelected) colors.accentContainer else colors.surface,
                                    darkBorder = BorderStroke(
                                        width = if (isSelected) 1.4.dp else 1.dp,
                                        color = if (isSelected) colors.accent else colors.borderStroke.copy(alpha = 0.45f),
                                    ),
                                    onClick = { onSelectVideoQuality(preset) },
                                    role = Role.RadioButton,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = preset.shortLabel,
                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (rowPresets.size == 1 && supportedVideoQualities.size > 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.showGridTitle, color = colors.textPrimary, fontSize = 14.sp)
                        Text(
                            strings.showGridSubtitle,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(checked = showGrid, onCheckedChange = onToggleShowGrid)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.saveOriginalTitle, color = colors.textPrimary, fontSize = 14.sp)
                        Text(
                            strings.saveOriginalSubtitle,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(checked = saveOriginalPhoto, onCheckedChange = onToggleSaveOriginal)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.mirrorFrontTitle, color = colors.textPrimary, fontSize = 14.sp)
                        Text(
                            strings.mirrorFrontSubtitle,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(checked = mirrorFrontCamera, onCheckedChange = onToggleMirrorFront)
                }
                Text(
                    text = strings.versionFooter,
                    color = colors.accentSoft,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.done, color = colors.accent) }
        },
    )
}

@Composable
private fun PermissionGateScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassBackdrop(colors, darkColor = colors.surface)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            shape = RoundedCornerShape(20.dp),
            darkColor = colors.surfaceElevated,
            darkBorder = BorderStroke(1.dp, colors.borderStroke.copy(alpha = 0.6f)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = strings.cameraPermTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = strings.cameraPermBody,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.grantCameraPerm, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    border = BorderStroke(1.dp, colors.borderStroke),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.openAppSettings, color = colors.accentSoft)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CameraScreenPreview() {
    TimeEditTheme {
        CameraScreenContent(
            uiState = CameraUiState(
                zoomPresets = listOf(0.6f, 1.0f, 2.0f),
                showGrid = true,
            ),
            surfaceRequest = null,
            epochMillisFlow = remember { MutableStateFlow(1795615500000L) },
            onToggleFlash = {},
            onToggleAspectRatio = {},
            onToggleTimer = {},
            onSelectPhotoQuality = {},
            onSelectVideoQuality = {},
            onToggleAudio = {},
            onSwitchCamera = {},
            onSelectZoom = {},
            onPinchZoom = {},
            onTapToFocus = { _, _, _, _ -> },
            onWatermarkPositionCommitted = {},
            onBrandPositionCommitted = {},
            onLogoPositionCommitted = {},
            onResetWatermarkPositions = {},
            onOpenTimeEdit = {},
            onOpenLocationPicker = {},
            onResetLiveTime = {},
            onToggleTimeLayout = {},
            onSelectCaptureMode = {},
            onOpenGalleryStamper = {},
            onShutterClick = {},
            onOpenLastPhoto = {},
            onOpenWatermark = {},
            onOpenSettings = {},
        )
    }
}
