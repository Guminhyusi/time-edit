/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.storage.MediaStoreSaver
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.engine.renderer.PhotoStampProcessor
import com.timeedit.camera.engine.renderer.WatermarkCanvasRenderer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class VideoWatermarkSnapshot(
    val template: WatermarkTemplate = DefaultWatermarkTemplates.timeLocation(),
    val timeConfig: TimeConfig = TimeConfig(),
    val language: AppLanguage = AppLanguage.VI,
    val weatherCode: Int? = null,
    val logoBitmap: Bitmap? = null,
    val customBrandBitmap: Bitmap? = null,
    val version: Long = 0L,
)

class CameraController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var activeOverlayEffect: OverlayEffect? = null

    @Volatile
    private var videoWatermarkSnapshot = VideoWatermarkSnapshot()

    private var cachedFrameBitmap: Bitmap? = null
    private var cachedFrameSecond: Long = -1L
    private var cachedSnapshotVersion: Long = -1L
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var loggedFrameGeometry = false
    private var currentSurfaceRotation: Int = Surface.ROTATION_0

    fun updateVideoWatermarkSnapshot(snapshot: VideoWatermarkSnapshot) {
        videoWatermarkSnapshot = snapshot
    }

    fun updateTargetRotation(surfaceRotation: Int) {
        currentSurfaceRotation = surfaceRotation
        imageCapture?.targetRotation = surfaceRotation
        if (activeRecording == null) {
            videoCapture?.targetRotation = surfaceRotation
        }
    }

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        captureMode: CaptureMode,
        isFrontCamera: Boolean,
        aspectRatio: CameraAspectRatio,
        flashMode: CameraFlashMode,
        preferredVideoQuality: VideoQualityPreset = VideoQualityPreset.HD_30,
        onVideoCapabilitiesChanged: (supported: List<VideoQualityPreset>, effective: VideoQualityPreset) -> Unit = { _, _ -> },
        onZoomStateChanged: (current: Float, min: Float, max: Float, hasFlash: Boolean) -> Unit,
    ): Result<Unit> = runCatching {
        stopVideoRecording()
        activeOverlayEffect?.close()
        activeOverlayEffect = null

        val provider = cameraProvider ?: obtainCameraProvider().also { cameraProvider = it }

        val effectiveAspectRatio = if (captureMode == CaptureMode.VIDEO) {
            CameraAspectRatio.RATIO_16_9
        } else {
            aspectRatio
        }
        val aspectStrategy = when (effectiveAspectRatio) {
            CameraAspectRatio.RATIO_16_9 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            CameraAspectRatio.RATIO_4_3, CameraAspectRatio.RATIO_1_1 ->
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        }
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectStrategy)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply {
                setSurfaceProvider { request ->
                    _surfaceRequest.value = request
                }
            }

        val selector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val targetCameraInfo = runCatching {
            selector.filter(provider.availableCameraInfos).firstOrNull()
        }.getOrNull()
        val supportedVideoPresets = if (targetCameraInfo != null) {
            querySupportedVideoPresets(targetCameraInfo)
        } else {
            listOf(VideoQualityPreset.HD_30, VideoQualityPreset.FHD_30)
        }
        val effectiveVideoPreset = VideoQualityPreset.resolveEffectiveVideoQuality(
            preferred = preferredVideoQuality,
            supported = supportedVideoPresets,
        )
        onVideoCapabilitiesChanged(supportedVideoPresets, effectiveVideoPreset)

        provider.unbindAll()

        val camera = when (captureMode) {
            CaptureMode.PHOTO -> {
                videoCapture = null
                val captureFlashMode = when (flashMode) {
                    CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    CameraFlashMode.OFF, CameraFlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(currentSurfaceRotation)
                    .setFlashMode(captureFlashMode)
                    .build()
                imageCapture = capture
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }

            CaptureMode.VIDEO -> {
                imageCapture = null
                val targetQuality = when (effectiveVideoPreset) {
                    VideoQualityPreset.FHD_30, VideoQualityPreset.FHD_60 -> Quality.FHD
                    VideoQualityPreset.HD_30, VideoQualityPreset.HD_60 -> Quality.HD
                }
                val qualitySelector = QualitySelector.from(
                    targetQuality,
                    FallbackStrategy.lowerQualityOrHigherThan(targetQuality),
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val vCapture = VideoCapture.Builder(recorder)
                    .setTargetRotation(currentSurfaceRotation)
                    .setTargetFrameRate(android.util.Range(effectiveVideoPreset.fps, effectiveVideoPreset.fps))
                    .build()
                videoCapture = vCapture

                loggedFrameGeometry = false
                val overlayEffect = createVideoOverlayEffect()
                activeOverlayEffect = overlayEffect

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(vCapture)
                    .addEffect(overlayEffect)
                    .build()
                provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
            }
        }

        activeCamera = camera

        val hasFlash = camera.cameraInfo.hasFlashUnit()
        if (hasFlash) {
            camera.cameraControl.enableTorch(flashMode == CameraFlashMode.TORCH)
        }

        camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
            if (zoomState != null) {
                onZoomStateChanged(
                    zoomState.zoomRatio,
                    zoomState.minZoomRatio,
                    zoomState.maxZoomRatio,
                    hasFlash,
                )
            }
        }
    }

    private fun createVideoOverlayEffect(): OverlayEffect {
        val effect = OverlayEffect(
            CameraEffect.VIDEO_CAPTURE,
            0,
            mainHandler,
        ) { err ->
            Log.e(TAG, "OverlayEffect error", err)
        }
        effect.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val bufW = canvas.width
            val bufH = canvas.height
            val rotationDegrees = ((frame.rotationDegrees % 360) + 360) % 360

            if (!loggedFrameGeometry) {
                loggedFrameGeometry = true
                Log.d(TAG, "Video OverlayEffect frame buf=${bufW}x${bufH}, size=${frame.size}, rot=$rotationDegrees")
            }

            val swapDims = rotationDegrees == 90 || rotationDegrees == 270
            val uprightW = if (swapDims) bufH else bufW
            val uprightH = if (swapDims) bufW else bufH
            if (uprightW <= 0 || uprightH <= 0) return@setOnDrawListener true

            val snap = videoWatermarkSnapshot
            val resolvedEpoch = TimeProvider.resolveEpochMillis(
                nowMillis = System.currentTimeMillis(),
                config = snap.timeConfig,
            )
            val secondBucket = resolvedEpoch / 1000L

            val overlayBitmap = getOrRenderVideoWatermarkBitmap(
                width = uprightW,
                height = uprightH,
                snapshot = snap,
                epochMillis = resolvedEpoch,
                secondBucket = secondBucket,
            )

            canvas.save()
            when (rotationDegrees) {
                90 -> {
                    canvas.translate(0f, bufH.toFloat())
                    canvas.rotate(-90f)
                }
                180 -> {
                    canvas.translate(bufW.toFloat(), bufH.toFloat())
                    canvas.rotate(180f)
                }
                270 -> {
                    canvas.translate(bufW.toFloat(), 0f)
                    canvas.rotate(90f)
                }
            }
            canvas.drawBitmap(overlayBitmap, 0f, 0f, null)
            canvas.restore()
            true
        }
        return effect
    }

    private fun getOrRenderVideoWatermarkBitmap(
        width: Int,
        height: Int,
        snapshot: VideoWatermarkSnapshot,
        epochMillis: Long,
        secondBucket: Long,
    ): Bitmap {
        val existing = cachedFrameBitmap
        if (existing != null &&
            !existing.isRecycled &&
            cachedWidth == width &&
            cachedHeight == height &&
            cachedFrameSecond == secondBucket &&
            cachedSnapshotVersion == snapshot.version
        ) {
            return existing
        }

        val target = if (existing != null && !existing.isRecycled && cachedWidth == width && cachedHeight == height) {
            existing.eraseColor(Color.TRANSPARENT)
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val offscreenCanvas = Canvas(target)
        WatermarkCanvasRenderer.drawWatermarkOnCanvas(
            context = appContext,
            canvas = offscreenCanvas,
            photoWidth = width,
            photoHeight = height,
            template = snapshot.template,
            epochMillis = epochMillis,
            logoBitmap = snapshot.logoBitmap,
            customBrandBitmap = snapshot.customBrandBitmap,
            weatherCode = snapshot.weatherCode,
            isPreview = false,
            language = snapshot.language,
        )

        cachedFrameBitmap = target
        cachedWidth = width
        cachedHeight = height
        cachedFrameSecond = secondBucket
        cachedSnapshotVersion = snapshot.version
        return target
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        epochMillis: Long,
        withAudio: Boolean,
        onDurationSeconds: (Long) -> Unit,
        onFinalized: (Result<Uri>) -> Unit,
    ) {
        val vCapture = videoCapture ?: run {
            onFinalized(Result.failure(IllegalStateException("Máy quay video chưa sẵn sàng")))
            return
        }
        if (activeRecording != null) return

        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaStoreOptions = MediaStoreOutputOptions.Builder(
                appContext.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            )
                .setContentValues(MediaStoreSaver.createVideoContentValues(epochMillis))
                .build()
            vCapture.output.prepareRecording(appContext, mediaStoreOptions)
        } else {
            @Suppress("DEPRECATION")
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val dir = File(dcim, "TimeEdit").apply { if (!exists()) mkdirs() }
            val file = File(dir, MediaStoreSaver.buildVideoFileName(epochMillis))
            vCapture.output.prepareRecording(appContext, FileOutputOptions.Builder(file).build())
        }

        val configured = if (withAudio) pending.withAudioEnabled() else pending
        var lastReportedSecond = -1L

        activeRecording = configured.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Status -> {
                    val secs = event.recordingStats.recordedDurationNanos / 1_000_000_000L
                    if (secs != lastReportedSecond) {
                        lastReportedSecond = secs
                        onDurationSeconds(secs)
                    }
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (!event.hasError()) {
                        onFinalized(Result.success(event.outputResults.outputUri))
                    } else {
                        onFinalized(
                            Result.failure(
                                RuntimeException("Quay video thất bại (mã lỗi ${event.error})"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
    }

    fun release() {
        stopVideoRecording()
        activeOverlayEffect?.close()
        activeOverlayEffect = null
        cachedFrameBitmap?.recycle()
        cachedFrameBitmap = null
    }

    fun updateFlash(flashMode: CameraFlashMode) {
        imageCapture?.flashMode = when (flashMode) {
            CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CameraFlashMode.OFF, CameraFlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
        }
        val camera = activeCamera ?: return
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(flashMode == CameraFlashMode.TORCH)
        }
    }

    fun setZoomRatio(ratio: Float) {
        val camera = activeCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value
        val minZ = zoomState?.minZoomRatio ?: 1f
        val maxZ = zoomState?.maxZoomRatio ?: 4f
        camera.cameraControl.setZoomRatio(ratio.coerceIn(minZ, maxZ))
    }

    fun focusAt(
        surfaceX: Float,
        surfaceY: Float,
        surfaceWidth: Float,
        surfaceHeight: Float,
    ) {
        val camera = activeCamera ?: return
        if (surfaceWidth <= 0f || surfaceHeight <= 0f) return
        val factory = SurfaceOrientedMeteringPointFactory(surfaceWidth, surfaceHeight)
        val point = factory.createPoint(surfaceX, surfaceY)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    suspend fun capturePhoto(
        epochMillis: Long,
        cropSquare: Boolean,
        isFrontCamera: Boolean,
        mirrorFront: Boolean,
        photoQuality: PhotoQuality = PhotoQuality.HD,
        template: WatermarkTemplate,
        saveOriginalPhoto: Boolean,
        weatherCode: Int? = null,
        gpsLatitude: Double? = null,
        gpsLongitude: Double? = null,
        gpsAltitude: Double? = null,
        language: AppLanguage = AppLanguage.VI,
    ): Result<Uri> = runCatching {
        val capture = imageCapture ?: error("Camera chưa sẵn sàng")
        val rawJpeg = takePictureBytes(capture)

        if (saveOriginalPhoto) {
            val origTemp = File.createTempFile("te_orig_", ".png", appContext.cacheDir)
            try {
                withContext(Dispatchers.IO) {
                    PhotoStampProcessor.convertOriginalToPngToFile(
                        rawBytes = rawJpeg,
                        epochMillis = epochMillis,
                        cropSquare = cropSquare,
                        mirrorHorizontal = isFrontCamera && mirrorFront,
                        gpsLatitude = gpsLatitude,
                        gpsLongitude = gpsLongitude,
                        gpsAltitude = gpsAltitude,
                        targetFile = origTemp,
                        maxDimension = photoQuality.maxDimension,
                    )
                }
                MediaStoreSaver.savePngFile(
                    context = appContext,
                    sourceFile = origTemp,
                    epochMillis = epochMillis,
                    isOriginal = true,
                )
            } finally {
                origTemp.delete()
            }
        }

        val stampedTemp = File.createTempFile("te_cap_", ".png", appContext.cacheDir)
        try {
            withContext(Dispatchers.IO) {
                PhotoStampProcessor.processAndStampPngToFile(
                    context = appContext,
                    rawBytes = rawJpeg,
                    epochMillis = epochMillis,
                    cropSquare = cropSquare,
                    mirrorHorizontal = isFrontCamera && mirrorFront,
                    template = template,
                    weatherCode = weatherCode,
                    gpsLatitude = gpsLatitude,
                    gpsLongitude = gpsLongitude,
                    gpsAltitude = gpsAltitude,
                    targetFile = stampedTemp,
                    maxDimension = photoQuality.maxDimension,
                    language = language,
                )
            }
            MediaStoreSaver.savePngFile(
                context = appContext,
                sourceFile = stampedTemp,
                epochMillis = epochMillis,
                isOriginal = false,
            ).getOrThrow()
        } finally {
            stampedTemp.delete()
        }
    }

    private fun querySupportedVideoPresets(cameraInfo: CameraInfo): List<VideoQualityPreset> {
        val qualities = runCatching {
            Recorder.getVideoCapabilities(cameraInfo).getSupportedQualities(DynamicRange.SDR)
        }.getOrNull().orEmpty()
        val hasHd = Quality.HD in qualities || qualities.isEmpty()
        val hasFhd = Quality.FHD in qualities
        val fpsRanges = runCatching {
            cameraInfo.supportedFrameRateRanges.map { it.lower to it.upper }
        }.getOrDefault(emptyList())
        val presets = computeSupportedVideoPresets(
            hasHdQuality = hasHd,
            hasFhdQuality = hasFhd,
            supportedFpsRanges = fpsRanges,
            supportsAvcAt = ::isAvcEncoderSupported,
        )
        Log.d(TAG, "VideoCaps: qualities=$qualities fps=$fpsRanges presets=$presets")
        return presets
    }

    private fun isAvcEncoderSupported(width: Int, height: Int, fps: Int): Boolean {
        return runCatching {
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            codecs.any { info ->
                if (!info.isEncoder) return@any false
                if (!info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }) return@any false
                val caps = info.getCapabilitiesForType("video/avc").videoCapabilities ?: return@any false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val perfPoints = caps.supportedPerformancePoints
                    if (!perfPoints.isNullOrEmpty()) {
                        val targetPoint = MediaCodecInfo.VideoCapabilities.PerformancePoint(width, height, fps)
                        if (perfPoints.any { it.covers(targetPoint) }) {
                            return@any true
                        }
                    }
                }
                val alignedW = alignUpToMultiple(width, caps.widthAlignment)
                val alignedH = alignUpToMultiple(height, caps.heightAlignment)
                caps.areSizeAndRateSupported(alignedW, alignedH, fps.toDouble())
            }
        }.getOrDefault(fps <= 30)
    }

    private suspend fun obtainCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    try {
                        if (cont.isActive) cont.resume(future.get())
                    } catch (e: Throwable) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                },
                mainExecutor,
            )
        }

    private suspend fun takePictureBytes(capture: ImageCapture): ByteArray =
        suspendCancellableCoroutine { cont ->
            capture.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            if (cont.isActive) cont.resume(bytes)
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resumeWithException(t)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }

    companion object {
        private const val TAG = "CameraController"

        internal fun alignUpToMultiple(value: Int, alignment: Int): Int {
            if (alignment <= 1) return value
            val rem = value % alignment
            return if (rem == 0) value else value + (alignment - rem)
        }

        internal fun computeSupportedVideoPresets(
            hasHdQuality: Boolean,
            hasFhdQuality: Boolean,
            supportedFpsRanges: List<Pair<Int, Int>>,
            supportsAvcAt: (width: Int, height: Int, fps: Int) -> Boolean,
        ): List<VideoQualityPreset> {
            val supports60Fps = supportedFpsRanges.any { (minFps, maxFps) ->
                minFps <= 60 && maxFps >= 60
            }
            val result = mutableListOf<VideoQualityPreset>()
            if (hasHdQuality) {
                result += VideoQualityPreset.HD_30
                if (supports60Fps && supportsAvcAt(1280, 720, 60)) {
                    result += VideoQualityPreset.HD_60
                }
            }
            if (hasFhdQuality && supportsAvcAt(1920, 1080, 30)) {
                result += VideoQualityPreset.FHD_30
                if (supports60Fps && supportsAvcAt(1920, 1080, 60)) {
                    result += VideoQualityPreset.FHD_60
                }
            }
            return result.ifEmpty { listOf(VideoQualityPreset.HD_30) }
        }
    }
}
