/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

object BrandAssetPackLoader {
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun hasLocalBrandPack(context: Context): Boolean =
        loadAssetBitmap(context, "official_wm_new.webp") != null ||
            loadAssetBitmap(context, "official_watermark2_en.png") != null

    fun loadAssetBitmap(context: Context, fileName: String): Bitmap? {
        cache[fileName]?.takeIf { !it.isRecycled }?.let { return it }
        if (missing.contains(fileName)) return null
        val loaded = runCatching {
            context.assets.open("brand_pack/$fileName").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
        if (loaded == null) {
            missing.add(fileName)
            return null
        }
        cache[fileName] = loaded
        return loaded
    }

    fun decodeCustomBrandBitmap(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > 640 || bounds.outHeight / sample > 640) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }
}
