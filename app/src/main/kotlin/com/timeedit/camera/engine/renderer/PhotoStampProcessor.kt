/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class ExtractedPhotoExif(
    val epochMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val width: Int,
    val height: Int,
)

object PhotoStampProcessor {
    private val exifPrimaryFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val exifIsoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun parseExifDateTime(
        raw: String?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { LocalDateTime.parse(clean, exifPrimaryFormatter) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(clean, exifIsoFormatter) }.getOrNull()
            ?: return null
        return parsed.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun computeInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        maxDimension: Int = 4096,
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (rawWidth / sample > maxDimension || rawHeight / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }

    fun computeScaledDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxDimension: Int,
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0 || maxDimension <= 0) {
            return srcWidth.coerceAtLeast(1) to srcHeight.coerceAtLeast(1)
        }
        val longest = maxOf(srcWidth, srcHeight)
        if (longest <= maxDimension) {
            return srcWidth to srcHeight
        }
        val scale = maxDimension.toDouble() / longest.toDouble()
        val outW = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val outH = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        return outW to outH
    }

    private fun downscaleIfNeeded(
        bitmap: Bitmap,
        maxDimension: Int,
        requireMutable: Boolean = false,
    ): Bitmap {
        val (targetW, targetH) = computeScaledDimensions(bitmap.width, bitmap.height, maxDimension)
        if (targetW == bitmap.width && targetH == bitmap.height) {
            return if (requireMutable && !bitmap.isMutable) {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                bitmap
            }
        }
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        return if (requireMutable && !scaled.isMutable) {
            val mutable = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (mutable !== scaled) scaled.recycle()
            mutable
        } else {
            scaled
        }
    }

    fun resolveStampEpochMillis(
        customTimeConfig: TimeConfig?,
        sharedTimeConfig: TimeConfig,
        nowMillis: Long,
    ): Long = TimeProvider.resolveEpochMillis(
        nowMillis = nowMillis,
        config = customTimeConfig ?: sharedTimeConfig,
    )

    fun extractPhotoMetadata(
        context: Context,
        uri: Uri,
    ): ExtractedPhotoExif {
        val resolver = context.contentResolver
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var epochMillis: Long? = null
        var lat: Double? = null
        var lng: Double? = null
        var alt: Double? = null

        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                val rawTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                epochMillis = parseExifDateTime(rawTime)
                val latLong = exif.latLong
                if (latLong != null && latLong.size >= 2 && (latLong[0] != 0.0 || latLong[1] != 0.0)) {
                    lat = latLong[0]
                    lng = latLong[1]
                    val rawAlt = exif.getAltitude(Double.NaN)
                    if (!rawAlt.isNaN() && rawAlt.isFinite()) {
                        alt = rawAlt
                    }
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }

        val swapped = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val w = if (swapped) bounds.outHeight else bounds.outWidth
        val h = if (swapped) bounds.outWidth else bounds.outHeight

        return ExtractedPhotoExif(
            epochMillis = epochMillis,
            latitude = lat,
            longitude = lng,
            altitude = alt,
            width = w.coerceAtLeast(0),
            height = h.coerceAtLeast(0),
        )
    }

    fun decodeOrientedPreviewBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1440,
    ): Bitmap? = runCatching {
        val resolver = context.contentResolver
        var orientation = ExifInterface.ORIENTATION_NORMAL
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@runCatching null

        val oriented = applyExifTransform(
            bitmap = decoded,
            orientation = orientation,
            mirrorHorizontal = false,
        )
        if (oriented !== decoded) {
            decoded.recycle()
        }
        oriented
    }.getOrNull()

    fun convertOriginalToPngToFile(
        rawBytes: ByteArray,
        epochMillis: Long,
        cropSquare: Boolean,
        mirrorHorizontal: Boolean,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAltitude: Double?,
        targetFile: File,
        maxDimension: Int = 1280,
    ) {
        val originalExif = runCatching { ExifInterface(ByteArrayInputStream(rawBytes)) }.getOrNull()
        val srcOrientation = originalExif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension * 2)

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: error("Không thể giải mã ảnh gốc")

        val oriented = applyExifTransform(
            bitmap = decoded,
            orientation = srcOrientation,
            mirrorHorizontal = mirrorHorizontal,
        )
        val cropped = if (cropSquare) cropCenterSquare(oriented) else oriented
        val finalBitmap = downscaleIfNeeded(cropped, maxDimension = maxDimension, requireMutable = false)

        try {
            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            if (finalBitmap !== cropped) finalBitmap.recycle()
            if (cropped !== oriented) cropped.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }

        applyOutputExif(
            targetFile = targetFile,
            originalExif = originalExif,
            epochMillis = epochMillis,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAltitude = gpsAltitude,
        )
    }

    fun stampGalleryUriToFile(
        context: Context,
        uri: Uri,
        epochMillis: Long,
        template: WatermarkTemplate,
        weatherCode: Int?,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAltitude: Double?,
        targetFile: File,
        maxDimension: Int = 1280,
        language: AppLanguage = AppLanguage.VI,
    ) {
        val resolver = context.contentResolver
        val originalExif = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
        }.getOrNull()
        val srcOrientation = originalExif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension * 2)

        val decodeOpts = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = sampleSize
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: error("Không thể đọc ảnh từ thư viện")

        renderStampAndWritePngFile(
            context = context,
            decoded = decoded,
            originalExif = originalExif,
            srcOrientation = srcOrientation,
            epochMillis = epochMillis,
            cropSquare = false,
            mirrorHorizontal = false,
            template = template,
            weatherCode = weatherCode,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAltitude = gpsAltitude,
            targetFile = targetFile,
            maxDimension = maxDimension,
            language = language,
        )
    }

    fun processAndStampPngToFile(
        context: Context,
        rawBytes: ByteArray,
        epochMillis: Long,
        cropSquare: Boolean,
        mirrorHorizontal: Boolean,
        template: WatermarkTemplate,
        weatherCode: Int?,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAltitude: Double?,
        targetFile: File,
        maxDimension: Int = 1280,
        language: AppLanguage = AppLanguage.VI,
    ) {
        val originalExif = runCatching { ExifInterface(ByteArrayInputStream(rawBytes)) }.getOrNull()
        val srcOrientation = originalExif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension * 2)

        val decodeOpts = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = sampleSize
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: error("Không thể giải mã ảnh")

        renderStampAndWritePngFile(
            context = context,
            decoded = decoded,
            originalExif = originalExif,
            srcOrientation = srcOrientation,
            epochMillis = epochMillis,
            cropSquare = cropSquare,
            mirrorHorizontal = mirrorHorizontal,
            template = template,
            weatherCode = weatherCode,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAltitude = gpsAltitude,
            targetFile = targetFile,
            maxDimension = maxDimension,
            language = language,
        )
    }

    private fun renderStampAndWritePngFile(
        context: Context,
        decoded: Bitmap,
        originalExif: ExifInterface?,
        srcOrientation: Int,
        epochMillis: Long,
        cropSquare: Boolean,
        mirrorHorizontal: Boolean,
        template: WatermarkTemplate,
        weatherCode: Int?,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAltitude: Double?,
        targetFile: File,
        maxDimension: Int,
        language: AppLanguage,
    ) {
        val oriented = applyExifTransform(
            bitmap = decoded,
            orientation = srcOrientation,
            mirrorHorizontal = mirrorHorizontal,
        )
        val cropped = if (cropSquare) cropCenterSquare(oriented) else oriented
        val mutableTarget = downscaleIfNeeded(cropped, maxDimension = maxDimension, requireMutable = true)

        try {
            val logoBmp = WatermarkCanvasRenderer.decodeLogoBitmap(context, template.logoUri)
            val customBrandBmp = if (template.brandBadgePreset == BrandBadgePreset.CUSTOM_IMAGE) {
                BrandAssetPackLoader.decodeCustomBrandBitmap(context, template.customBrandImageUri)
            } else null
            try {
                val canvas = Canvas(mutableTarget)
                WatermarkCanvasRenderer.drawWatermarkOnCanvas(
                    context = context,
                    canvas = canvas,
                    photoWidth = mutableTarget.width,
                    photoHeight = mutableTarget.height,
                    template = template,
                    epochMillis = epochMillis,
                    logoBitmap = logoBmp,
                    customBrandBitmap = customBrandBmp,
                    weatherCode = weatherCode,
                    isPreview = false,
                    language = language,
                )
            } finally {
                logoBmp?.recycle()
                customBrandBmp?.recycle()
            }

            FileOutputStream(targetFile).use { out ->
                mutableTarget.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            if (mutableTarget !== cropped) mutableTarget.recycle()
            if (cropped !== oriented) cropped.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }

        applyOutputExif(
            targetFile = targetFile,
            originalExif = originalExif,
            epochMillis = epochMillis,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAltitude = gpsAltitude,
        )
    }

    private fun applyOutputExif(
        targetFile: File,
        originalExif: ExifInterface?,
        epochMillis: Long,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAltitude: Double?,
    ) {
        runCatching {
            val targetExif = ExifInterface(targetFile.absolutePath)
            if (originalExif != null) {
                copyCameraExifTags(originalExif, targetExif)
            }
            targetExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString(),
            )
            val exifTime = TimeProvider.formatExifDateTime(epochMillis)
            targetExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
            targetExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
            targetExif.setAttribute(ExifInterface.TAG_DATETIME, exifTime)
            applyExifLocation(targetExif, gpsLatitude, gpsLongitude, gpsAltitude)
            targetExif.saveAttributes()
        }
    }

    private fun applyExifLocation(
        exif: ExifInterface,
        lat: Double?,
        lng: Double?,
        alt: Double?,
    ) {
        if (lat != null && lng != null) {
            exif.setLatLong(lat, lng)
            if (alt != null && alt.isFinite()) {
                exif.setAltitude(alt)
            }
        }
    }

    private fun applyExifTransform(
        bitmap: Bitmap,
        orientation: Int,
        mirrorHorizontal: Boolean,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (mirrorHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    private fun cropCenterSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return if (x == 0 && y == 0 && bitmap.width == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, x, y, size, size)
        }
    }

    private fun copyCameraExifTags(src: ExifInterface, dst: ExifInterface) {
        val tags = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
        )
        for (tag in tags) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
    }
}
