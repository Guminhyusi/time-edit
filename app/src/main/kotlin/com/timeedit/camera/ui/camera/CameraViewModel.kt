/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.AppStrings
import com.timeedit.camera.core.i18n.AppStringsProvider
import com.timeedit.camera.core.location.FormattedLocationAddress
import com.timeedit.camera.core.location.LiveLocationSnapshot
import com.timeedit.camera.core.location.LocationEngine
import com.timeedit.camera.core.location.LocationFixState
import com.timeedit.camera.core.location.PlaceSearchResult
import com.timeedit.camera.core.location.TimemarkLocationFormatter
import com.timeedit.camera.core.location.WeatherClient
import com.timeedit.camera.core.location.WeatherSnapshot
import com.timeedit.camera.core.sensors.CompassSensorManager
import com.timeedit.camera.core.sensors.DeviceOrientationMonitor
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.data.TimeEditPreferences
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.CoordinateFormatStyle
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.WatermarkJsonCodec
import com.timeedit.camera.domain.watermark.WatermarkPosition
import com.timeedit.camera.domain.watermark.WatermarkStyleId
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.engine.camera.CameraAspectRatio
import com.timeedit.camera.engine.camera.CameraController
import com.timeedit.camera.engine.camera.CameraFlashMode
import com.timeedit.camera.engine.camera.CameraUiState
import com.timeedit.camera.engine.camera.CaptureMode
import com.timeedit.camera.engine.camera.PhotoQuality
import com.timeedit.camera.engine.camera.VideoQualityPreset
import com.timeedit.camera.engine.camera.VideoWatermarkSnapshot
import com.timeedit.camera.engine.camera.computeZoomPresets
import com.timeedit.camera.engine.renderer.BrandAssetPackLoader
import com.timeedit.camera.engine.renderer.WatermarkCanvasRenderer
import com.timeedit.camera.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val cameraController = CameraController(application)
    private val preferences = TimeEditPreferences(application)
    private val locationEngine = LocationEngine(application)
    private val compassSensorManager = CompassSensorManager(application)
    private val orientationMonitor = DeviceOrientationMonitor(application)

    val surfaceRequest = cameraController.surfaceRequest

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _currentEpochMillis = MutableStateFlow(System.currentTimeMillis())
    val currentEpochMillis: StateFlow<Long> = _currentEpochMillis.asStateFlow()

    private var countdownJob: Job? = null
    private var bindCameraJob: Job? = null
    private var templateSaveJob: Job? = null
    private var weatherFetchJob: Job? = null
    private var lastWeatherKey: String? = null
    private val manualTelemetryOverrides = mutableSetOf<String>()
    private var sensorsActive = false
    private var compassRunning = false

    private var videoSnapshotVersion = 0L
    private var cachedVideoLogoUri: String? = null
    private var cachedVideoLogoBitmap: Bitmap? = null
    private var cachedVideoBrandUri: String? = null
    private var cachedVideoBrandPreset: BrandBadgePreset? = null
    private var cachedVideoBrandBitmap: Bitmap? = null
    private var videoAssetJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.preferencesFlow.collect { saved ->
                locationEngine.setLanguage(saved.appLanguage)
                _uiState.update { prev ->
                    val mergedTemplate = mergeLiveTelemetry(
                        base = saved.activeTemplate,
                        liveLoc = prev.liveLocation,
                        azimuthDeg = prev.liveAzimuthDegrees,
                        weather = prev.liveWeather,
                        language = saved.appLanguage,
                    )
                    prev.copy(
                        appLanguage = saved.appLanguage,
                        themeMode = saved.themeMode,
                        timeConfig = saved.timeConfig,
                        activeTemplate = mergedTemplate,
                        customPresets = saved.customPresets,
                        saveOriginalPhoto = saved.saveOriginalPhoto,
                        mirrorFrontCamera = saved.mirrorFrontCamera,
                        showGrid = saved.showGrid,
                        photoQuality = saved.photoQuality,
                        preferredVideoQuality = saved.videoQuality,
                        effectiveVideoQuality = VideoQualityPreset.resolveEffectiveVideoQuality(
                            preferred = saved.videoQuality,
                            supported = prev.supportedVideoQualities,
                        ),
                    )
                }
                _currentEpochMillis.value = TimeProvider.resolveEpochMillis(
                    nowMillis = System.currentTimeMillis(),
                    config = saved.timeConfig,
                )
                syncCompassSensorState(_uiState.value.activeTemplate.showCompass)
                syncVideoWatermarkSnapshot(_uiState.value)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                _currentEpochMillis.value = TimeProvider.resolveEpochMillis(
                    nowMillis = System.currentTimeMillis(),
                    config = _uiState.value.timeConfig,
                )
                delay(500L)
            }
        }
    }

    private fun currentStrings(): AppStrings = AppStringsProvider.forLanguage(_uiState.value.appLanguage)

    fun bindCamera(lifecycleOwner: LifecycleOwner) {
        val state = _uiState.value
        syncVideoWatermarkSnapshot(state)
        bindCameraJob?.cancel()
        bindCameraJob = viewModelScope.launch {
            cameraController.bind(
                lifecycleOwner = lifecycleOwner,
                captureMode = state.captureMode,
                isFrontCamera = state.isFrontCamera,
                aspectRatio = state.aspectRatio,
                flashMode = state.flashMode,
                preferredVideoQuality = state.preferredVideoQuality,
                onVideoCapabilitiesChanged = { supported, effective ->
                    _uiState.update { prev ->
                        prev.copy(
                            supportedVideoQualities = supported,
                            effectiveVideoQuality = effective,
                        )
                    }
                },
                onZoomStateChanged = { current, min, max, hasFlash ->
                    _uiState.update { prev ->
                        prev.copy(
                            zoomRatio = current,
                            minZoomRatio = min,
                            maxZoomRatio = max,
                            hasFlashUnit = hasFlash,
                            zoomPresets = computeZoomPresets(min, max),
                        )
                    }
                },
            ).onFailure { error ->
                val strings = currentStrings()
                _uiState.update { it.copy(errorMessage = error.message ?: strings.cameraInitFailed) }
            }
        }
    }

    fun setCaptureMode(mode: CaptureMode, lifecycleOwner: LifecycleOwner) {
        val state = _uiState.value
        if (state.isRecording || state.isCapturing || state.captureMode == mode) return
        countdownJob?.cancel()
        countdownJob = null
        val nextAspect = if (mode == CaptureMode.VIDEO && state.aspectRatio == CameraAspectRatio.RATIO_1_1) {
            CameraAspectRatio.RATIO_16_9
        } else {
            state.aspectRatio
        }
        val nextFlash = if (mode == CaptureMode.VIDEO &&
            state.flashMode != CameraFlashMode.TORCH
        ) {
            CameraFlashMode.OFF
        } else {
            state.flashMode
        }
        _uiState.update {
            it.copy(
                captureMode = mode,
                countdownSeconds = 0,
                aspectRatio = nextAspect,
                flashMode = nextFlash,
            )
        }
        bindCamera(lifecycleOwner)
    }

    fun toggleAudioEnabled() {
        val state = _uiState.value
        if (state.isRecording) return
        _uiState.update { it.copy(isAudioEnabled = !state.isAudioEnabled) }
    }

    fun cycleFlash() {
        val state = _uiState.value
        if (state.captureMode == CaptureMode.VIDEO) {
            if (!state.hasFlashUnit) return
            val resolved = if (state.flashMode == CameraFlashMode.TORCH) {
                CameraFlashMode.OFF
            } else {
                CameraFlashMode.TORCH
            }
            _uiState.update { it.copy(flashMode = resolved) }
            cameraController.updateFlash(resolved)
            return
        }
        val next = state.flashMode.next()
        val resolved = if (!state.hasFlashUnit && next == CameraFlashMode.TORCH) {
            next.next()
        } else {
            next
        }
        _uiState.update { it.copy(flashMode = resolved) }
        cameraController.updateFlash(resolved)
    }

    fun cycleAspectRatio(lifecycleOwner: LifecycleOwner) {
        _uiState.update { it.copy(aspectRatio = it.aspectRatio.next()) }
        bindCamera(lifecycleOwner)
    }

    fun cycleTimer() {
        _uiState.update { it.copy(timer = it.timer.next()) }
    }

    fun setShowGrid(enabled: Boolean) {
        _uiState.update { it.copy(showGrid = enabled) }
        viewModelScope.launch {
            preferences.setShowGrid(enabled)
        }
    }

    fun setPhotoQuality(quality: PhotoQuality) {
        if (_uiState.value.photoQuality == quality) return
        _uiState.update { it.copy(photoQuality = quality) }
        viewModelScope.launch {
            preferences.setPhotoQuality(quality)
        }
    }

    fun setVideoQuality(
        preset: VideoQualityPreset,
        lifecycleOwner: LifecycleOwner,
    ) {
        val state = _uiState.value
        if (state.isRecording) return
        val resolved = VideoQualityPreset.resolveEffectiveVideoQuality(
            preferred = preset,
            supported = state.supportedVideoQualities,
        )
        _uiState.update {
            it.copy(
                preferredVideoQuality = preset,
                effectiveVideoQuality = resolved,
            )
        }
        viewModelScope.launch {
            preferences.setVideoQuality(preset)
        }
        if (state.captureMode == CaptureMode.VIDEO) {
            bindCamera(lifecycleOwner)
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner) {
        _uiState.update {
            val nextFront = !it.isFrontCamera
            val nextFlash = if (nextFront && it.flashMode == CameraFlashMode.TORCH) {
                CameraFlashMode.OFF
            } else {
                it.flashMode
            }
            it.copy(
                isFrontCamera = nextFront,
                flashMode = nextFlash,
                zoomRatio = 1.0f,
            )
        }
        bindCamera(lifecycleOwner)
        compassSensorManager.updateFrontCamera(_uiState.value.isFrontCamera)
    }

    fun startLiveSensors() {
        sensorsActive = true
        orientationMonitor.start { _, uiRot, surfaceRot ->
            _uiState.update { it.copy(uiRotationDegrees = uiRot) }
            cameraController.updateTargetRotation(surfaceRot)
        }

        val current = _uiState.value
        locationEngine.setLanguage(current.appLanguage)
        syncCompassSensorState(current.activeTemplate.showCompass)

        locationEngine.startUpdates(
            scope = viewModelScope,
            onStateChanged = { fixState ->
                _uiState.update { prev ->
                    val effectiveState = if (!prev.activeTemplate.useLiveLocation && prev.activeTemplate.pinnedLatitude != null) {
                        LocationFixState.MANUAL_PIN
                    } else fixState
                    prev.copy(locationState = effectiveState)
                }
            },
            onLocationUpdate = { snapshot ->
                _uiState.update { prev ->
                    val merged = mergeLiveTelemetry(
                        base = prev.activeTemplate,
                        liveLoc = snapshot,
                        azimuthDeg = prev.liveAzimuthDegrees,
                        weather = prev.liveWeather,
                        language = prev.appLanguage,
                    )
                    prev.copy(
                        liveLocation = snapshot,
                        locationState = if (prev.activeTemplate.useLiveLocation) LocationFixState.LOCKED else LocationFixState.MANUAL_PIN,
                        activeTemplate = merged,
                    )
                }
                val active = _uiState.value.activeTemplate
                if (active.useLiveLocation) {
                    triggerWeatherFetch(snapshot.latitude, snapshot.longitude)
                }
            },
        )
    }

    fun stopLiveSensors() {
        sensorsActive = false
        orientationMonitor.stop()
        locationEngine.stopUpdates()
        syncCompassSensorState(showCompass = false)
    }

    private fun syncCompassSensorState(showCompass: Boolean) {
        val shouldRun = sensorsActive && showCompass
        if (shouldRun && !compassRunning) {
            compassRunning = true
            compassSensorManager.start(isFrontCamera = _uiState.value.isFrontCamera) { deg ->
                _uiState.update { prev ->
                    val compassText = TimemarkLocationFormatter.formatCompass(deg)
                    prev.copy(
                        liveAzimuthDegrees = deg,
                        activeTemplate = prev.activeTemplate.copy(compass = compassText),
                    )
                }
            }
        } else if (!shouldRun && compassRunning) {
            compassRunning = false
            compassSensorManager.stop()
        }
    }

    override fun onCleared() {
        stopLiveSensors()
        cameraController.release()
        super.onCleared()
    }

    fun applyManualLocation(
        lat: Double,
        lng: Double,
        address: FormattedLocationAddress,
    ) {
        val prevState = _uiState.value
        val updated = prevState.activeTemplate.copy(
            useLiveLocation = false,
            pinnedLatitude = lat,
            pinnedLongitude = lng,
            address = address.fullAddress,
            smallPosition = address.smallPosition,
            largePosition = address.largePosition,
            coordinates = TimemarkLocationFormatter.formatCoordinates(lat, lng, prevState.activeTemplate.coordinateFormat),
        )
        _uiState.update {
            it.copy(
                locationState = LocationFixState.MANUAL_PIN,
                activeTemplate = updated,
            )
        }
        persistTemplateDebounced(updated, immediate = true)
        triggerWeatherFetch(lat, lng)
    }

    fun resumeLiveGps() {
        manualTelemetryOverrides.clear()
        val currentLoc = _uiState.value.liveLocation
        val resetTemplate = mergeLiveTelemetry(
            base = _uiState.value.activeTemplate.copy(
                useLiveLocation = true,
                pinnedLatitude = null,
                pinnedLongitude = null,
            ),
            liveLoc = currentLoc,
            azimuthDeg = _uiState.value.liveAzimuthDegrees,
            weather = _uiState.value.liveWeather,
            language = _uiState.value.appLanguage,
        )
        _uiState.update {
            it.copy(
                activeTemplate = resetTemplate,
                locationState = if (currentLoc != null) LocationFixState.LOCKED else LocationFixState.SEARCHING,
            )
        }
        persistTemplateDebounced(resetTemplate, immediate = true)
        startLiveSensors()
        if (currentLoc != null) {
            triggerWeatherFetch(currentLoc.latitude, currentLoc.longitude)
        }
    }

    suspend fun searchPlaces(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null,
    ): List<PlaceSearchResult> {
        val live = _uiState.value.liveLocation
        return locationEngine.searchPlaces(
            query = query,
            nearLat = nearLat ?: live?.latitude,
            nearLng = nearLng ?: live?.longitude,
        )
    }

    suspend fun reverseGeocodePin(lat: Double, lng: Double): FormattedLocationAddress =
        locationEngine.reverseGeocode(lat, lng)

    fun updateCoordinateFormat(style: CoordinateFormatStyle) {
        updateActiveTemplate { t ->
            val latLng = resolveEffectiveLatLng(_uiState.value, t)
            val reformatted = latLng?.let { (lat, lng) ->
                TimemarkLocationFormatter.formatCoordinates(lat, lng, style)
            } ?: t.coordinates
            t.copy(coordinateFormat = style, coordinates = reformatted)
        }
    }

    fun setZoomRatio(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    fun scaleZoomBy(factor: Float) {
        val current = _uiState.value.zoomRatio
        cameraController.setZoomRatio(current * factor)
    }

    fun focusAt(surfaceX: Float, surfaceY: Float, surfaceWidth: Float, surfaceHeight: Float) {
        cameraController.focusAt(surfaceX, surfaceY, surfaceWidth, surfaceHeight)
    }

    fun updateTimeConfig(config: TimeConfig) {
        _uiState.update { it.copy(timeConfig = config) }
        _currentEpochMillis.value = TimeProvider.resolveEpochMillis(
            nowMillis = System.currentTimeMillis(),
            config = config,
        )
        syncVideoWatermarkSnapshot(_uiState.value)
        viewModelScope.launch {
            preferences.saveTimeConfig(config)
        }
    }

    fun resetToLiveTime() {
        updateTimeConfig(TimeConfig())
    }

    fun toggleTimeLayoutVariant() {
        val current = _uiState.value.activeTemplate
        if (current.style != WatermarkStyleId.TIME_LOCATION) return
        updateActiveTemplate { it.copy(timeLayoutVariant = it.timeLayoutVariant.next()) }
    }

    fun selectTemplate(template: WatermarkTemplate) {
        manualTelemetryOverrides.clear()
        val current = _uiState.value
        val curT = current.activeTemplate
        val baseWithPin = if (!curT.useLiveLocation && curT.pinnedLatitude != null && curT.pinnedLongitude != null) {
            template.copy(
                useLiveLocation = false,
                pinnedLatitude = curT.pinnedLatitude,
                pinnedLongitude = curT.pinnedLongitude,
                address = curT.address,
                smallPosition = curT.smallPosition,
                largePosition = curT.largePosition,
                coordinates = TimemarkLocationFormatter.formatCoordinates(curT.pinnedLatitude, curT.pinnedLongitude, template.coordinateFormat),
                coordinateFormat = curT.coordinateFormat,
            )
        } else {
            template
        }
        val merged = mergeLiveTelemetry(
            base = baseWithPin,
            liveLoc = current.liveLocation,
            azimuthDeg = current.liveAzimuthDegrees,
            weather = current.liveWeather,
            language = current.appLanguage,
        )
        _uiState.update { it.copy(activeTemplate = merged) }
        persistTemplateDebounced(merged, immediate = true)
        syncCompassSensorState(merged.showCompass)
    }

    fun setAppLanguage(language: AppLanguage) {
        val current = _uiState.value
        if (current.appLanguage == language) return
        locationEngine.setLanguage(language)
        val localizedTemplate = DefaultWatermarkTemplates.localizeTemplateForLanguage(
            current = current.activeTemplate,
            targetLanguage = language,
        )
        val merged = mergeLiveTelemetry(
            base = localizedTemplate,
            liveLoc = current.liveLocation,
            azimuthDeg = current.liveAzimuthDegrees,
            weather = current.liveWeather,
            language = language,
        )
        _uiState.update {
            it.copy(
                appLanguage = language,
                activeTemplate = merged,
            )
        }
        syncVideoWatermarkSnapshot(_uiState.value)
        viewModelScope.launch {
            preferences.setAppLanguage(language)
            preferences.saveActiveTemplate(merged)
        }
        val liveLoc = current.liveLocation
        if (liveLoc != null && merged.useLiveLocation) {
            viewModelScope.launch {
                val refreshedAddress = locationEngine.reverseGeocode(liveLoc.latitude, liveLoc.longitude)
                val updatedLoc = liveLoc.copy(address = refreshedAddress)
                _uiState.update { prev ->
                    if (prev.appLanguage != language) return@update prev
                    val updatedTemplate = mergeLiveTelemetry(
                        base = prev.activeTemplate,
                        liveLoc = updatedLoc,
                        azimuthDeg = prev.liveAzimuthDegrees,
                        weather = prev.liveWeather,
                        language = language,
                    )
                    prev.copy(liveLocation = updatedLoc, activeTemplate = updatedTemplate)
                }
                syncVideoWatermarkSnapshot(_uiState.value)
            }
        }
    }

    fun updateActiveTemplate(transform: (WatermarkTemplate) -> WatermarkTemplate) {
        val prev = _uiState.value
        val prevT = prev.activeTemplate
        val updated = transform(prevT)
        if (updated.weather != prevT.weather) manualTelemetryOverrides += "weather"
        if (updated.altitude != prevT.altitude) manualTelemetryOverrides += "altitude"
        if (updated.compass != prevT.compass) manualTelemetryOverrides += "compass"
        if (updated.useLiveLocation && !prevT.useLiveLocation) {
            manualTelemetryOverrides.clear()
        }
        _uiState.update { it.copy(activeTemplate = updated) }
        persistTemplateDebounced(updated, immediate = false)
        syncCompassSensorState(updated.showCompass)
    }

    fun resetCurrentTemplateToDefault() {
        manualTelemetryOverrides.clear()
        val current = _uiState.value
        val strings = AppStringsProvider.forLanguage(current.appLanguage)
        val base = DefaultWatermarkTemplates.defaultTemplateForId(
            id = current.activeTemplate.id,
            style = current.activeTemplate.style,
            language = current.appLanguage,
        )
        val merged = mergeLiveTelemetry(
            base = base,
            liveLoc = current.liveLocation,
            azimuthDeg = current.liveAzimuthDegrees,
            weather = current.liveWeather,
            language = current.appLanguage,
        )
        _uiState.update {
            it.copy(
                activeTemplate = merged,
                locationState = if (current.liveLocation != null) LocationFixState.LOCKED else LocationFixState.SEARCHING,
                statusMessage = strings.resetTemplateDone,
            )
        }
        persistTemplateDebounced(merged, immediate = true)
        syncCompassSensorState(merged.showCompass)
    }

    fun updateWatermarkPosition(position: WatermarkPosition) {
        val updated = _uiState.value.activeTemplate.copy(position = position)
        _uiState.update { it.copy(activeTemplate = updated) }
        persistTemplateDebounced(updated, immediate = true)
    }

    fun updateBrandPosition(position: WatermarkPosition) {
        val updated = _uiState.value.activeTemplate.copy(brandPosition = position)
        _uiState.update { it.copy(activeTemplate = updated) }
        persistTemplateDebounced(updated, immediate = true)
    }

    fun updateLogoPosition(position: WatermarkPosition) {
        val updated = _uiState.value.activeTemplate.copy(logoPosition = position)
        _uiState.update { it.copy(activeTemplate = updated) }
        persistTemplateDebounced(updated, immediate = true)
    }

    fun resetWatermarkPositions() {
        val state = _uiState.value
        val cur = state.activeTemplate
        val defaultPos = DefaultWatermarkTemplates.defaultTemplateForId(
            id = cur.id,
            style = cur.style,
            language = state.appLanguage,
        ).position
        val updated = cur.copy(
            position = defaultPos,
            brandPosition = null,
            logoPosition = null,
        )
        _uiState.update { it.copy(activeTemplate = updated) }
        persistTemplateDebounced(updated, immediate = true)
    }

    fun saveCurrentAsPreset(presetName: String) {
        val strings = currentStrings()
        val cleanName = presetName.trim().ifEmpty { strings.wmSavePresetTitle }
        val newPreset = _uiState.value.activeTemplate.copy(
            id = "preset_${System.currentTimeMillis()}",
            name = cleanName,
        )
        val updatedPresets = _uiState.value.customPresets + newPreset
        _uiState.update {
            it.copy(
                activeTemplate = newPreset,
                customPresets = updatedPresets,
                statusMessage = strings.presetSaved(cleanName),
            )
        }
        viewModelScope.launch {
            preferences.saveCustomPresets(updatedPresets)
            preferences.saveActiveTemplate(newPreset)
        }
    }

    fun deletePreset(id: String) {
        val updatedPresets = _uiState.value.customPresets.filterNot { it.id == id }
        _uiState.update { it.copy(customPresets = updatedPresets) }
        viewModelScope.launch {
            preferences.saveCustomPresets(updatedPresets)
        }
    }

    fun setSaveOriginalPhoto(enabled: Boolean) {
        _uiState.update { it.copy(saveOriginalPhoto = enabled) }
        viewModelScope.launch {
            preferences.setSaveOriginalPhoto(enabled)
        }
    }

    fun setMirrorFrontCamera(enabled: Boolean) {
        _uiState.update { it.copy(mirrorFrontCamera = enabled) }
        viewModelScope.launch {
            preferences.setMirrorFrontCamera(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun showStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun onShutterClicked() {
        val state = _uiState.value
        if (state.isCapturing) return

        if (state.countdownSeconds > 0) {
            countdownJob?.cancel()
            countdownJob = null
            _uiState.update { it.copy(countdownSeconds = 0) }
            return
        }

        val delaySeconds = state.timer.seconds
        if (delaySeconds <= 0) {
            performCapture()
        } else {
            countdownJob = viewModelScope.launch {
                for (sec in delaySeconds downTo 1) {
                    _uiState.update { it.copy(countdownSeconds = sec) }
                    delay(1000L)
                }
                _uiState.update { it.copy(countdownSeconds = 0) }
                performCapture()
            }
        }
    }

    private fun performCapture() {
        val state = _uiState.value
        _uiState.update { it.copy(isCapturing = true) }
        val captureTime = TimeProvider.resolveEpochMillis(
            nowMillis = System.currentTimeMillis(),
            config = state.timeConfig,
        )
        val effectiveLatLng = resolveEffectiveLatLng(state, state.activeTemplate)
        val effectiveAlt = if (state.activeTemplate.useLiveLocation) {
            state.liveLocation?.altitudeMeters
        } else {
            state.activeTemplate.pinnedAltitude ?: state.liveLocation?.altitudeMeters
        }
        viewModelScope.launch {
            cameraController.capturePhoto(
                epochMillis = captureTime,
                cropSquare = state.aspectRatio == CameraAspectRatio.RATIO_1_1,
                isFrontCamera = state.isFrontCamera,
                mirrorFront = state.mirrorFrontCamera,
                photoQuality = state.photoQuality,
                template = state.activeTemplate,
                saveOriginalPhoto = state.saveOriginalPhoto,
                weatherCode = state.liveWeather?.weatherCode,
                gpsLatitude = effectiveLatLng?.first,
                gpsLongitude = effectiveLatLng?.second,
                gpsAltitude = effectiveAlt,
                language = state.appLanguage,
            ).onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastCapturedUri = uri,
                        lastCapturedIsVideo = false,
                    )
                }
            }.onFailure { err ->
                val strings = AppStringsProvider.forLanguage(_uiState.value.appLanguage)
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        errorMessage = err.message ?: strings.captureFailed,
                    )
                }
            }
        }
    }

    fun startOrStopVideoRecording(hasAudioPermission: Boolean) {
        val state = _uiState.value
        if (state.captureMode != CaptureMode.VIDEO) return

        if (state.isRecording) {
            cameraController.stopVideoRecording()
            return
        }

        val withAudio = state.isAudioEnabled && hasAudioPermission
        if (state.isAudioEnabled && !hasAudioPermission) {
            val strings = AppStringsProvider.forLanguage(state.appLanguage)
            _uiState.update {
                it.copy(statusMessage = strings.recordingMutedToast)
            }
        }

        syncVideoWatermarkSnapshot(state)
        val recordStartEpoch = TimeProvider.resolveEpochMillis(
            nowMillis = System.currentTimeMillis(),
            config = state.timeConfig,
        )
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingDurationSeconds = 0L,
            )
        }

        cameraController.startVideoRecording(
            epochMillis = recordStartEpoch,
            withAudio = withAudio,
            onDurationSeconds = { secs ->
                _uiState.update { it.copy(recordingDurationSeconds = secs) }
            },
            onFinalized = { result ->
                val strings = AppStringsProvider.forLanguage(_uiState.value.appLanguage)
                result.onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingDurationSeconds = 0L,
                            lastCapturedUri = uri,
                            lastCapturedIsVideo = true,
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            recordingDurationSeconds = 0L,
                            errorMessage = err.message ?: strings.videoFailed,
                        )
                    }
                }
            },
        )
    }

    fun onBatchPhotosSaved(
        lastUri: Uri?,
    ) {
        _uiState.update {
            it.copy(
                lastCapturedUri = lastUri ?: it.lastCapturedUri,
                lastCapturedIsVideo = if (lastUri != null) false else it.lastCapturedIsVideo,
            )
        }
    }

    private fun persistTemplateDebounced(template: WatermarkTemplate, immediate: Boolean) {
        templateSaveJob?.cancel()
        templateSaveJob = viewModelScope.launch {
            if (!immediate) delay(250L)
            preferences.saveActiveTemplate(template)
        }
        syncVideoWatermarkSnapshot(_uiState.value)
    }

    private fun syncVideoWatermarkSnapshot(state: CameraUiState) {
        val template = state.activeTemplate
        val needAssetReload = template.logoUri != cachedVideoLogoUri ||
            template.customBrandImageUri != cachedVideoBrandUri ||
            template.brandBadgePreset != cachedVideoBrandPreset

        videoSnapshotVersion += 1L
        val currentVersion = videoSnapshotVersion
        cameraController.updateVideoWatermarkSnapshot(
            VideoWatermarkSnapshot(
                template = template,
                timeConfig = state.timeConfig,
                language = state.appLanguage,
                weatherCode = state.liveWeather?.weatherCode,
                logoBitmap = cachedVideoLogoBitmap,
                customBrandBitmap = cachedVideoBrandBitmap,
                version = currentVersion,
            ),
        )

        if (needAssetReload) {
            videoAssetJob?.cancel()
            videoAssetJob = viewModelScope.launch {
                val (logoBmp, brandBmp) = withContext(Dispatchers.IO) {
                    val l = WatermarkCanvasRenderer.decodeLogoBitmap(appContext, template.logoUri)
                    val b = if (template.brandBadgePreset == BrandBadgePreset.CUSTOM_IMAGE) {
                        BrandAssetPackLoader.decodeCustomBrandBitmap(appContext, template.customBrandImageUri)
                    } else null
                    l to b
                }
                cachedVideoLogoUri = template.logoUri
                cachedVideoLogoBitmap = logoBmp
                cachedVideoBrandUri = template.customBrandImageUri
                cachedVideoBrandPreset = template.brandBadgePreset
                cachedVideoBrandBitmap = brandBmp

                val latest = _uiState.value
                videoSnapshotVersion += 1L
                cameraController.updateVideoWatermarkSnapshot(
                    VideoWatermarkSnapshot(
                        template = latest.activeTemplate,
                        timeConfig = latest.timeConfig,
                        language = latest.appLanguage,
                        weatherCode = latest.liveWeather?.weatherCode,
                        logoBitmap = logoBmp,
                        customBrandBitmap = brandBmp,
                        version = videoSnapshotVersion,
                    ),
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun resolveEffectiveLatLng(
        state: CameraUiState,
        template: WatermarkTemplate,
    ): Pair<Double, Double>? {
        if (!template.useLiveLocation && template.pinnedLatitude != null && template.pinnedLongitude != null) {
            return template.pinnedLatitude to template.pinnedLongitude
        }
        return state.liveLocation?.let { it.latitude to it.longitude }
    }

    private fun mergeLiveTelemetry(
        base: WatermarkTemplate,
        liveLoc: LiveLocationSnapshot?,
        azimuthDeg: Float?,
        weather: WeatherSnapshot?,
        language: AppLanguage = AppLanguage.VI,
    ): WatermarkTemplate {
        var next = base
        if (azimuthDeg != null && "compass" !in manualTelemetryOverrides) {
            next = next.copy(compass = TimemarkLocationFormatter.formatCompass(azimuthDeg))
        }
        if (weather != null && "weather" !in manualTelemetryOverrides) {
            val wText = if (next.style == WatermarkStyleId.LOCATION_BAR) {
                weather.compactText
            } else {
                weather.formattedFullText(language)
            }
            next = next.copy(weather = wText)
        }
        if (next.useLiveLocation && liveLoc != null) {
            val formattedCoords = TimemarkLocationFormatter.formatCoordinates(
                liveLoc.latitude,
                liveLoc.longitude,
                next.coordinateFormat,
            )
            val altText = if ("altitude" !in manualTelemetryOverrides) {
                liveLoc.altitudeMeters?.let { TimemarkLocationFormatter.formatAltitude(it) } ?: next.altitude
            } else {
                next.altitude
            }
            next = next.copy(
                address = liveLoc.address.fullAddress.ifBlank { next.address },
                smallPosition = liveLoc.address.smallPosition.ifBlank { next.smallPosition },
                largePosition = liveLoc.address.largePosition.ifBlank { next.largePosition },
                coordinates = formattedCoords,
                altitude = altText,
            )
        } else if (!next.useLiveLocation && next.pinnedLatitude != null && next.pinnedLongitude != null) {
            next = next.copy(
                coordinates = TimemarkLocationFormatter.formatCoordinates(
                    next.pinnedLatitude,
                    next.pinnedLongitude,
                    next.coordinateFormat,
                ),
            )
        }
        return next
    }

    private fun triggerWeatherFetch(lat: Double, lng: Double) {
        val key = "${(lat * 100).toInt()}_${(lng * 100).toInt()}"
        if (key == lastWeatherKey && _uiState.value.liveWeather != null) return
        lastWeatherKey = key
        weatherFetchJob?.cancel()
        weatherFetchJob = viewModelScope.launch {
            val snapshot = WeatherClient.fetchWeather(lat, lng) ?: return@launch
            _uiState.update { prev ->
                val merged = mergeLiveTelemetry(
                    base = prev.activeTemplate,
                    liveLoc = prev.liveLocation,
                    azimuthDeg = prev.liveAzimuthDegrees,
                    weather = snapshot,
                    language = prev.appLanguage,
                )
                prev.copy(liveWeather = snapshot, activeTemplate = merged)
            }
        }
    }
}
