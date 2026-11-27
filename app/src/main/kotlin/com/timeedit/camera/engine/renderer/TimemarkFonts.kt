/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.engine.renderer

import android.content.Context
import android.graphics.Typeface

data class TimemarkTypefaces(
    val bigShouldersMedium: Typeface,
    val robotoCondensedBold: Typeface,
    val robotoCondensedMedium: Typeface,
    val robotoCondensedRegular: Typeface,
    val ptMonoBold: Typeface,
    val ptMonoRegular: Typeface,
    val montserratBlack: Typeface,
)

object TimemarkFonts {
    @Volatile
    private var cached: TimemarkTypefaces? = null

    fun get(context: Context): TimemarkTypefaces {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val assets = context.assets
            val loaded = TimemarkTypefaces(
                bigShouldersMedium = loadOrFallback(assets, "fonts/BigShouldersText-Medium.ttf", Typeface.BOLD),
                robotoCondensedBold = loadOrFallback(assets, "fonts/RobotoCondensed-Bold.ttf", Typeface.BOLD),
                robotoCondensedMedium = loadOrFallback(assets, "fonts/RobotoCondensed-Medium.ttf", Typeface.BOLD),
                robotoCondensedRegular = loadOrFallback(assets, "fonts/RobotoCondensed-Regular.ttf", Typeface.NORMAL),
                ptMonoBold = loadOrFallback(assets, "fonts/PTMono-Bold.ttf", Typeface.BOLD, Typeface.MONOSPACE),
                ptMonoRegular = loadOrFallback(assets, "fonts/PTMono-Regular.ttf", Typeface.NORMAL, Typeface.MONOSPACE),
                montserratBlack = loadOrFallback(assets, "fonts/Montserrat-Black.ttf", Typeface.BOLD),
            )
            cached = loaded
            return loaded
        }
    }

    private fun loadOrFallback(
        assets: android.content.res.AssetManager,
        path: String,
        style: Int,
        family: Typeface = Typeface.SANS_SERIF,
    ): Typeface = runCatching {
        Typeface.createFromAsset(assets, path)
    }.getOrElse {
        Typeface.create(family, style)
    }
}
