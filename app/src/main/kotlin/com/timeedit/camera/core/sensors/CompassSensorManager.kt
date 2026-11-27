/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CompassSensorManager(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val accelReading = FloatArray(3)
    private val magnetReading = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false

    private var smoothSin = 0.0
    private var smoothCos = 1.0
    private var initialized = false
    private var lastEmittedDeg = Float.MIN_VALUE
    private var lastEmitTimeMs = 0L

    private var isFrontCamera = false
    private var onAzimuth: ((Float) -> Unit)? = null

    fun start(
        isFrontCamera: Boolean,
        onAzimuthChanged: (Float) -> Unit,
    ) {
        stop()
        this.isFrontCamera = isFrontCamera
        this.onAzimuth = onAzimuthChanged

        val sm = sensorManager ?: return
        val rotVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotVector != null) {
            sm.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun updateFrontCamera(front: Boolean) {
        isFrontCamera = front
    }

    fun stop() {
        runCatching { sensorManager?.unregisterListener(this) }
        onAzimuth = null
        initialized = false
        hasAccel = false
        hasMagnet = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                emitAzimuthFromMatrix()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelReading, 0, 3)
                hasAccel = true
                if (hasMagnet && SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)) {
                    emitAzimuthFromMatrix()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetReading, 0, 3)
                hasMagnet = true
                if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)) {
                    emitAzimuthFromMatrix()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun emitAzimuthFromMatrix() {
        val displayRot = currentDisplayRotation()
        when (displayRot) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix)
            else -> System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
        }
        SensorManager.getOrientation(remappedMatrix, orientation)
        var rawDeg = Math.toDegrees(orientation[0].toDouble())
        if (isFrontCamera) rawDeg += 180.0

        val rad = Math.toRadians(rawDeg)
        if (!initialized) {
            smoothSin = sin(rad)
            smoothCos = cos(rad)
            initialized = true
        } else {
            val alpha = 0.22
            smoothSin += alpha * (sin(rad) - smoothSin)
            smoothCos += alpha * (cos(rad) - smoothCos)
        }

        val smoothedDeg = (((Math.toDegrees(atan2(smoothSin, smoothCos)) % 360.0) + 360.0) % 360.0).toFloat()
        val now = System.currentTimeMillis()
        // Matches SensorService.java: throttle every 200ms and only when >= 1 degree change
        if (now - lastEmitTimeMs >= 200L && (lastEmittedDeg == Float.MIN_VALUE || abs(smoothedDeg - lastEmittedDeg) >= 1.0f)) {
            lastEmitTimeMs = now
            lastEmittedDeg = smoothedDeg
            onAzimuth?.invoke(smoothedDeg)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appContext.display.rotation
            } else {
                (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        }.getOrDefault(Surface.ROTATION_0)
    }
}
