/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.sensors

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface
import kotlin.math.abs

enum class FrameEdge {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
}

object OrientationMath {

    fun quantizeOrientationWithHysteresis(
        rawOrientationDegrees: Int,
        currentQuantized: Int,
        hysteresisDegrees: Int = 15,
    ): Int {
        if (rawOrientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN || rawOrientationDegrees < 0) {
            return currentQuantized
        }
        val raw = ((rawOrientationDegrees % 360) + 360) % 360
        val cur = ((currentQuantized % 360) + 360) % 360
        val rawDist = abs(raw - cur)
        val circularDist = minOf(rawDist, 360 - rawDist)
        if (circularDist < 45 + hysteresisDegrees) {
            return cur
        }
        return when (raw) {
            in 45 until 135 -> 90
            in 135 until 225 -> 180
            in 225 until 315 -> 270
            else -> 0
        }
    }

    /**
     * Converts clockwise physical tilt degrees (`0`, `90`, `180`, `270`) into the UI rotation
     * needed so on-screen icons and watermarks stay upright relative to gravity:
     * - Physical 90° (top tilted right, top edge right / bottom edge left -> clockwise 90°)
     *   OrientationEventListener reports 90 when the right edge is down (rotated 90° CW -> top points right),
     *   or left edge down? Wait: standard Android OrientationEventListener:
     *   0 = natural upright; 90 = top points left? No: 90 when left side is UP (device rotated 90° CW, top on right? Wait: Android docs:
     *   "orientation is 0 degrees when the device is oriented in its natural position, 90 degrees when its left side is at the top (top rotated CW 90),
     *    180 degrees when upside down, and 270 degrees when its right side is at the top (top rotated CCW 90)".
     *   So when top is at RIGHT (left side at top, orientation=90), an upright graphic in portrait needs -90° (270°) rotationZ.
     *   And `(360 - 90) % 360 = 270` -> `-90°` CCW!
     */
    fun deviceToUiRotationDegrees(deviceQuantizedDegrees: Int): Int =
        ((360 - ((deviceQuantizedDegrees % 360) + 360) % 360) % 360)

    fun deviceToSurfaceRotation(deviceQuantizedDegrees: Int): Int =
        when (((deviceQuantizedDegrees % 360) + 360) % 360) {
            90 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

    fun computeShortestRotationTarget(
        currentAngle: Float,
        targetDegrees: Int,
    ): Float {
        val currentMod = ((currentAngle % 360f) + 360f) % 360f
        val targetMod = (((targetDegrees % 360) + 360) % 360).toFloat()
        var delta = targetMod - currentMod
        if (delta > 180f) delta -= 360f
        if (delta <= -180f) delta += 360f
        return currentAngle + delta
    }

    fun mapScreenDeltaToVirtual(
        dx: Float,
        dy: Float,
        uiRotationDegrees: Int,
    ): Pair<Float, Float> = when (((uiRotationDegrees % 360) + 360) % 360) {
        90 -> dy to -dx
        180 -> -dx to -dy
        270 -> -dy to dx
        else -> dx to dy
    }

    fun topEdgeForUiRotation(uiRotationDegrees: Int): FrameEdge =
        when (((uiRotationDegrees % 360) + 360) % 360) {
            90 -> FrameEdge.RIGHT
            180 -> FrameEdge.BOTTOM
            270 -> FrameEdge.LEFT
            else -> FrameEdge.TOP
        }
}

class DeviceOrientationMonitor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var listener: OrientationEventListener? = null
    private var currentDeviceDegrees = 0
    private var onChanged: ((deviceDegrees: Int, uiRotationDegrees: Int, surfaceRotation: Int) -> Unit)? = null

    fun start(
        onOrientationChanged: (deviceDegrees: Int, uiRotationDegrees: Int, surfaceRotation: Int) -> Unit,
    ) {
        stop()
        onChanged = onOrientationChanged
        val eventListener = object : OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_UI) {
            override fun onOrientationChanged(orientation: Int) {
                val next = OrientationMath.quantizeOrientationWithHysteresis(
                    rawOrientationDegrees = orientation,
                    currentQuantized = currentDeviceDegrees,
                )
                if (next != currentDeviceDegrees) {
                    currentDeviceDegrees = next
                    onChanged?.invoke(
                        next,
                        OrientationMath.deviceToUiRotationDegrees(next),
                        OrientationMath.deviceToSurfaceRotation(next),
                    )
                }
            }
        }
        if (eventListener.canDetectOrientation()) {
            eventListener.enable()
            listener = eventListener
        } else {
            eventListener.disable()
        }
    }

    fun stop() {
        runCatching { listener?.disable() }
        listener = null
        onChanged = null
    }
}
