/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object MediaStoreSaver {
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    fun buildPhotoFileName(
        epochMillis: Long,
        suffix: String = "",
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val stamp = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(fileNameFormatter)
        return "IMG_${stamp}${suffix}.png"
    }

    fun buildVideoFileName(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val stamp = Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(fileNameFormatter)
        return "VID_${stamp}.mp4"
    }

    fun createVideoContentValues(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ContentValues {
        val fileName = buildVideoFileName(epochMillis, zoneId)
        return ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/TimeEdit")
            }
            put(MediaStore.Video.Media.DATE_TAKEN, epochMillis)
            put(MediaStore.Video.Media.DATE_MODIFIED, epochMillis / 1000L)
        }
    }

    suspend fun savePngFile(
        context: Context,
        sourceFile: File,
        epochMillis: Long,
        isOriginal: Boolean = false,
        suffixOverride: String = "",
    ): Result<Uri> = runCatching {
        val appContext = context.applicationContext
        val suffix = when {
            suffixOverride.isNotEmpty() -> suffixOverride
            isOriginal -> "_orig"
            else -> ""
        }
        val fileName = buildPhotoFileName(epochMillis, suffix = suffix)
        val relativeDir = if (isOriginal) {
            "${Environment.DIRECTORY_DCIM}/TimeEdit/Originals"
        } else {
            "${Environment.DIRECTORY_DCIM}/TimeEdit"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Images.Media.DATE_TAKEN, epochMillis)
                put(MediaStore.Images.Media.DATE_MODIFIED, epochMillis / 1000L)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: error("Không thể tạo mục lưu trữ cho $fileName")
            try {
                FileInputStream(sourceFile).use { input ->
                    resolver.openOutputStream(uri)?.use { out ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    } ?: error("Không thể mở luồng ghi ảnh")
                }
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val targetDir = File(dcim, if (isOriginal) "TimeEdit/Originals" else "TimeEdit")
            if (!targetDir.exists()) targetDir.mkdirs()
            val outFile = uniqueFile(targetDir, fileName)
            FileInputStream(sourceFile).use { input ->
                outFile.outputStream().use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            outFile.setLastModified(epochMillis)
            scanFileUri(appContext, outFile.absolutePath) ?: Uri.fromFile(outFile)
        }
    }

    suspend fun savePng(
        context: Context,
        pngBytes: ByteArray,
        epochMillis: Long,
        isOriginal: Boolean = false,
        suffixOverride: String = "",
    ): Result<Uri> = runCatching {
        val tempFile = File.createTempFile("te_mem_", ".png", context.applicationContext.cacheDir)
        try {
            tempFile.writeBytes(pngBytes)
            savePngFile(
                context = context,
                sourceFile = tempFile,
                epochMillis = epochMillis,
                isOriginal = isOriginal,
                suffixOverride = suffixOverride,
            ).getOrThrow()
        } finally {
            tempFile.delete()
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "png")
        var candidate = File(dir, fileName)
        var idx = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$idx.$ext")
            idx++
        }
        return candidate
    }

    private suspend fun scanFileUri(context: Context, path: String): Uri? =
        suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("image/png")) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
}
