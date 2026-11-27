/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.camera

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraStateTest {
    @Test
    fun flashMode_cyclesThroughAllStatesAndWraps() {
        assertThat(CameraFlashMode.OFF.next()).isEqualTo(CameraFlashMode.AUTO)
        assertThat(CameraFlashMode.AUTO.next()).isEqualTo(CameraFlashMode.ON)
        assertThat(CameraFlashMode.ON.next()).isEqualTo(CameraFlashMode.TORCH)
        assertThat(CameraFlashMode.TORCH.next()).isEqualTo(CameraFlashMode.OFF)
    }

    @Test
    fun aspectRatio_cyclesThrough43_169_11() {
        assertThat(CameraAspectRatio.RATIO_4_3.next()).isEqualTo(CameraAspectRatio.RATIO_16_9)
        assertThat(CameraAspectRatio.RATIO_16_9.next()).isEqualTo(CameraAspectRatio.RATIO_1_1)
        assertThat(CameraAspectRatio.RATIO_1_1.next()).isEqualTo(CameraAspectRatio.RATIO_4_3)
    }

    @Test
    fun captureTimer_cyclesOff_3s_5s_10s() {
        assertThat(CaptureTimer.OFF.next()).isEqualTo(CaptureTimer.SEC_3)
        assertThat(CaptureTimer.SEC_3.next()).isEqualTo(CaptureTimer.SEC_5)
        assertThat(CaptureTimer.SEC_5.next()).isEqualTo(CaptureTimer.SEC_10)
        assertThat(CaptureTimer.SEC_10.next()).isEqualTo(CaptureTimer.OFF)
    }

    @Test
    fun computeZoomPresets_handlesUltrawideStandardAndFixed() {
        assertThat(computeZoomPresets(minZoomRatio = 0.6f, maxZoomRatio = 10f))
            .containsExactly(0.6f, 1.0f, 2.0f).inOrder()
        assertThat(computeZoomPresets(minZoomRatio = 1.0f, maxZoomRatio = 8f))
            .containsExactly(1.0f, 2.0f).inOrder()
        assertThat(computeZoomPresets(minZoomRatio = 1.0f, maxZoomRatio = 1.0f))
            .containsExactly(1.0f)
    }

    @Test
    fun formatZoomLabel_formatsIntegersWithoutTrailingZero() {
        assertThat(formatZoomLabel(0.6f)).isEqualTo("0.6x")
        assertThat(formatZoomLabel(1.0f)).isEqualTo("1x")
        assertThat(formatZoomLabel(2.0f)).isEqualTo("2x")
        assertThat(formatZoomLabel(1.54f)).isEqualTo("1.5x")
    }

    @Test
    fun formatRecordingDuration_formatsMinutesSecondsAndHours() {
        assertThat(formatRecordingDuration(-3L)).isEqualTo("00:00")
        assertThat(formatRecordingDuration(0L)).isEqualTo("00:00")
        assertThat(formatRecordingDuration(9L)).isEqualTo("00:09")
        assertThat(formatRecordingDuration(75L)).isEqualTo("01:15")
        assertThat(formatRecordingDuration(3665L)).isEqualTo("01:01:05")
    }

    @Test
    fun cameraUiState_defaultsToPhotoModeWithAudioEnabled() {
        val state = CameraUiState()
        assertThat(state.captureMode).isEqualTo(CaptureMode.PHOTO)
        assertThat(state.isRecording).isFalse()
        assertThat(state.isAudioEnabled).isTrue()
        assertThat(state.lastCapturedIsVideo).isFalse()
    }
}
