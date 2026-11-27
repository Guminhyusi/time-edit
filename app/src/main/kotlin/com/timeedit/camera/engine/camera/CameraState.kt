/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.camera

import android.graphics.Bitmap
import android.net.Uri
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.location.LiveLocationSnapshot
import com.timeedit.camera.core.location.LocationFixState
import com.timeedit.camera.core.location.WeatherSnapshot
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.ui.theme.AppThemeMode

enum class CameraFlashMode(val label: String) {
    OFF("Tắt"),
    AUTO("Tự động"),
    ON("Bật"),
    TORCH("Đèn pin");

    fun next(): CameraFlashMode = entries[(ordinal + 1) % entries.size]
}

enum class CameraAspectRatio(val label: String, val portraitRatio: Float) {
    RATIO_4_3("4:3", 3f / 4f),
    RATIO_16_9("16:9", 9f / 16f),
    RATIO_1_1("1:1", 1f);

    fun next(): CameraAspectRatio = entries[(ordinal + 1) % entries.size]
}

enum class CaptureMode(val label: String) {
    PHOTO("ẢNH"),
    VIDEO("VIDEO"),
}

enum class CaptureTimer(val seconds: Int, val label: String) {
    OFF(0, "Tắt"),
    SEC_3(3, "3s"),
    SEC_5(5, "5s"),
    SEC_10(10, "10s");

    fun next(): CaptureTimer = entries[(ordinal + 1) % entries.size]
}

enum class PhotoQuality(
    val code: String,
    val label: String,
    val maxDimension: Int,
) {
    SD("sd", "SD", 960),
    HD("hd", "HD", 1280),
    FHD("fhd", "FHD", 1920);

    companion object {
        fun fromCode(code: String?): PhotoQuality =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HD
    }
}

enum class VideoQualityPreset(
    val code: String,
    val label: String,
    val shortLabel: String,
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    HD_30("720p30", "720p 30fps", "720p 30", 1280, 720, 30),
    HD_60("720p60", "720p 60fps", "720p 60", 1280, 720, 60),
    FHD_30("1080p30", "1080p 30fps", "1080p 30", 1920, 1080, 30),
    FHD_60("1080p60", "1080p 60fps", "1080p 60", 1920, 1080, 60);

    companion object {
        fun fromCode(code: String?): VideoQualityPreset =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HD_30

        fun resolveEffectiveVideoQuality(
            preferred: VideoQualityPreset,
            supported: List<VideoQualityPreset>,
        ): VideoQualityPreset {
            if (supported.isEmpty()) return HD_30
            if (preferred in supported) return preferred
            supported.firstOrNull { it.height == preferred.height && it.fps == 30 }?.let { return it }
            supported
                .filter { it.height <= preferred.height }
                .sortedWith(compareByDescending<VideoQualityPreset> { it.height }.thenByDescending { it.fps })
                .firstOrNull()
                ?.let { return it }
            return supported.first()
        }
    }
}

data class CameraUiState(
    val appLanguage: AppLanguage = AppLanguage.VI,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val isFrontCamera: Boolean = false,
    val uiRotationDegrees: Int = 0,
    val flashMode: CameraFlashMode = CameraFlashMode.OFF,
    val hasFlashUnit: Boolean = false,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    val timer: CaptureTimer = CaptureTimer.OFF,
    val countdownSeconds: Int = 0,
    val showGrid: Boolean = false,
    val photoQuality: PhotoQuality = PhotoQuality.HD,
    val preferredVideoQuality: VideoQualityPreset = VideoQualityPreset.HD_30,
    val effectiveVideoQuality: VideoQualityPreset = VideoQualityPreset.HD_30,
    val supportedVideoQualities: List<VideoQualityPreset> = listOf(VideoQualityPreset.HD_30),
    val zoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 1.0f,
    val maxZoomRatio: Float = 4.0f,
    val zoomPresets: List<Float> = listOf(1.0f),
    val isCapturing: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Long = 0L,
    val isAudioEnabled: Boolean = true,
    val lastCapturedUri: Uri? = null,
    val lastCapturedIsVideo: Boolean = false,
    val timeConfig: TimeConfig = TimeConfig(),
    val activeTemplate: WatermarkTemplate = DefaultWatermarkTemplates.timeLocation(),
    val customPresets: List<WatermarkTemplate> = emptyList(),
    val saveOriginalPhoto: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val locationState: LocationFixState = LocationFixState.IDLE,
    val liveLocation: LiveLocationSnapshot? = null,
    val liveAzimuthDegrees: Float? = null,
    val liveWeather: WeatherSnapshot? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
)

fun computeZoomPresets(minZoomRatio: Float, maxZoomRatio: Float): List<Float> {
    val result = mutableListOf<Float>()
    if (minZoomRatio <= 0.65f) {
        result += 0.6f
    }
    if (minZoomRatio <= 1.0f && maxZoomRatio >= 1.0f) {
        result += 1.0f
    } else {
        result += minZoomRatio
    }
    if (maxZoomRatio >= 2.0f) {
        result += 2.0f
    }
    return result.distinct()
}

fun formatZoomLabel(ratio: Float): String {
    val rounded = kotlin.math.round(ratio * 10f) / 10f
    return if (rounded % 1.0f == 0f) {
        "${rounded.toInt()}x"
    } else {
        "${rounded}x"
    }
}

fun formatRecordingDuration(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600L
    val minutes = (clamped % 3600L) / 60L
    val seconds = clamped % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(java.util.Locale.US, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(java.util.Locale.US, minutes, seconds)
    }
}
