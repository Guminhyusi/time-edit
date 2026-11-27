/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.sensors

import android.view.OrientationEventListener
import android.view.Surface
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DeviceOrientationTest {

    @Test
    fun quantizeOrientationWithHysteresis_handlesCardinalsUnknownAndDeadzone() {
        assertThat(
            OrientationMath.quantizeOrientationWithHysteresis(
                rawOrientationDegrees = OrientationEventListener.ORIENTATION_UNKNOWN,
                currentQuantized = 90,
            ),
        ).isEqualTo(90)

        assertThat(OrientationMath.quantizeOrientationWithHysteresis(355, 90)).isEqualTo(0)
        assertThat(OrientationMath.quantizeOrientationWithHysteresis(88, 0)).isEqualTo(90)
        assertThat(OrientationMath.quantizeOrientationWithHysteresis(182, 90)).isEqualTo(180)
        assertThat(OrientationMath.quantizeOrientationWithHysteresis(268, 0)).isEqualTo(270)

        // Diagonal hysteresis deadzone (< 45 + 15 = 60 deg from current 0 keeps 0)
        assertThat(OrientationMath.quantizeOrientationWithHysteresis(52, 0, hysteresisDegrees = 15))
            .isEqualTo(0)
        assertThat(OrientationMath.quantizeOrientationWithHysteresis(65, 0, hysteresisDegrees = 15))
            .isEqualTo(90)
    }

    @Test
    fun deviceToUiAndSurfaceRotation_mapAllFourCardinalsCorrectly() {
        assertThat(OrientationMath.deviceToUiRotationDegrees(0)).isEqualTo(0)
        assertThat(OrientationMath.deviceToUiRotationDegrees(90)).isEqualTo(270)
        assertThat(OrientationMath.deviceToUiRotationDegrees(180)).isEqualTo(180)
        assertThat(OrientationMath.deviceToUiRotationDegrees(270)).isEqualTo(90)

        assertThat(OrientationMath.deviceToSurfaceRotation(0)).isEqualTo(Surface.ROTATION_0)
        assertThat(OrientationMath.deviceToSurfaceRotation(90)).isEqualTo(Surface.ROTATION_270)
        assertThat(OrientationMath.deviceToSurfaceRotation(180)).isEqualTo(Surface.ROTATION_180)
        assertThat(OrientationMath.deviceToSurfaceRotation(270)).isEqualTo(Surface.ROTATION_90)
    }

    @Test
    fun computeShortestRotationTarget_alwaysTakesShortestAngularArc() {
        assertThat(OrientationMath.computeShortestRotationTarget(0f, 270)).isWithin(0.01f).of(-90f)
        assertThat(OrientationMath.computeShortestRotationTarget(-90f, 0)).isWithin(0.01f).of(0f)
        assertThat(OrientationMath.computeShortestRotationTarget(90f, 180)).isWithin(0.01f).of(180f)
        assertThat(OrientationMath.computeShortestRotationTarget(270f, 0)).isWithin(0.01f).of(360f)
        assertThat(OrientationMath.computeShortestRotationTarget(-90f, 180)).isWithin(0.01f).of(-180f)
    }

    @Test
    fun mapScreenDeltaToVirtual_projectsScreenGesturesCorrectly() {
        assertThat(OrientationMath.mapScreenDeltaToVirtual(10f, 20f, 0)).isEqualTo(10f to 20f)
        assertThat(OrientationMath.mapScreenDeltaToVirtual(10f, 20f, 90)).isEqualTo(20f to -10f)
        assertThat(OrientationMath.mapScreenDeltaToVirtual(10f, 20f, 180)).isEqualTo(-10f to -20f)
        assertThat(OrientationMath.mapScreenDeltaToVirtual(10f, 20f, 270)).isEqualTo(-20f to 10f)
    }

    @Test
    fun topEdgeForUiRotation_placesResetPopupAlongPhysicalTopEdge() {
        assertThat(OrientationMath.topEdgeForUiRotation(0)).isEqualTo(FrameEdge.TOP)
        assertThat(OrientationMath.topEdgeForUiRotation(90)).isEqualTo(FrameEdge.RIGHT)
        assertThat(OrientationMath.topEdgeForUiRotation(180)).isEqualTo(FrameEdge.BOTTOM)
        assertThat(OrientationMath.topEdgeForUiRotation(270)).isEqualTo(FrameEdge.LEFT)
    }
}
