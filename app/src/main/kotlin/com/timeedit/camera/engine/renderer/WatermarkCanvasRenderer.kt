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
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.AppStrings
import com.timeedit.camera.core.i18n.AppStringsProvider
import com.timeedit.camera.core.time.TimeProvider
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.LogoPlacement
import com.timeedit.camera.domain.watermark.TimeLayoutVariant
import com.timeedit.camera.domain.watermark.WatermarkCorner
import com.timeedit.camera.domain.watermark.WatermarkStyleId
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.domain.watermark.generatePhotoCode
import com.timeedit.camera.domain.watermark.resolveWatermarkOrigin
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class WatermarkRenderMetrics(
    val mainBounds: RectF,
    val timeBounds: RectF,
    val logoBounds: RectF? = null,
    val brandBounds: RectF? = null,
)

object WatermarkCanvasRenderer {
    fun decodeLogoBitmap(context: Context, logoUriString: String?): Bitmap? {
        if (logoUriString.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(logoUriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }.getOrNull()
    }

    fun drawWatermarkOnCanvas(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        template: WatermarkTemplate,
        epochMillis: Long,
        logoBitmap: Bitmap? = null,
        customBrandBitmap: Bitmap? = null,
        weatherCode: Int? = null,
        isPreview: Boolean = false,
        language: AppLanguage = AppLanguage.VI,
    ): WatermarkRenderMetrics {
        if (photoWidth <= 0 || photoHeight <= 0) {
            return WatermarkRenderMetrics(RectF(), RectF())
        }

        val strings = AppStringsProvider.forLanguage(language)
        val fonts = TimemarkFonts.get(context)
        val shortEdge = min(photoWidth, photoHeight).toFloat()
        val baseDp = shortEdge / 360f
        val dp = baseDp * template.theme.scale.coerceIn(0.6f, 1.6f)

        val accentColor = parseColorSafe(template.theme.accentColorHex, Color.parseColor("#FFC233"))
        val textColor = parseColorSafe(template.theme.textColorHex, Color.WHITE)
        val outerMarginPx = 8f * baseDp

        // Pre-measure optional top Styled Note and bottom PhotoCode / Verified Shield
        val extras = measureCompositeExtras(context, dp, fonts, template, strings)

        val metrics = when (template.style) {
            WatermarkStyleId.TIME_LOCATION -> drawId34TimeLocation(
                canvas, photoWidth, photoHeight, baseDp, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.CLOCK_IN -> drawId21ClockIn(
                context, canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.WORK_REPORT -> drawId20WorkReport(
                canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.LOCATION_BAR -> drawId60LocationBar(
                context, canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, weatherCode, isPreview, extras, strings,
            )

            WatermarkStyleId.CUSTOM_TEXT -> drawId10CustomText(
                canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.COMPLETED -> drawId80Completed(
                context, canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.CLEANED -> drawId90Cleaned(
                context, canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.CONSTRUCTION_RECORD -> drawId120Construction(
                canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.SHEET -> drawId100Sheet(
                canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )

            WatermarkStyleId.SECURITY_GUARD -> drawId43SecurityGuard(
                context, canvas, photoWidth, photoHeight, dp, outerMarginPx, fonts, template, epochMillis, accentColor, textColor, isPreview, extras, strings,
            )
        }

        val logoBounds = if (logoBitmap != null && !logoBitmap.isRecycled) {
            drawOuterLogo(
                canvas = canvas,
                logoBitmap = logoBitmap,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                baseDp = baseDp,
                template = template,
                mainBounds = metrics.mainBounds,
            )
        } else null

        val brandBounds = drawCornerBrandBadge(
            context = context,
            canvas = canvas,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            baseDp = baseDp,
            fonts = fonts,
            template = template,
            customBrandBitmap = customBrandBitmap,
            mainBounds = metrics.mainBounds,
            logoBounds = logoBounds,
            strings = strings,
        )

        drawVerticalPhotoCodeStrip(
            context = context,
            canvas = canvas,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            baseDp = baseDp,
            fonts = fonts,
            template = template,
            epochMillis = epochMillis,
        )

        return metrics.copy(logoBounds = logoBounds, brandBounds = brandBounds)
    }

    private class CompositeExtras(
        val styledNoteText: String?,
        val styledNoteLayout: StaticLayout?,
        val styledNoteBoxW: Float,
        val styledNoteBoxH: Float,
        val showVerified: Boolean,
        val shieldBmp: Bitmap?,
        val verifiedPaint: TextPaint,
        val verifiedH: Float,
        val verifiedLabel: String,
    ) {
        val topOffset: Float = if (styledNoteBoxH > 0f) styledNoteBoxH + 6f else 0f
        val bottomExtra: Float = verifiedH
    }

    private fun measureCompositeExtras(
        context: Context,
        dp: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        strings: AppStrings,
    ): CompositeExtras {
        val noteText = if (template.showStyledNote && template.styledNote.isNotBlank()) {
            template.styledNote.trim()
        } else null
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.5f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.WHITE
            applyTimemarkShadow(dp)
        }
        var noteLayout: StaticLayout? = null
        var noteBoxW = 0f
        var noteBoxH = 0f
        if (noteText != null) {
            val l = StaticLayout.Builder.obtain(noteText, 0, noteText.length, notePaint, (180f * dp).roundToInt())
                .build()
            val lw = (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f
            noteLayout = l
            noteBoxW = lw + 20f * dp
            noteBoxH = l.height + 10f * dp
        }

        val verifiedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.argb(195, 255, 255, 255)
            applyTimemarkShadow(dp)
        }
        val shieldBmp = if (template.showVerifiedBadge) {
            BrandAssetPackLoader.loadAssetBitmap(context, "watermark_shield.webp")
        } else null
        val verifiedH = if (template.showVerifiedBadge) textHeight(verifiedPaint) + 5f * dp else 0f

        val verifiedLabel = when (strings.language) {
            AppLanguage.VI -> "Thời gian xác thực"
            AppLanguage.EN -> "Verified Time"
            AppLanguage.ZH_CN -> "真实时间认证"
        }
        return CompositeExtras(
            styledNoteText = noteText,
            styledNoteLayout = noteLayout,
            styledNoteBoxW = noteBoxW,
            styledNoteBoxH = noteBoxH,
            showVerified = template.showVerifiedBadge,
            shieldBmp = shieldBmp,
            verifiedPaint = verifiedPaint,
            verifiedH = verifiedH,
            verifiedLabel = verifiedLabel,
        )
    }

    private fun drawExtrasTop(
        canvas: Canvas,
        startX: Float,
        topY: Float,
        dp: Float,
        extras: CompositeExtras,
    ) {
        val layout = extras.styledNoteLayout ?: return
        val rect = RectF(startX, topY, startX + extras.styledNoteBoxW, topY + extras.styledNoteBoxH)
        canvas.drawRoundRect(
            rect,
            3f * dp,
            3f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) },
        )
        canvas.drawRoundRect(
            rect,
            3f * dp,
            3f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * dp
                color = Color.argb(190, 255, 255, 255)
            },
        )
        canvas.save()
        canvas.translate(startX + 10f * dp, topY + 5f * dp)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawExtrasBottom(
        canvas: Canvas,
        startX: Float,
        bottomOfBodyY: Float,
        dp: Float,
        extras: CompositeExtras,
    ) {
        var cy = bottomOfBodyY
        if (extras.showVerified) {
            cy += 4f * dp
            val p = extras.verifiedPaint
            val iconSize = 12f * dp
            val shield = extras.shieldBmp
            if (shield != null && !shield.isRecycled) {
                canvas.drawBitmap(
                    shield,
                    null,
                    RectF(startX, cy + 1f * dp, startX + iconSize, cy + 1f * dp + iconSize),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            } else {
                drawVectorShield(canvas, startX, cy + 1f * dp, iconSize)
            }
            canvas.drawText(
                extras.verifiedLabel,
                startX + iconSize + 4f * dp,
                cy - p.ascent(),
                p,
            )
        }
    }

    internal fun formatCompactBarDateTimeLine(
        epochMillis: Long,
        template: WatermarkTemplate,
        language: AppLanguage,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val locale = language.toLocale()
        val dateStr = TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, zoneId = zoneId, locale = locale)
        val weekStr = TimeProvider.formatWeekday(epochMillis, full = true, zoneId = zoneId, locale = locale)
        val clockStr = TimeProvider.formatTemplateClock(epochMillis, template.use24Hour, template.showSeconds, zoneId = zoneId)
        return when (language) {
            AppLanguage.ZH_CN -> "$dateStr $weekStr $clockStr"
            AppLanguage.VI, AppLanguage.EN -> "$weekStr, $dateStr $clockStr"
        }
    }

    /**
     * ID 34 (watermark_cover_id1034 / new_watermark_cover_id34)
     */
    private fun drawId34TimeLocation(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        baseDp: Float,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        if (template.timeLayoutVariant == TimeLayoutVariant.COMPACT_BAR) {
            return drawId34CompactBar(
                canvas = canvas,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                baseDp = baseDp,
                dp = dp,
                fonts = fonts,
                template = template,
                epochMillis = epochMillis,
                accentColor = accentColor,
                textColor = textColor,
                isPreview = isPreview,
                extras = extras,
                strings = strings,
            )
        }

        val bgAlpha = (template.theme.backgroundAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
        val hasCard = bgAlpha > 3
        val cardPad = if (hasCard) 8f * dp else 0f

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 43.0f * dp
            typeface = fonts.bigShouldersMedium
            color = textColor
            letterSpacing = 0.01f
        }
        val amPmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f * dp
            typeface = fonts.robotoCondensedMedium
            color = textColor
            applyTimemarkShadow(dp)
        }
        val secPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedBold
            color = textColor
            applyTimemarkShadow(dp)
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.8f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.0f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.0f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val outerMarginX = 9.0f * baseDp
        val maxTextWidth = min(240f * dp, photoWidth - outerMarginX * 2f - cardPad * 2f).coerceAtLeast(120f * dp)

        val locale = strings.language.toLocale()
        val timeStr = if (template.showTime) TimeProvider.formatTimeHourMinute(epochMillis, template.use24Hour) else ""
        val secStr = if (template.showTime && template.showSeconds) TimeProvider.formatSecondsOnly(epochMillis) else null
        val amPmStr = if (template.showTime && !template.use24Hour) TimeProvider.formatAmPm(epochMillis) else null
        val dateStr = if (template.showTime) TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale) else ""
        val weekStr = if (template.showTime) TimeProvider.formatWeekday(epochMillis, full = true, locale = locale) else ""

        val timeRect = Rect()
        if (timeStr.isNotEmpty()) timePaint.getTextBounds(timeStr, 0, timeStr.length, timeRect)
        val timeVisualW = if (timeStr.isNotEmpty()) timePaint.measureText(timeStr) else 0f
        val timeVisualH = if (timeStr.isNotEmpty()) max(timeRect.height().toFloat(), 35.4f * dp) else 0f

        val secBoxSize = if (secStr != null) 18f * dp else 0f
        val secColonW = if (secStr != null) datePaint.measureText(":") + 3f * dp else 0f
        val secColumnW = if (secStr != null) secColonW + secBoxSize + 3f * dp else 0f
        val amPmW = if (amPmStr != null) amPmPaint.measureText(amPmStr) + 4f * dp else 0f
        val rightOfDigitsW = max(secColumnW, amPmW)

        val barW = 1.25f * dp
        val barH = 35.8f * dp
        val barMarginLeft = 8.0f * dp
        val barMarginRight = 5.8f * dp
        val dateColW = if (template.showTime) max(datePaint.measureText(dateStr), datePaint.measureText(weekStr)) else 0f
        val headerRowW = if (template.showTime) {
            timeVisualW + rightOfDigitsW + barMarginLeft + barW + barMarginRight + dateColW
        } else 0f
        val headerRowH = if (template.showTime) max(timeVisualH, barH) else 0f

        val detailItems = collectDetailItems(template, includeDateAsDetail = false, dateStr = dateStr, isPreview = isPreview, strings = strings)
        val detailLayouts = buildDetailStaticLayouts(detailItems, maxTextWidth.roundToInt(), dp, detailPaint, monoPaint, lineSpacingExtraDp = 2.5f)

        var maxLineW = max(headerRowW, extras.styledNoteBoxW)
        var detailsH = 0f
        detailLayouts.forEachIndexed { idx, (_, layout) ->
            val lw = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            maxLineW = max(maxLineW, lw + 2f * dp)
            detailsH += layout.height
            if (idx < detailLayouts.lastIndex) detailsH += 4f * dp
        }

        val headerToDetailsGap = if (headerRowH > 0f && detailLayouts.isNotEmpty()) 11.5f * dp else 0f
        val bodyW = max(maxLineW, 120f * dp) + cardPad * 2f
        val bodyH = headerRowH + headerToDetailsGap + detailsH + cardPad * 2f
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + bodyH + extras.bottomExtra

        val outerMarginY = if (detailLayouts.isEmpty()) 14.4f * baseDp else 8.2f * baseDp
        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = bodyW,
            boxHeight = totalH,
            outerMargin = outerMarginX,
            position = template.position,
            outerMarginVertical = outerMarginY,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val bodyTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val bodyRect = RectF(boxLeft, bodyTop, boxLeft + bodyW, bodyTop + bodyH)

        if (hasCard) {
            canvas.drawRoundRect(
                bodyRect,
                6f * dp,
                6f * dp,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 10, 17, 26) },
            )
        }

        val startX = boxLeft + cardPad
        var cursorY = bodyTop + cardPad
        var timeRectOut = RectF(startX, cursorY, startX + bodyW, cursorY + headerRowH)

        if (template.showTime) {
            val rowCenterY = cursorY + headerRowH / 2f
            val digitBaselineY = rowCenterY - (timeRect.top + timeRect.bottom) / 2f
            val cleanTimePaint = TextPaint(timePaint).apply {
                style = Paint.Style.FILL
                color = textColor
                applyTimemarkShadow(dp)
            }
            canvas.drawText(timeStr, startX, digitBaselineY, cleanTimePaint)

            var nextColX = startX + timeVisualW
            if (amPmStr != null) {
                canvas.drawText(amPmStr, nextColX + 3f * dp, cursorY + 11f * dp, amPmPaint)
            }
            if (secStr != null) {
                val pillTop = cursorY + headerRowH - secBoxSize - 2f * dp
                val colonX = nextColX + 2f * dp
                val colonBaseline = pillTop + secBoxSize / 2f - (secPaint.ascent() + secPaint.descent()) / 2f
                canvas.drawText(":", colonX, colonBaseline, secPaint)

                val pillLeft = colonX + secPaint.measureText(":") + 2f * dp
                val pillRect = RectF(pillLeft, pillTop, pillLeft + secBoxSize, pillTop + secBoxSize)
                canvas.drawRoundRect(
                    pillRect,
                    2f * dp,
                    2f * dp,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(64, 255, 255, 255) },
                )
                val secW = secPaint.measureText(secStr)
                canvas.drawText(secStr, pillLeft + (secBoxSize - secW) / 2f, colonBaseline, secPaint)
            }
            nextColX += rightOfDigitsW

            val barLeft = nextColX + barMarginLeft
            val barTop = rowCenterY - barH / 2f
            canvas.drawRect(
                RectF(barLeft, barTop, barLeft + barW, barTop + barH),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
            )

            val dateColX = barLeft + barW + barMarginRight
            val dateBounds = Rect()
            datePaint.getTextBounds(dateStr, 0, dateStr.length, dateBounds)
            val dateBaselineY = barTop - dateBounds.top.toFloat()
            val weekBaselineY = barTop + barH - 0.5f * dp
            canvas.drawText(dateStr, dateColX, dateBaselineY, datePaint)
            canvas.drawText(weekStr, dateColX, weekBaselineY, datePaint)

            timeRectOut = RectF(startX, cursorY, dateColX + dateColW, cursorY + headerRowH)
            cursorY += headerRowH + headerToDetailsGap
        }

        val detailStartX = startX
        detailLayouts.forEachIndexed { idx, (_, layout) ->
            canvas.save()
            canvas.translate(detailStartX, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height
            if (idx < detailLayouts.lastIndex) cursorY += 4f * dp
        }

        drawExtrasBottom(canvas, boxLeft, bodyRect.bottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + bodyW, boxTop + totalH),
            timeBounds = timeRectOut,
        )
    }

    private fun drawId34CompactBar(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        baseDp: Float,
        dp: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val bgAlpha = (template.theme.backgroundAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
        val hasCard = bgAlpha > 3
        val cardPad = if (hasCard) 8f * dp else 0f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.0f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.0f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val outerMarginX = 9.0f * baseDp
        val outerMarginY = 7.5f * baseDp
        val barW = 1.6f * dp
        val barGapRight = 7.6f * dp
        val maxTextWidth = min(240f * dp, photoWidth - outerMarginX * 2f - cardPad * 2f - barW - barGapRight)
            .coerceAtLeast(120f * dp)

        val dateStr = if (template.showTime) {
            TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = strings.language.toLocale())
        } else ""
        val headerLine = if (template.showTime) {
            formatCompactBarDateTimeLine(epochMillis, template, strings.language)
        } else ""

        val headerLayout = if (headerLine.isNotEmpty()) {
            StaticLayout.Builder.obtain(headerLine, 0, headerLine.length, headerPaint, maxTextWidth.roundToInt().coerceAtLeast(80))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2.0f * dp, 1.0f)
                .build()
        } else null

        val detailItems = collectDetailItems(template, includeDateAsDetail = false, dateStr = dateStr, isPreview = isPreview, strings = strings)
        val detailLayouts = buildDetailStaticLayouts(detailItems, maxTextWidth.roundToInt(), dp, detailPaint, monoPaint, lineSpacingExtraDp = 2.5f)

        var contentW = if (headerLayout != null) {
            (0 until headerLayout.lineCount).maxOfOrNull { headerLayout.getLineWidth(it) } ?: 0f
        } else 0f
        var detailsH = 0f
        detailLayouts.forEachIndexed { idx, (_, layout) ->
            val lw = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            contentW = max(contentW, lw + 2f * dp)
            detailsH += layout.height
            if (idx < detailLayouts.lastIndex) detailsH += 4f * dp
        }

        val headerH = headerLayout?.height?.toFloat() ?: 0f
        val headerToDetailsGap = if (headerLayout != null && detailLayouts.isNotEmpty()) 12.5f * dp else 0f
        val barAndContentW = barW + barGapRight + max(contentW, 90f * dp)
        val bodyW = max(barAndContentW, extras.styledNoteBoxW) + cardPad * 2f
        val innerContentH = headerH + headerToDetailsGap + detailsH
        val bodyH = max(innerContentH, 13.5f * dp) + cardPad * 2f
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + bodyH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = bodyW,
            boxHeight = totalH,
            outerMargin = outerMarginX,
            position = template.position,
            outerMarginVertical = outerMarginY,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val bodyTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val bodyRect = RectF(boxLeft, bodyTop, boxLeft + bodyW, bodyTop + bodyH)

        if (hasCard) {
            canvas.drawRoundRect(
                bodyRect,
                6f * dp,
                6f * dp,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 10, 17, 26) },
            )
        }

        val barLeft = boxLeft + cardPad
        val textStartX = barLeft + barW + barGapRight
        var cursorY = bodyTop + cardPad

        val firstTopTrim = 2.0f * dp
        val lastBottomTrim = 1.5f * dp
        val barTop = bodyTop + cardPad + firstTopTrim
        val barBottom = max(barTop + 13.3f * dp, bodyTop + bodyH - cardPad - lastBottomTrim)
        canvas.drawRect(
            RectF(barLeft, barTop, barLeft + barW, barBottom),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
        )

        val timeRectOut = RectF(textStartX, cursorY, textStartX + contentW, cursorY + max(headerH, 16f * dp))
        if (headerLayout != null) {
            canvas.save()
            canvas.translate(textStartX, cursorY)
            headerLayout.draw(canvas)
            canvas.restore()
            cursorY += headerH + headerToDetailsGap
        }

        detailLayouts.forEachIndexed { idx, (_, layout) ->
            canvas.save()
            canvas.translate(textStartX, cursorY)
            layout.draw(canvas)
            canvas.restore()
            cursorY += layout.height
            if (idx < detailLayouts.lastIndex) cursorY += 4f * dp
        }

        drawExtrasBottom(canvas, boxLeft, bodyRect.bottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + bodyW, boxTop + totalH),
            timeBounds = timeRectOut,
        )
    }

    /**
     * ID 21 (watermark_cover_id21): Clock In
     */
    private fun drawId21ClockIn(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Điểm danh"
            AppLanguage.EN -> "Clock In"
            AppLanguage.ZH_CN -> "打卡"
        }
        val bannerTitle = template.title.trim().ifEmpty { defaultTitle }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.parseColor("#222222")
        }
        val pillTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f * dp
            typeface = fonts.bigShouldersMedium
            color = Color.parseColor("#006CFF")
            letterSpacing = 0.02f
        }
        val secAmPmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.parseColor("#006CFF")
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val pillH = 30f * dp
        val badgeH = 24f * dp
        val badgeTextW = badgeTextPaint.measureText(bannerTitle)
        val badgeW = min(badgeTextW + 14f * dp, 128f * dp)

        val hmStr = if (template.showTime) TimeProvider.formatTimeHourMinute(epochMillis, template.use24Hour) else ""
        val secStr = if (template.showTime && template.showSeconds) TimeProvider.formatSecondsOnly(epochMillis) else null
        val amPmStr = if (template.showTime && !template.use24Hour) TimeProvider.formatAmPm(epochMillis) else null
        val hmW = if (hmStr.isNotEmpty()) pillTimePaint.measureText(hmStr) else 0f
        val extraW = max(
            if (amPmStr != null) secAmPmPaint.measureText(amPmStr) + 3f * dp else 0f,
            if (secStr != null) secAmPmPaint.measureText(":$secStr") + 3f * dp else 0f,
        )
        val pillW = 3f * dp + badgeW + 6f * dp + (if (template.showTime) hmW + extraW + 8f * dp else 4f * dp)

        val maxDetailsW = min(245f * dp, photoWidth - outerMargin * 2f - 10f * dp).coerceAtLeast(140f * dp)
        val locale = strings.language.toLocale()
        val dateStr = if (template.showTime) {
            "${TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)} ${TimeProvider.formatWeekday(epochMillis, full = true, locale = locale)}"
        } else null
        val detailItems = collectDetailItems(template, includeDateAsDetail = true, dateStr = dateStr, isPreview = isPreview, strings = strings)
        val detailLayouts = buildDetailStaticLayouts(detailItems, maxDetailsW.roundToInt(), dp, detailPaint, monoPaint)

        var detailsW = 0f
        var detailsH = 0f
        detailLayouts.forEachIndexed { idx, (_, layout) ->
            val lw = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
            detailsW = max(detailsW, lw)
            detailsH += layout.height
            if (idx < detailLayouts.lastIndex) detailsH += 4f * dp
        }

        val barW = 2f * dp
        val barGap = 6f * dp
        val pillToDetailGap = if (detailLayouts.isNotEmpty()) 8f * dp else 0f
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val bodyW = max(max(pillW, barW + barGap + detailsW), extras.styledNoteBoxW)
        val bodyH = pillH + pillToDetailGap + detailsH
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + bodyH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = bodyW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val bodyTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f

        val pillRect = RectF(boxLeft, bodyTop, boxLeft + pillW, bodyTop + pillH)
        canvas.drawRoundRect(
            pillRect,
            4f * dp,
            4f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(3f * dp, 0f, 1.5f * dp, Color.argb(90, 0, 0, 0))
            },
        )

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(pillRect, 4f * dp, 4f * dp, Path.Direction.CW) })
        val bgWaveBmp = BrandAssetPackLoader.loadAssetBitmap(context, "bg_check_in_global.webp")
        if (bgWaveBmp != null && !bgWaveBmp.isRecycled) {
            canvas.drawBitmap(
                bgWaveBmp,
                null,
                pillRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 150 },
            )
        } else {
            val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(26, 0, 108, 255)
                style = Paint.Style.STROKE
                strokeWidth = 0.7f * dp
            }
            for (i in 0..4) {
                canvas.drawCircle(pillRect.right - 22f * dp, pillRect.bottom + 8f * dp, (18f + i * 9f) * dp, wavePaint)
            }
        }
        canvas.restore()

        val badgeRect = RectF(
            boxLeft + 3f * dp,
            bodyTop + (pillH - badgeH) / 2f,
            boxLeft + 3f * dp + badgeW,
            bodyTop + (pillH + badgeH) / 2f,
        )
        canvas.drawRoundRect(
            badgeRect,
            2f * dp,
            2f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
        )
        val badgeBaseline = badgeRect.centerY() - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f
        canvas.drawText(bannerTitle, badgeRect.centerX() - badgeTextW / 2f, badgeBaseline, badgeTextPaint)

        if (template.showTime) {
            val hmX = badgeRect.right + 6f * dp
            val hmBounds = Rect()
            pillTimePaint.getTextBounds(hmStr, 0, hmStr.length, hmBounds)
            val hmBaseline = pillRect.centerY() - (hmBounds.top + hmBounds.bottom) / 2f
            pillTimePaint.shader = LinearGradient(
                0f,
                hmBaseline + hmBounds.top,
                0f,
                hmBaseline + hmBounds.bottom,
                Color.parseColor("#006CFF"),
                Color.parseColor("#000000"),
                Shader.TileMode.CLAMP,
            )
            canvas.drawText(hmStr, hmX, hmBaseline, pillTimePaint)
            pillTimePaint.shader = null

            val subX = hmX + hmW + 2f * dp
            if (amPmStr != null) canvas.drawText(amPmStr, subX, pillRect.top + 12f * dp, secAmPmPaint)
            if (secStr != null) canvas.drawText(":$secStr", subX, pillRect.bottom - 5f * dp, secAmPmPaint)
        }

        var bodyBottom = pillRect.bottom
        if (detailLayouts.isNotEmpty()) {
            val detailsTop = bodyTop + pillH + pillToDetailGap
            canvas.drawRect(
                RectF(boxLeft, detailsTop + 2f * dp, boxLeft + barW, detailsTop + detailsH - 2f * dp),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
            )
            var cy = detailsTop
            val textX = boxLeft + barW + barGap
            detailLayouts.forEachIndexed { idx, (_, layout) ->
                canvas.save()
                canvas.translate(textX, cy)
                layout.draw(canvas)
                canvas.restore()
                cy += layout.height
                if (idx < detailLayouts.lastIndex) cy += 4f * dp
            }
            bodyBottom = cy
        }

        drawExtrasBottom(canvas, boxLeft, bodyBottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + bodyW, boxTop + totalH),
            timeBounds = pillRect,
        )
    }

    /**
     * ID 20 (watermark_cover_id20): Work Report — exact match to `cover_wm20.webp`
     * Sharp warm amber header with left-aligned bold white title + light translucent gray-white body + clean 2-column rows
     */
    private fun drawId20WorkReport(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val cardW = min(215f * dp, photoWidth - outerMargin * 2f).coerceAtLeast(160f * dp)
        val radius = 2f * dp
        val padH = 10f * dp
        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Nhật ký công việc"
            AppLanguage.EN -> "Work Report"
            AppLanguage.ZH_CN -> "工作汇报"
        }
        val titleStr = template.title.trim().ifEmpty { defaultTitle }

        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15.5f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.WHITE
            applyTimemarkShadow(dp)
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.parseColor("#4A4A4A")
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = if (Color.red(textColor) < 100 && Color.green(textColor) < 100) textColor else Color.parseColor("#000000")
        }

        val rows = collectKeyValueRows(template, epochMillis, isPreview, includeDefaultFields = true, strings = strings)
        val headerH = 30f * dp
        val colGap = 10f * dp
        val rawMaxLabelW = rows.maxOfOrNull { labelPaint.measureText(it.first) } ?: 52f * dp
        val labelColW = rawMaxLabelW.coerceIn(48f * dp, cardW * 0.38f)
        val valMaxW = (cardW - padH * 2f - labelColW - colGap).roundToInt().coerceAtLeast(60)
        val preparedRows = rows.map { (k, v) ->
            val lLayout = StaticLayout.Builder.obtain(k, 0, k.length, labelPaint, labelColW.roundToInt().coerceAtLeast(40)).build()
            val vLayout = StaticLayout.Builder.obtain(v, 0, v.length, valuePaint, valMaxW).build()
            Triple(k, lLayout, vLayout)
        }

        val bodyPadV = 7f * dp
        var bodyH = bodyPadV * 2f
        preparedRows.forEachIndexed { idx, (_, ll, vl) ->
            bodyH += max(ll.height, vl.height)
            if (idx < preparedRows.lastIndex) bodyH += 4f * dp
        }
        val cardH = headerH + bodyH
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalW = max(cardW, extras.styledNoteBoxW)
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + cardH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = totalW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val cardTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val cardRect = RectF(boxLeft, cardTop, boxLeft + cardW, cardTop + cardH)

        val bodyAlpha = (template.theme.backgroundAlpha.coerceIn(0.25f, 0.96f) * 255f).roundToInt()
        val isLightText = Color.red(textColor) > 180 && Color.green(textColor) > 180
        val bodyBgColor = if (isLightText) Color.argb(bodyAlpha, 12, 18, 28) else Color.argb(bodyAlpha, 224, 226, 230)
        if (isLightText) {
            labelPaint.color = Color.argb(200, 255, 255, 255)
            valuePaint.color = Color.WHITE
        }

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(cardRect, radius, radius, Path.Direction.CW) })
        canvas.drawRect(
            RectF(cardRect.left, cardRect.top + headerH, cardRect.right, cardRect.bottom),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyBgColor },
        )
        val headerRect = RectF(cardRect.left, cardRect.top, cardRect.right, cardRect.top + headerH)
        canvas.drawRect(
            headerRect,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blendAlpha(accentColor, 0.94f) },
        )
        canvas.restore()

        val headerBaseline = headerRect.centerY() - (headerTitlePaint.ascent() + headerTitlePaint.descent()) / 2f
        canvas.drawText(titleStr, headerRect.left + padH, headerBaseline, headerTitlePaint)

        var cy = headerRect.bottom + bodyPadV
        var timeRowBounds = headerRect
        val valX = cardRect.left + padH + labelColW + colGap
        preparedRows.forEachIndexed { idx, (label, ll, vl) ->
            val rh = max(ll.height, vl.height)
            canvas.save(); canvas.translate(cardRect.left + padH, cy); ll.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(valX, cy); vl.draw(canvas); canvas.restore()
            if (label == strings.wmTime || label == "Thời gian" || label == "Time" || label == "时间") {
                timeRowBounds = RectF(cardRect.left, cy, cardRect.right, cy + rh)
            }
            cy += rh
            if (idx < preparedRows.lastIndex) cy += 4f * dp
        }

        drawExtrasBottom(canvas, boxLeft, cardRect.bottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + totalW, boxTop + totalH),
            timeBounds = timeRowBounds,
        )
    }

    /**
     * Full-width bottom location bar + top-right app tab + azimuth & weather icons
     */
    private fun drawId60LocationBar(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        weatherCode: Int?,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val barW = (photoWidth - outerMargin * 2f).coerceAtLeast(180f * dp)
        val padH = 12f * dp
        val padV = 8f * dp
        val stripH = 2.5f * dp
        val tabH = 20f * dp

        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * dp
            typeface = fonts.robotoCondensedBold
            color = textColor
            applyTimemarkShadow(dp)
        }
        val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val tabPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.WHITE
        }

        val rightColText = buildList {
            if (template.showCompass && template.compass.isNotBlank()) add(Triple(template.compass.trim(), "compass", true))
            if (template.showWeather && template.weather.isNotBlank()) add(Triple(template.weather.trim(), "weather", true))
            if (template.showAltitude && template.altitude.isNotBlank()) add(Triple(template.altitude.trim(), "alt", false))
        }
        val rightIconSize = 13f * dp
        val rightColW = rightColText.maxOfOrNull { (txt, _, hasIcon) ->
            infoPaint.measureText(txt) + if (hasIcon) rightIconSize + 4f * dp else 0f
        } ?: 0f
        val leftMaxW = (barW - padH * 2f - if (rightColW > 0f) rightColW + 14f * dp else 0f)
            .roundToInt().coerceAtLeast(100)

        val locale = strings.language.toLocale()
        val addrText = when {
            template.showAddress && template.smallPosition.isNotBlank() -> template.smallPosition.trim()
            template.showAddress && template.address.isNotBlank() -> template.address.trim()
            template.showAddress && isPreview -> strings.wmTapAddLocation
            else -> ""
        }
        val addrLayout = if (addrText.isNotEmpty()) {
            StaticLayout.Builder.obtain(addrText, 0, addrText.length, addrPaint, leftMaxW)
                .setLineSpacing(1.5f * dp, 1.0f).build()
        } else null

        val leftSubLines = buildList {
            if (template.showAddress && template.largePosition.isNotBlank() &&
                !template.largePosition.trim().equals(addrText, ignoreCase = true)
            ) {
                add(template.largePosition.trim() to false)
            }
            if (template.showCoordinates && template.coordinates.isNotBlank()) add(template.coordinates.trim() to true)
            if (template.showTime) {
                val d = TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)
                val t = TimeProvider.formatTemplateClock(epochMillis, template.use24Hour, template.showSeconds)
                add("$d $t" to false)
            }
            template.customFields.filter { it.enabled && it.value.isNotBlank() }.forEach {
                add("${it.label}: ${it.value.trim()}" to false)
            }
            if (template.showNote && template.note.isNotBlank()) add(template.note.trim() to false)
        }
        val subLayouts = leftSubLines.map { (txt, isMono) ->
            StaticLayout.Builder.obtain(txt, 0, txt.length, if (isMono) monoPaint else infoPaint, leftMaxW).build()
        }

        var leftH = if (addrLayout != null) addrLayout.height + 4f * dp else 0f
        subLayouts.forEachIndexed { idx, l ->
            leftH += l.height
            if (idx < subLayouts.lastIndex) leftH += 3f * dp
        }
        val rightH = rightColText.size * (textHeight(infoPaint) + 3f * dp)
        val contentH = max(leftH, rightH).coerceAtLeast(36f * dp)
        val barH = padV * 2f + contentH + stripH
        val totalH = tabH + barH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = barW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
            isFullWidth = true,
        )

        // Top-right mini app tab
        val bgAlpha = (template.theme.backgroundAlpha.coerceIn(0.15f, 0.90f) * 255f).roundToInt()
        val tabLabel = if (BrandAssetPackLoader.hasLocalBrandPack(context) || template.brandBadgePreset == BrandBadgePreset.VN_AUTHENTIC) {
            "Timemark Camera"
        } else {
            "Time Edit"
        }
        val tabIcon = BrandAssetPackLoader.loadAssetBitmap(context, "ic_launcher_round_xhey.webp")
        val tabIconW = if (tabIcon != null) 14f * dp else 8f * dp
        val tabW = 6f * dp + tabIconW + 4f * dp + tabPaint.measureText(tabLabel) + 6f * dp
        val tabRect = RectF(boxLeft + barW - tabW, boxTop, boxLeft + barW, boxTop + tabH)
        canvas.drawRect(tabRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 0, 0, 0) })
        if (tabIcon != null && !tabIcon.isRecycled) {
            val iy = tabRect.centerY() - tabIconW / 2f
            canvas.drawBitmap(tabIcon, null, RectF(tabRect.left + 4f * dp, iy, tabRect.left + 4f * dp + tabIconW, iy + tabIconW), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawCircle(tabRect.left + 8f * dp, tabRect.centerY(), 4f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor })
        }
        canvas.drawText(tabLabel, tabRect.left + 4f * dp + tabIconW + 4f * dp, tabRect.centerY() - (tabPaint.ascent() + tabPaint.descent()) / 2f, tabPaint)

        val barTop = boxTop + tabH
        val barRect = RectF(boxLeft, barTop, boxLeft + barW, barTop + barH)
        canvas.drawRect(barRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 0, 0, 0) })
        canvas.drawRect(
            RectF(barRect.left, barRect.bottom - stripH, barRect.right, barRect.bottom),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
        )

        var cy = barTop + padV
        if (addrLayout != null) {
            canvas.save(); canvas.translate(boxLeft + padH, cy); addrLayout.draw(canvas); canvas.restore()
            cy += addrLayout.height + 4f * dp
        }
        var timeRectOut = RectF(boxLeft, cy, boxLeft + barW * 0.6f, barRect.bottom)
        subLayouts.forEachIndexed { idx, l ->
            canvas.save(); canvas.translate(boxLeft + padH, cy); l.draw(canvas); canvas.restore()
            if (leftSubLines[idx].first.contains(":")) timeRectOut = RectF(boxLeft, cy, boxLeft + leftMaxW, cy + l.height)
            cy += l.height
            if (idx < subLayouts.lastIndex) cy += 3f * dp
        }

        if (rightColText.isNotEmpty()) {
            val azIcon = BrandAssetPackLoader.loadAssetBitmap(context, "icon_azimuth.webp")
            val weatherBmp = BrandAssetPackLoader.loadAssetBitmap(context, weatherAssetName(weatherCode))
            var ry = barRect.bottom - stripH - padV
            for (i in rightColText.lastIndex downTo 0) {
                val (txt, kind, hasIcon) = rightColText[i]
                val tw = infoPaint.measureText(txt)
                val tx = barRect.right - padH - tw
                canvas.drawText(txt, tx, ry - infoPaint.descent(), infoPaint)
                if (hasIcon) {
                    val iy = ry - textHeight(infoPaint) / 2f - rightIconSize / 2f
                    val iconRect = RectF(tx - rightIconSize - 4f * dp, iy, tx - 4f * dp, iy + rightIconSize)
                    val iconBmp = when (kind) {
                        "compass" -> azIcon
                        "weather" -> weatherBmp
                        else -> null
                    }
                    if (iconBmp != null && !iconBmp.isRecycled) {
                        canvas.drawBitmap(iconBmp, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    } else when (kind) {
                        "weather" -> drawWeatherSunFallback(canvas, iconRect.centerX(), iconRect.centerY(), rightIconSize * 0.45f)
                        "compass" -> canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), rightIconSize * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1.4f * dp
                            color = accentColor
                        })
                    }
                }
                ry -= (textHeight(infoPaint) + 3f * dp)
            }
        }

        drawExtrasBottom(canvas, boxLeft, barRect.bottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + barW, boxTop + totalH),
            timeBounds = timeRectOut,
        )
    }

    /**
     * ID 10 (new_watermark_cover_id10): Custom Text / Service
     */
    private fun drawId10CustomText(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val bgAlpha = (template.theme.backgroundAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
        val cardPad = if (bgAlpha > 3) 8f * dp else 0f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f * dp
            typeface = fonts.robotoCondensedBold
            color = textColor
            applyTimemarkShadow(dp)
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val maxRowW = min(230f * dp, photoWidth - outerMargin * 2f - cardPad * 2f - 12f * dp).coerceAtLeast(120f * dp)
        val titleStr = if (template.showTitle && template.title.isNotBlank()) template.title.trim() else null
        val titleLayout = titleStr?.let { StaticLayout.Builder.obtain(it, 0, it.length, titlePaint, maxRowW.roundToInt()).build() }

        val locale = strings.language.toLocale()
        val dateTimeStr = if (template.showTime) {
            "${TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)} ${TimeProvider.formatTemplateClock(epochMillis, template.use24Hour, template.showSeconds)}"
        } else null
        val items = collectDetailItems(template, includeDateAsDetail = template.showTime, dateStr = dateTimeStr, isPreview = isPreview, strings = strings)
        val rowLayouts = buildDetailStaticLayouts(items, maxRowW.roundToInt(), dp, rowPaint, monoPaint)

        val barW = 3f * dp
        val barGap = 8f * dp
        var bodyW = 0f
        var bodyH = 0f
        rowLayouts.forEachIndexed { idx, (_, l) ->
            bodyW = max(bodyW, (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f)
            bodyH += l.height
            if (idx < rowLayouts.lastIndex) bodyH += 3f * dp
        }
        val titleW = titleLayout?.let { l -> (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f } ?: 0f
        val titleH = titleLayout?.let { it.height + 6f * dp } ?: 0f
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f

        val cardW = max(max(titleW, barW + barGap + bodyW) + cardPad * 2f, extras.styledNoteBoxW).coerceAtLeast(110f * dp)
        val cardH = titleH + bodyH + cardPad * 2f
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + cardH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = cardW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val cardTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val cardRect = RectF(boxLeft, cardTop, boxLeft + cardW, cardTop + cardH)

        if (bgAlpha > 3) {
            canvas.drawRoundRect(cardRect, 6f * dp, 6f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 10, 17, 26) })
        }

        val startX = boxLeft + cardPad
        var cy = cardTop + cardPad
        if (titleLayout != null) {
            canvas.save(); canvas.translate(startX, cy); titleLayout.draw(canvas); canvas.restore()
            cy += titleLayout.height + 6f * dp
        }

        var timeBounds = RectF(startX, cy, startX + cardW, cy + 28f * dp)
        if (rowLayouts.isNotEmpty()) {
            canvas.drawRect(
                RectF(startX, cy + 2f * dp, startX + barW, cy + bodyH - 2f * dp),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor },
            )
            val rx = startX + barW + barGap
            rowLayouts.forEachIndexed { idx, (isTime, l) ->
                canvas.save(); canvas.translate(rx, cy); l.draw(canvas); canvas.restore()
                if (isTime) timeBounds = RectF(rx, cy, rx + bodyW, cy + l.height)
                cy += l.height
                if (idx < rowLayouts.lastIndex) cy += 3f * dp
            }
        }

        drawExtrasBottom(canvas, boxLeft, cardRect.bottom, dp, extras)
        return WatermarkRenderMetrics(mainBounds = RectF(boxLeft, boxTop, boxLeft + cardW, boxTop + totalH), timeBounds = timeBounds)
    }

    /**
     * Completed badge:
     * Compact dark-brown rounded pill (`#391A13`) with white `28dp` time + yellow delivery box icon,
     * followed below by yellow `#FFB100` bar + date + address (+ optional title when toggled on).
     */
    private fun drawId80Completed(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val headerBgColor = parseColorSafe(template.theme.accentColorHex, Color.parseColor("#391A13"))
        val badgeTitle = if (template.showTitle && template.title.isNotBlank()) template.title.trim() else null
        val subText = if (template.showSubtitle && template.subtitle.isNotBlank()) template.subtitle.trim() else null

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f * dp
            typeface = fonts.bigShouldersMedium
            color = Color.WHITE
            letterSpacing = 0.01f
        }
        val amPmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.WHITE
        }
        val companyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * dp
            typeface = fonts.robotoCondensedMedium
            color = Color.WHITE
        }
        val mainTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f * dp
            typeface = fonts.robotoCondensedBold
            color = textColor
            applyTimemarkShadow(dp)
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val timeStr = if (template.showTime) TimeProvider.formatTimeHourMinute(epochMillis, template.use24Hour) else ""
        val amPmStr = if (template.showTime && !template.use24Hour) TimeProvider.formatAmPm(epochMillis) else ""
        val iconSize = 24f * dp
        val padH = 10f * dp
        val padV = 6f * dp
        val timeW = if (timeStr.isNotEmpty()) timePaint.measureText(timeStr) else 0f
        val amPmW = if (amPmStr.isNotEmpty()) amPmPaint.measureText(amPmStr) + 3f * dp else 0f
        val topRowW = (if (timeStr.isNotEmpty()) timeW + amPmW + 10f * dp else 0f) + iconSize
        val subW = subText?.let { companyPaint.measureText(it) } ?: 0f
        val pillW = max(topRowW, subW) + padH * 2f
        val topRowH = 30f * dp
        val pillH = padV * 2f + topRowH + (subText?.let { textHeight(companyPaint) + 3f * dp } ?: 0f)

        val maxDetailsW = min(245f * dp, photoWidth - outerMargin * 2f - 14f * dp).coerceAtLeast(130f * dp)
        val locale = strings.language.toLocale()
        val dateStr = if (template.showTime) {
            TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)
        } else null
        val detailItems = collectDetailItems(template, includeDateAsDetail = true, dateStr = dateStr, isPreview = isPreview, strings = strings)
        val detailLayouts = buildDetailStaticLayouts(detailItems, maxDetailsW.roundToInt(), dp, detailPaint, monoPaint)

        var detailsW = badgeTitle?.let { mainTitlePaint.measureText(it) } ?: 0f
        var detailsH = badgeTitle?.let { textHeight(mainTitlePaint) + if (detailLayouts.isNotEmpty()) 4f * dp else 0f } ?: 0f
        detailLayouts.forEachIndexed { idx, (_, l) ->
            detailsW = max(detailsW, (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f)
            detailsH += l.height
            if (idx < detailLayouts.lastIndex) detailsH += 4f * dp
        }

        val barW = 2.5f * dp
        val barGap = 8f * dp
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val detailsGap = if (detailsH > 0f) 8f * dp else 0f
        val bodyW = max(max(pillW, if (detailsW > 0f) barW + barGap + detailsW else 0f), extras.styledNoteBoxW)
        val bodyH = pillH + detailsGap + detailsH
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + bodyH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = bodyW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val headerTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val headerRect = RectF(boxLeft, headerTop, boxLeft + pillW, headerTop + pillH)

        canvas.drawRoundRect(
            headerRect,
            5f * dp,
            5f * dp,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = blendAlpha(headerBgColor, template.theme.backgroundAlpha.coerceIn(0.45f, 1.0f))
            },
        )

        val rowCenterY = headerTop + padV + topRowH / 2f
        if (timeStr.isNotEmpty()) {
            val tx = boxLeft + padH
            val tb = Rect()
            timePaint.getTextBounds(timeStr, 0, timeStr.length, tb)
            val tBase = rowCenterY - (tb.top + tb.bottom) / 2f
            canvas.drawText(timeStr, tx, tBase, timePaint)
            if (amPmStr.isNotEmpty()) {
                canvas.drawText(amPmStr, tx + timeW + 3f * dp, tBase - 7f * dp, amPmPaint)
            }
        }

        val iconLeft = headerRect.right - padH - iconSize
        val iconTop = rowCenterY - iconSize / 2f
        val deliveryBmp = BrandAssetPackLoader.loadAssetBitmap(context, "icon_delivery_box.webp")
        if (deliveryBmp != null && !deliveryBmp.isRecycled) {
            canvas.drawBitmap(deliveryBmp, null, RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            drawDeliveryBoxFallback(canvas, iconLeft, iconTop, iconSize, dp)
        }

        if (subText != null) {
            canvas.drawText(subText, boxLeft + padH, headerRect.bottom - padV - companyPaint.descent(), companyPaint)
        }

        var bodyBottom = headerRect.bottom
        if (detailsH > 0f) {
            val detailsTop = headerRect.bottom + detailsGap
            canvas.drawRect(
                RectF(boxLeft, detailsTop + 2f * dp, boxLeft + barW, detailsTop + detailsH - 2f * dp),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB100") },
            )
            var cy = detailsTop
            val textX = boxLeft + barW + barGap
            if (badgeTitle != null) {
                canvas.drawText(badgeTitle, textX, cy - mainTitlePaint.ascent(), mainTitlePaint)
                cy += textHeight(mainTitlePaint) + if (detailLayouts.isNotEmpty()) 4f * dp else 0f
            }
            detailLayouts.forEachIndexed { idx, (_, l) ->
                canvas.save(); canvas.translate(textX, cy); l.draw(canvas); canvas.restore()
                cy += l.height
                if (idx < detailLayouts.lastIndex) cy += 4f * dp
            }
            bodyBottom = detailsTop + detailsH
        }

        drawExtrasBottom(canvas, boxLeft, bodyBottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + bodyW, boxTop + totalH),
            timeBounds = headerRect,
        )
    }

    /**
     * ID 90 (watermark_cover_id90): Cleaned
     */
    private fun drawId90Cleaned(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val primaryBlue = parseColorSafe(template.theme.accentColorHex, Color.parseColor("#004B92"))
        val secondaryCyan = Color.parseColor("#229CD7")
        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Hoàn thành"
            AppLanguage.EN -> "Cleaned"
            AppLanguage.ZH_CN -> "已清洁"
        }
        val titleStr = template.title.trim().ifEmpty { defaultTitle }

        val pillTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.WHITE
        }
        val pillTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f * dp
            typeface = fonts.bigShouldersMedium
            color = Color.WHITE
            letterSpacing = 0.02f
            applyTimemarkShadow(dp)
        }
        val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
            applyTimemarkShadow(dp)
        }
        val monoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.ptMonoRegular
            color = textColor
            applyTimemarkShadow(dp)
        }

        val starBmp = BrandAssetPackLoader.loadAssetBitmap(context, "star.png")
        val starH = 12f * dp
        val pillH = 32f * dp
        val leftSegW = max(pillTitlePaint.measureText(titleStr) + 22f * dp, 84f * dp)
        val timeStr = if (template.showTime) TimeProvider.formatTimeHourMinute(epochMillis, template.use24Hour) else ""
        val rightSegW = if (timeStr.isNotEmpty()) max(pillTimePaint.measureText(timeStr) + 16f * dp, 58f * dp) else 0f
        val splitBarW = leftSegW + rightSegW

        val maxDetailsW = min(245f * dp, photoWidth - outerMargin * 2f - 14f * dp).coerceAtLeast(130f * dp)
        val locale = strings.language.toLocale()
        val dateStr = if (template.showTime) {
            "${TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)} ${TimeProvider.formatWeekday(epochMillis, full = true, locale = locale)}"
        } else null
        val detailItems = collectDetailItems(template, includeDateAsDetail = true, dateStr = dateStr, isPreview = isPreview, strings = strings)
        val detailLayouts = buildDetailStaticLayouts(detailItems, maxDetailsW.roundToInt(), dp, detailPaint, monoPaint)

        var detailsW = 0f
        var detailsH = 0f
        detailLayouts.forEachIndexed { idx, (_, l) ->
            detailsW = max(detailsW, (0 until l.lineCount).maxOfOrNull { l.getLineWidth(it) } ?: 0f)
            detailsH += l.height
            if (idx < detailLayouts.lastIndex) detailsH += 4f * dp
        }

        val barW = 3f * dp
        val barGap = 8f * dp
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val bodyW = max(max(splitBarW, barW + barGap + detailsW), extras.styledNoteBoxW)
        val bodyH = starH + 2f * dp + pillH + (if (detailsH > 0f) 8f * dp + detailsH else 0f)
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + bodyH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = bodyW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val topAreaY = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f

        if (starBmp != null && !starBmp.isRecycled) {
            val aspect = starBmp.width.toFloat() / starBmp.height.coerceAtLeast(1).toFloat()
            val sw = starH * aspect
            canvas.drawBitmap(starBmp, null, RectF(boxLeft, topAreaY, boxLeft + sw, topAreaY + starH), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            drawVectorStars(canvas, boxLeft, topAreaY, starH, dp)
        }

        val pillTop = topAreaY + starH + 2f * dp
        val splitRect = RectF(boxLeft, pillTop, boxLeft + splitBarW, pillTop + pillH)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(splitRect, 2f * dp, 2f * dp, Path.Direction.CW) })
        canvas.drawRect(RectF(boxLeft, pillTop, boxLeft + leftSegW, pillTop + pillH), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryBlue })
        if (rightSegW > 0f) {
            canvas.drawRect(RectF(boxLeft + leftSegW, pillTop, boxLeft + splitBarW, pillTop + pillH), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = secondaryCyan })
        }
        canvas.restore()

        val titleW = pillTitlePaint.measureText(titleStr)
        canvas.drawText(titleStr, boxLeft + (leftSegW - titleW) / 2f, splitRect.centerY() - (pillTitlePaint.ascent() + pillTitlePaint.descent()) / 2f, pillTitlePaint)
        if (rightSegW > 0f) {
            val tw = pillTimePaint.measureText(timeStr)
            canvas.drawText(timeStr, boxLeft + leftSegW + (rightSegW - tw) / 2f, splitRect.centerY() - (pillTimePaint.ascent() + pillTimePaint.descent()) / 2f, pillTimePaint)
        }

        var bodyBottom = splitRect.bottom
        if (detailLayouts.isNotEmpty()) {
            val detailsTop = splitRect.bottom + 8f * dp
            canvas.drawRect(
                RectF(boxLeft, detailsTop + 3f * dp, boxLeft + barW, detailsTop + detailsH - 2f * dp),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryBlue },
            )
            var cy = detailsTop
            val rx = boxLeft + barW + barGap
            detailLayouts.forEachIndexed { idx, (_, l) ->
                canvas.save(); canvas.translate(rx, cy); l.draw(canvas); canvas.restore()
                cy += l.height
                if (idx < detailLayouts.lastIndex) cy += 4f * dp
            }
            bodyBottom = cy
        }

        drawExtrasBottom(canvas, boxLeft, bodyBottom, dp, extras)
        return WatermarkRenderMetrics(
            mainBounds = RectF(boxLeft, boxTop, boxLeft + bodyW, boxTop + totalH),
            timeBounds = splitRect,
        )
    }

    /**
     * ID 120 (watermark_cover_id120): Construction Record — exact match to `cover_wm120.webp`
     * Deep blue header (`#2A489E`) + centered bold white title + seamless gray-white translucent body +
     * aligned `Label : BoldValue` columns
     */
    private fun drawId120Construction(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val cardW = min(226f * dp, photoWidth - outerMargin * 2f).coerceAtLeast(168f * dp)
        val padH = 8f * dp
        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Hồ sơ kỹ thuật"
            AppLanguage.EN -> "Construction Record"
            AppLanguage.ZH_CN -> "工程记录"
        }
        val titleStr = template.title.trim().ifEmpty { defaultTitle }
        val subStr = if (template.showSubtitle && template.subtitle.isNotBlank()) template.subtitle.trim() else null

        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f * dp
            typeface = fonts.robotoCondensedBold
            color = Color.WHITE
        }
        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.WHITE
        }
        val isLightText = Color.red(textColor) > 180 && Color.green(textColor) > 180
        val labelColor = if (isLightText) Color.argb(215, 255, 255, 255) else Color.parseColor("#222222")
        val valueColor = if (isLightText) Color.WHITE else Color.parseColor("#000000")
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = labelColor
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedBold
            color = valueColor
        }

        val headerH = if (subStr != null) 44f * dp else 32f * dp
        val gapH = 0f
        val rows = collectKeyValueRows(template, epochMillis, isPreview, includeDefaultFields = true, strings = strings)
        val rawMaxLabelW = rows.maxOfOrNull { labelPaint.measureText(it.first) } ?: 54f * dp
        val labelColW = rawMaxLabelW.coerceIn(48f * dp, cardW * 0.36f)
        val colonW = labelPaint.measureText(" : ")
        val valMaxW = (cardW - padH * 2f - labelColW - colonW).roundToInt().coerceAtLeast(64)
        val preparedRows = rows.map { (k, v) ->
            val lLayout = StaticLayout.Builder.obtain(k, 0, k.length, labelPaint, labelColW.roundToInt().coerceAtLeast(40)).build()
            val vLayout = StaticLayout.Builder.obtain(v, 0, v.length, valuePaint, valMaxW).build()
            Triple(k, lLayout, vLayout)
        }

        val bodyPadV = 6f * dp
        var bodyH = bodyPadV * 2f
        preparedRows.forEachIndexed { idx, (_, ll, vl) ->
            bodyH += max(ll.height, vl.height)
            if (idx < preparedRows.lastIndex) bodyH += 4f * dp
        }
        val cardH = headerH + gapH + bodyH
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalW = max(cardW, extras.styledNoteBoxW)
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + cardH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = totalW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val cardTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val headerRect = RectF(boxLeft, cardTop, boxLeft + cardW, cardTop + headerH)
        canvas.drawRect(headerRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blendAlpha(accentColor, 0.94f) })
        if (subStr != null) {
            val tw = headerTitlePaint.measureText(titleStr)
            canvas.drawText(titleStr, headerRect.centerX() - tw / 2f, headerTopBaseline(headerRect.top + 6f * dp, headerTitlePaint), headerTitlePaint)
            val sw = headerSubPaint.measureText(subStr)
            canvas.drawText(subStr, headerRect.centerX() - sw / 2f, headerRect.bottom - 6f * dp, headerSubPaint)
        } else {
            val tw = headerTitlePaint.measureText(titleStr)
            canvas.drawText(titleStr, headerRect.centerX() - tw / 2f, headerRect.centerY() - (headerTitlePaint.ascent() + headerTitlePaint.descent()) / 2f, headerTitlePaint)
        }

        val bodyRect = RectF(boxLeft, headerRect.bottom + gapH, boxLeft + cardW, headerRect.bottom + gapH + bodyH)
        val whiteAlpha = (template.theme.backgroundAlpha.coerceIn(0.25f, 0.96f) * 255f).roundToInt()
        val bodyBgColor = if (isLightText) Color.argb(whiteAlpha, 12, 18, 28) else Color.argb(whiteAlpha, 226, 228, 232)
        canvas.drawRect(bodyRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bodyBgColor })

        var cy = bodyRect.top + bodyPadV
        var timeRowBounds = headerRect
        val colonX = boxLeft + padH + labelColW
        val valX = colonX + colonW
        preparedRows.forEachIndexed { idx, (label, ll, vl) ->
            val rh = max(ll.height, vl.height)
            canvas.save(); canvas.translate(boxLeft + padH, cy); ll.draw(canvas); canvas.restore()
            canvas.drawText(" :", colonX, cy - labelPaint.ascent(), labelPaint)
            canvas.save(); canvas.translate(valX, cy); vl.draw(canvas); canvas.restore()
            if (label == strings.wmTime || label == "Thời gian" || label == "Time" || label == "时间") {
                timeRowBounds = RectF(boxLeft, cy, boxLeft + cardW, cy + rh)
            }
            cy += rh
            if (idx < preparedRows.lastIndex) cy += 4f * dp
        }

        drawExtrasBottom(canvas, boxLeft, bodyRect.bottom, dp, extras)
        return WatermarkRenderMetrics(mainBounds = RectF(boxLeft, boxTop, boxLeft + totalW, boxTop + totalH), timeBounds = timeRowBounds)
    }

    /**
     * ID 100 / ID 110 (watermark_cover_id100): Sheet / Project table grid — exact match to `cover_wm100.webp`
     */
    private fun drawId100Sheet(
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val cardW = min(218f * dp, photoWidth - outerMargin * 2f).coerceAtLeast(170f * dp)
        val topBarH = 4f * dp
        val borderW = 1.2f * dp
        val divW = 0.8f * dp
        val labelColW = 63f * dp
        val cellPadH = 5f * dp
        val cellPadV = 5f * dp

        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Hồ sơ công việc"
            AppLanguage.EN -> "Project Report"
            AppLanguage.ZH_CN -> "工作表记录"
        }
        val titleStr = template.title.trim().ifEmpty { defaultTitle }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * dp
            typeface = fonts.robotoCondensedBold
            color = accentColor
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            typeface = fonts.robotoCondensedRegular
            color = textColor
        }

        val rows = collectKeyValueRows(template, epochMillis, isPreview, includeDefaultFields = true, strings = strings)
        val valMaxW = (cardW - labelColW - cellPadH * 3f).roundToInt().coerceAtLeast(60)
        val labelLayouts = rows.map { (k, _) ->
            StaticLayout.Builder.obtain(k, 0, k.length, cellPaint, (labelColW - cellPadH * 1.5f).roundToInt().coerceAtLeast(40)).build()
        }
        val valLayouts = rows.map { (_, v) ->
            StaticLayout.Builder.obtain(v, 0, v.length, cellPaint, valMaxW).build()
        }

        val titleLayout = StaticLayout.Builder.obtain(titleStr, 0, titleStr.length, titlePaint, (cardW - cellPadH * 2f).roundToInt()).build()
        val titleRowH = titleLayout.height + cellPadV * 2f
        val rowHeights = rows.indices.map { i -> max(labelLayouts[i].height, valLayouts[i].height) + cellPadV * 2f }
        val tableH = topBarH + titleRowH + rowHeights.sum()

        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalW = max(cardW, extras.styledNoteBoxW)
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + tableH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = totalW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val tableTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val tableRect = RectF(boxLeft, tableTop, boxLeft + cardW, tableTop + tableH)

        val bgAlpha = (template.theme.backgroundAlpha.coerceIn(0.22f, 0.88f) * 255f).roundToInt()
        canvas.drawRect(tableRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 44, 49, 56) })

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        // Top 4dp bar + Left/Right/Bottom 1.2dp crisp borders matching cover_wm100.webp
        canvas.drawRect(RectF(tableRect.left, tableRect.top, tableRect.right, tableRect.top + topBarH), linePaint)
        canvas.drawRect(RectF(tableRect.left, tableRect.top, tableRect.left + borderW, tableRect.bottom), linePaint)
        canvas.drawRect(RectF(tableRect.right - borderW, tableRect.top, tableRect.right, tableRect.bottom), linePaint)
        canvas.drawRect(RectF(tableRect.left, tableRect.bottom - borderW, tableRect.right, tableRect.bottom), linePaint)

        // Title row
        var cy = tableTop + topBarH
        canvas.save(); canvas.translate(boxLeft + cellPadH, cy + cellPadV); titleLayout.draw(canvas); canvas.restore()
        cy += titleRowH
        canvas.drawRect(RectF(tableRect.left, cy - divW, tableRect.right, cy), linePaint)

        // Vertical column divider (`63dp`) across body rows only
        val colDivX = boxLeft + labelColW
        canvas.drawRect(RectF(colDivX, cy, colDivX + divW, tableRect.bottom), linePaint)

        var timeBounds = RectF(boxLeft, tableTop, tableRect.right, tableTop + titleRowH + topBarH)
        rows.indices.forEach { i ->
            val rh = rowHeights[i]
            canvas.save(); canvas.translate(boxLeft + cellPadH, cy + cellPadV); labelLayouts[i].draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(colDivX + cellPadH, cy + cellPadV); valLayouts[i].draw(canvas); canvas.restore()
            if (rows[i].first == strings.wmTime || rows[i].first == "Thời gian" || rows[i].first == "Time" || rows[i].first == "时间") {
                timeBounds = RectF(boxLeft, cy, tableRect.right, cy + rh)
            }
            cy += rh
            if (i < rows.lastIndex) {
                canvas.drawRect(RectF(tableRect.left, cy - divW, tableRect.right, cy), linePaint)
            }
        }

        drawExtrasBottom(canvas, boxLeft, tableRect.bottom, dp, extras)
        return WatermarkRenderMetrics(mainBounds = RectF(boxLeft, boxTop, boxLeft + totalW, boxTop + totalH), timeBounds = timeBounds)
    }

    /**
     * ID 43 (watermark_cover_id43): Security Guard patrol card — exact match to `cover_wm43.webp`
     */
    private fun drawId43SecurityGuard(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        dp: Float,
        outerMargin: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
        accentColor: Int,
        textColor: Int,
        isPreview: Boolean,
        extras: CompositeExtras,
        strings: AppStrings,
    ): WatermarkRenderMetrics {
        val cardW = min(204f * dp, photoWidth - outerMargin * 2f).coerceAtLeast(164f * dp)
        val headerBg = parseColorSafe(template.theme.accentColorHex, Color.parseColor("#2B3B78"))
        val patrolYellow = Color.parseColor("#FFE64D")
        val bodyRoyalBlue = Color.parseColor("#3B55C4")
        val defaultTitle = when (strings.language) {
            AppLanguage.VI -> "Tuần tra"
            AppLanguage.EN -> "Security Guard"
            AppLanguage.ZH_CN -> "保安巡逻"
        }
        val titleStr = template.title.trim().ifEmpty { defaultTitle }
        val subStr = if (template.showSubtitle && template.subtitle.isNotBlank()) template.subtitle.trim() else null

        val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f * dp
            typeface = fonts.robotoCondensedBold
            color = patrolYellow
        }
        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.argb(235, 255, 255, 255)
        }
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f * dp
            typeface = fonts.bigShouldersMedium
            color = patrolYellow
            letterSpacing = 0.01f
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f * dp
            typeface = fonts.robotoCondensedMedium
            color = Color.WHITE
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * dp
            typeface = fonts.robotoCondensedRegular
            color = Color.WHITE
        }

        val badgeSize = 28f * dp
        val headerPad = 8f * dp
        val headerH = max(badgeSize + headerPad * 2f, if (subStr != null) 48f * dp else 42f * dp)
        val stripeH = 8f * dp

        val locale = strings.language.toLocale()
        val hmStr = if (template.showTime) TimeProvider.formatTimeHourMinute(epochMillis, template.use24Hour) else ""
        val amPmStr = if (template.showTime && !template.use24Hour) TimeProvider.formatAmPm(epochMillis) else ""
        val dateStr = if (template.showTime) TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale) else ""

        val rowMaxW = (cardW - 24f * dp).roundToInt().coerceAtLeast(90)
        val rows = collectKeyValueRows(template, epochMillis, isPreview, includeDefaultFields = true, includeTimeRow = false, strings = strings)
        val rowLayouts = buildBoldPrefixLayouts(rows, rowMaxW, dp, rowPaint)

        val timeRowH = if (template.showTime) 34f * dp else 0f
        var bodyH = 6f * dp + timeRowH + if (rowLayouts.isNotEmpty()) 5f * dp else 0f
        rowLayouts.forEachIndexed { idx, l ->
            bodyH += l.height
            if (idx < rowLayouts.lastIndex) bodyH += 4f * dp
        }
        bodyH += 8f * dp

        val cardH = headerH + 2f * dp + stripeH + bodyH
        val noteGap = if (extras.styledNoteBoxH > 0f) 6f * dp else 0f
        val totalW = max(cardW, extras.styledNoteBoxW)
        val totalH = (if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f) + cardH + extras.bottomExtra

        val (boxLeft, boxTop) = resolveWatermarkOrigin(
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            boxWidth = totalW,
            boxHeight = totalH,
            outerMargin = outerMargin,
            position = template.position,
        )

        drawExtrasTop(canvas, boxLeft, boxTop, dp, extras)
        val cardTop = boxTop + if (extras.styledNoteBoxH > 0f) extras.styledNoteBoxH + noteGap else 0f
        val fullCardRect = RectF(boxLeft, cardTop, boxLeft + cardW, cardTop + cardH)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(fullCardRect, 4f * dp, 4f * dp, Path.Direction.CW) })

        val headerRect = RectF(boxLeft, cardTop, boxLeft + cardW, cardTop + headerH)
        canvas.drawRect(headerRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blendAlpha(headerBg, 0.94f) })

        // Gold chevron/shield emblem on left
        val shieldRect = RectF(boxLeft + headerPad, cardTop + (headerH - badgeSize) / 2f, boxLeft + headerPad + badgeSize, cardTop + (headerH + badgeSize) / 2f)
        val goldenShield = BrandAssetPackLoader.loadAssetBitmap(context, "ic_security_golden.webp")
        if (goldenShield != null && !goldenShield.isRecycled) {
            canvas.drawBitmap(goldenShield, null, shieldRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            drawSecurityShieldFallback(canvas, shieldRect, dp)
        }

        val htx = shieldRect.right + 10f * dp
        if (subStr != null) {
            canvas.drawText(titleStr, htx, headerRect.top + 20f * dp, headerTitlePaint)
            canvas.drawText(subStr, htx, headerRect.bottom - 7f * dp, headerSubPaint)
        } else {
            canvas.drawText(titleStr, htx, headerRect.centerY() - (headerTitlePaint.ascent() + headerTitlePaint.descent()) / 2f, headerTitlePaint)
        }

        // Crisp 2dp bright yellow line + chevron `>>>>>` divider band
        val yellowLineRect = RectF(boxLeft, headerRect.bottom, boxLeft + cardW, headerRect.bottom + 2f * dp)
        canvas.drawRect(yellowLineRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = patrolYellow })

        val bodyAlpha = (template.theme.backgroundAlpha.coerceIn(0.45f, 0.95f) * 255f).roundToInt()
        val bodyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bodyAlpha, Color.red(bodyRoyalBlue), Color.green(bodyRoyalBlue), Color.blue(bodyRoyalBlue)) }
        val bodyTop = yellowLineRect.bottom
        val bodyRect = RectF(boxLeft, bodyTop, boxLeft + cardW, bodyTop + stripeH + bodyH)
        canvas.drawRect(bodyRect, bodyBgPaint)

        val dividerRect = RectF(boxLeft, bodyTop, boxLeft + cardW, bodyTop + stripeH)
        val dividerBmp = BrandAssetPackLoader.loadAssetBitmap(context, "id43_special_divider_golden.webp")
        if (dividerBmp != null && !dividerBmp.isRecycled) {
            canvas.drawBitmap(dividerBmp, null, dividerRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            drawCautionChevrons(canvas, dividerRect, dp)
        }

        var cy = dividerRect.bottom + 4f * dp
        var timeRect = headerRect
        if (template.showTime) {
            val tX = boxLeft + 10f * dp
            val tb = Rect()
            timePaint.getTextBounds(hmStr, 0, hmStr.length, tb)
            val tCenter = cy + timeRowH / 2f
            canvas.drawText(hmStr, tX, tCenter - (tb.top + tb.bottom) / 2f, timePaint)
            val rightX = tX + timePaint.measureText(hmStr) + 8f * dp
            if (amPmStr.isNotEmpty()) {
                canvas.drawText(amPmStr, rightX, cy + 12f * dp, datePaint)
            }
            canvas.drawText(dateStr, rightX, cy + timeRowH - 5f * dp, datePaint)
            timeRect = RectF(boxLeft, cy, boxLeft + cardW, cy + timeRowH)
            cy += timeRowH + 5f * dp
        }

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF0000") }
        rowLayouts.forEachIndexed { idx, l ->
            canvas.drawRoundRect(RectF(boxLeft + 10f * dp, cy + 2f * dp, boxLeft + 13f * dp, cy + 13.5f * dp), 1f * dp, 1f * dp, pillPaint)
            canvas.save(); canvas.translate(boxLeft + 17f * dp, cy); l.draw(canvas); canvas.restore()
            cy += l.height
            if (idx < rowLayouts.lastIndex) cy += 4f * dp
        }
        canvas.restore()

        drawExtrasBottom(canvas, boxLeft, bodyRect.bottom, dp, extras)
        return WatermarkRenderMetrics(mainBounds = RectF(boxLeft, boxTop, boxLeft + totalW, boxTop + totalH), timeBounds = timeRect)
    }

    /**
     * Exact reproduction of `LogoOutRenderWidget` independent user logo positioning.
     */
    private fun drawOuterLogo(
        canvas: Canvas,
        logoBitmap: Bitmap,
        photoWidth: Int,
        photoHeight: Int,
        baseDp: Float,
        template: WatermarkTemplate,
        mainBounds: RectF,
    ): RectF {
        val margin = 10f * baseDp
        val targetW = min(photoWidth, photoHeight) * template.logoScale.coerceIn(0.12f, 0.45f)
        val aspect = logoBitmap.height.coerceAtLeast(1).toFloat() / logoBitmap.width.coerceAtLeast(1).toFloat()
        val drawW = targetW
        val drawH = (targetW * aspect).coerceAtMost(photoHeight * 0.35f)

        val (left, top) = if (template.logoPosition != null) {
            resolveWatermarkOrigin(
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                boxWidth = drawW,
                boxHeight = drawH,
                outerMargin = margin,
                position = template.logoPosition,
            )
        } else {
            when (template.logoPlacement) {
                LogoPlacement.TOP_LEFT -> margin to margin
                LogoPlacement.TOP_RIGHT -> (photoWidth - margin - drawW) to margin
                LogoPlacement.BOTTOM_RIGHT -> (photoWidth - margin - drawW) to (photoHeight - margin - drawH)
                LogoPlacement.BOTTOM_LEFT -> margin to (photoHeight - margin - drawH)
                LogoPlacement.ABOVE_WATERMARK -> {
                    val l = mainBounds.left.coerceIn(margin, (photoWidth - margin - drawW).coerceAtLeast(margin))
                    val t = (mainBounds.top - 8f * baseDp - drawH).coerceAtLeast(margin)
                    l to t
                }
            }
        }
        val dest = RectF(left, top, left + drawW, top + drawH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (template.logoAlpha.coerceIn(0.1f, 1f) * 255f).roundToInt()
        }
        canvas.drawBitmap(logoBitmap, null, dest, paint)
        return dest
    }

    /**
     * Corner Brand Logo:
     * Uses the original Timemark badge presets (`VN_AUTHENTIC`, `ORIGINAL_*`), user custom image,
     * custom two-tone text (`TIME_EDIT_OSS`), or Off. Supports free repositioning via `template.brandPosition`.
     */
    private fun drawCornerBrandBadge(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        baseDp: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        customBrandBitmap: Bitmap?,
        mainBounds: RectF,
        logoBounds: RectF?,
        strings: AppStrings,
    ): RectF? {
        if (template.brandBadgePreset == BrandBadgePreset.OFF) return null

        val brandScale = template.brandBadgeScale.coerceIn(0.6f, 1.5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val customBrandPos = template.brandPosition

        if (template.brandBadgePreset == BrandBadgePreset.VN_AUTHENTIC) {
            val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val line1YellowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11.8f * baseDp * brandScale
                typeface = sansBold
                letterSpacing = -0.02f
                color = Color.parseColor("#FFC233")
                setShadowLayer(2.0f * baseDp, 0f, 0.8f * baseDp, Color.argb(150, 0, 0, 0))
            }
            val line1WhitePaint = TextPaint(line1YellowPaint).apply {
                color = Color.WHITE
            }
            val prefixW = line1YellowPaint.measureText("Time")
            val suffixW = line1WhitePaint.measureText("mark")
            val line1W = prefixW + suffixW
            val line1H = textHeight(line1YellowPaint)

            val line2Text = strings.brandAuthenticSubtitle
            val line2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 7.8f * baseDp * brandScale
                typeface = fonts.robotoCondensedRegular
                color = Color.argb(242, 255, 255, 255)
                setShadowLayer(1.8f * baseDp, 0f, 0.8f * baseDp, Color.argb(150, 0, 0, 0))
            }
            val measuredLine2W = line2Paint.measureText(line2Text)
            if (measuredLine2W > line1W * 1.04f && measuredLine2W > 0f) {
                line2Paint.textSize = line2Paint.textSize * (line1W * 1.02f / measuredLine2W)
            }
            val line2W = line2Paint.measureText(line2Text)
            val line2H = textHeight(line2Paint)

            val gapV = 1.0f * baseDp
            val badgeW = max(line1W, line2W)
            val badgeH = line1H + gapV + line2H
            val marginRight = 5.2f * baseDp
            val marginBottom = 5.0f * baseDp
            val br = RectF(
                photoWidth - marginRight - badgeW,
                photoHeight - marginBottom - badgeH,
                photoWidth - marginRight,
                photoHeight - marginBottom,
            )
            val chosen = if (customBrandPos != null) {
                val (ox, oy) = resolveWatermarkOrigin(
                    photoWidth = photoWidth,
                    photoHeight = photoHeight,
                    boxWidth = badgeW,
                    boxHeight = badgeH,
                    outerMargin = marginRight,
                    position = customBrandPos,
                    outerMarginVertical = marginBottom,
                )
                RectF(ox, oy, ox + badgeW, oy + badgeH)
            } else {
                pickNonCollidingCorner(br, badgeW, badgeH, photoWidth, photoHeight, 5.2f * baseDp, mainBounds, logoBounds)
            }

            if (customBrandPos == null && chosen == br) {
                val shadowBmp = BrandAssetPackLoader.loadAssetBitmap(context, "shadow_for_official_watermark.webp")
                if (shadowBmp != null && !shadowBmp.isRecycled) {
                    val sw = (shadowBmp.width / 3.0f) * baseDp * brandScale
                    val sh = (shadowBmp.height / 3.0f) * baseDp * brandScale
                    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 190 }
                    canvas.drawBitmap(shadowBmp, null, RectF(photoWidth - sw, photoHeight - sh, photoWidth.toFloat(), photoHeight.toFloat()), shadowPaint)
                }
            }

            val line1StartX = chosen.centerX() - line1W / 2f
            val line1Baseline = chosen.top - line1YellowPaint.ascent()
            canvas.drawText("Time", line1StartX, line1Baseline, line1YellowPaint)
            canvas.drawText("mark", line1StartX + prefixW, line1Baseline, line1WhitePaint)

            val line2Baseline = chosen.top + line1H + gapV - line2Paint.ascent() - 1.5f * baseDp
            canvas.drawText(line2Text, chosen.centerX() - line2W / 2f, line2Baseline, line2Paint)
            return chosen
        }

        if (template.brandBadgePreset == BrandBadgePreset.CUSTOM_IMAGE &&
            customBrandBitmap != null && !customBrandBitmap.isRecycled
        ) {
            val margin = 8f * baseDp
            val drawW = 86f * baseDp * brandScale
            val drawH = drawW * (customBrandBitmap.height.toFloat() / customBrandBitmap.width.coerceAtLeast(1).toFloat())
            val dest = if (customBrandPos != null) {
                val (ox, oy) = resolveWatermarkOrigin(photoWidth, photoHeight, drawW, drawH, margin, customBrandPos)
                RectF(ox, oy, ox + drawW, oy + drawH)
            } else {
                val br = RectF(photoWidth - margin - drawW, photoHeight - margin - drawH, photoWidth - margin, photoHeight - margin)
                pickNonCollidingCorner(br, drawW, drawH, photoWidth, photoHeight, margin, mainBounds, logoBounds)
            }
            canvas.drawBitmap(customBrandBitmap, null, dest, paint)
            return dest
        }

        val assetName = when (template.brandBadgePreset) {
            BrandBadgePreset.ORIGINAL_COLOR -> "official_wm_new.webp"
            BrandBadgePreset.ORIGINAL_VN -> "official_watermark2_vn.png"
            BrandBadgePreset.ORIGINAL_EN -> "official_watermark2_en.png"
            BrandBadgePreset.ORIGINAL_ZH -> "official_watermark2_zh.png"
            else -> null
        }

        val brandBmp = assetName?.let { BrandAssetPackLoader.loadAssetBitmap(context, it) }
        if (brandBmp != null && !brandBmp.isRecycled) {
            // Original xxhdpi (`3.0f`) size scaled by baseDp * brandScale
            val drawW = (brandBmp.width / 3.0f) * baseDp * brandScale
            val drawH = (brandBmp.height / 3.0f) * baseDp * brandScale
            if (customBrandPos != null) {
                val visibleW = drawW * 0.75f
                val visibleH = drawH * 0.48f
                val margin = 6f * baseDp
                val (ox, oy) = resolveWatermarkOrigin(photoWidth, photoHeight, visibleW, visibleH, margin, customBrandPos)
                val destRect = RectF(ox - drawW * 0.25f, oy - drawH * 0.52f, ox + visibleW, oy + visibleH)
                canvas.drawBitmap(brandBmp, null, destRect, paint)
                return RectF(ox, oy, ox + visibleW, oy + visibleH)
            }
            val flushRightRect = RectF(photoWidth - drawW, photoHeight - drawH, photoWidth.toFloat(), photoHeight.toFloat())
            // Text sits in the lower-right ~50% of the padded square canvas
            val visibleHitBr = RectF(
                flushRightRect.left + drawW * 0.25f,
                flushRightRect.top + drawH * 0.52f,
                flushRightRect.right,
                flushRightRect.bottom,
            )
            val collidesBr = overlaps(visibleHitBr, mainBounds) ||
                (logoBounds != null && overlaps(visibleHitBr, logoBounds))
            if (!collidesBr) {
                val shadowBmp = BrandAssetPackLoader.loadAssetBitmap(context, "shadow_for_official_watermark.webp")
                if (shadowBmp != null && !shadowBmp.isRecycled) {
                    val sw = (shadowBmp.width / 3.0f) * baseDp * brandScale
                    val sh = (shadowBmp.height / 3.0f) * baseDp * brandScale
                    canvas.drawBitmap(shadowBmp, null, RectF(photoWidth - sw, photoHeight - sh, photoWidth.toFloat(), photoHeight.toFloat()), paint)
                }
                canvas.drawBitmap(brandBmp, null, flushRightRect, paint)
                return visibleHitBr
            } else {
                val trDest = RectF(photoWidth - drawW - 4f * baseDp, 6f * baseDp - drawH * 0.56f, photoWidth - 4f * baseDp, 6f * baseDp + drawH * 0.44f)
                val trVisible = RectF(trDest.left + drawW * 0.25f, 6f * baseDp, trDest.right, 6f * baseDp + drawH * 0.44f)
                val useTr = !overlaps(trVisible, mainBounds) &&
                    (logoBounds == null || !overlaps(trVisible, logoBounds))
                val chosenDest = if (useTr) {
                    trDest
                } else {
                    RectF(8f * baseDp - drawW * 0.28f, photoHeight - drawH - 4f * baseDp, 8f * baseDp + drawW * 0.72f, photoHeight - 4f * baseDp)
                }
                canvas.drawBitmap(brandBmp, null, chosenDest, paint)
                return if (useTr) {
                    trVisible
                } else {
                    RectF(chosenDest.left + drawW * 0.25f, chosenDest.top + drawH * 0.52f, chosenDest.right, chosenDest.bottom)
                }
            }
        }

        // Programmatic Text Fallback / Custom Brand Text (`TIME_EDIT_OSS`)
        val (prefix, suffix, subtitle) = when (template.brandBadgePreset) {
            BrandBadgePreset.ORIGINAL_COLOR,
            BrandBadgePreset.ORIGINAL_VN,
            BrandBadgePreset.ORIGINAL_EN,
            BrandBadgePreset.ORIGINAL_ZH,
            -> Triple(
                "Time",
                "mark",
                when (template.brandBadgePreset) {
                    BrandBadgePreset.ORIGINAL_VN -> "Máy ảnh"
                    BrandBadgePreset.ORIGINAL_ZH -> ""
                    else -> "Camera"
                },
            )
            else -> Triple(
                template.customBrandPrefix.ifBlank { "Time" },
                template.customBrandSuffix,
                template.customBrandSubtitle,
            )
        }
        val line1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15.5f * baseDp * brandScale
            typeface = fonts.montserratBlack
        }
        val line2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * baseDp * brandScale
            typeface = fonts.robotoCondensedBold
        }
        val prefixW = line1Paint.measureText(prefix)
        val suffixW = if (suffix.isNotEmpty()) line1Paint.measureText(suffix) else 0f
        val line1W = prefixW + suffixW
        val line2W = if (subtitle.isNotBlank()) line2Paint.measureText(subtitle) else 0f
        val badgeW = max(line1W, line2W)
        val badgeH = textHeight(line1Paint) + if (subtitle.isNotBlank()) textHeight(line2Paint) else 0f
        val margin = 10f * baseDp
        val chosen = if (customBrandPos != null) {
            val (ox, oy) = resolveWatermarkOrigin(photoWidth, photoHeight, badgeW, badgeH, margin, customBrandPos)
            RectF(ox, oy, ox + badgeW, oy + badgeH)
        } else {
            val br = RectF(photoWidth - margin - badgeW, photoHeight - margin - badgeH, photoWidth - margin, photoHeight - margin)
            pickNonCollidingCorner(br, badgeW, badgeH, photoWidth, photoHeight, margin, mainBounds, logoBounds)
        }

        val line1StartX = chosen.centerX() - line1W / 2f
        val line1Baseline = chosen.top - line1Paint.ascent()
        val accentHex = if (template.brandBadgePreset == BrandBadgePreset.ORIGINAL_COLOR || template.brandBadgePreset == BrandBadgePreset.TIME_EDIT_OSS) {
            Color.parseColor("#FFC233")
        } else Color.WHITE
        drawStrokedShadowText(canvas, prefix, line1StartX, line1Baseline, line1Paint, accentHex, baseDp)
        if (suffix.isNotEmpty()) {
            drawStrokedShadowText(canvas, suffix, line1StartX + prefixW, line1Baseline, line1Paint, Color.WHITE, baseDp)
        }
        if (subtitle.isNotBlank()) {
            val line2Baseline = chosen.top + textHeight(line1Paint) - line2Paint.ascent() - 2f * baseDp
            drawStrokedShadowText(canvas, subtitle, chosen.centerX() - line2W / 2f, line2Baseline, line2Paint, Color.WHITE, baseDp)
        }
        return chosen
    }

    private fun pickNonCollidingCorner(
        preferredBr: RectF,
        w: Float,
        h: Float,
        photoW: Int,
        photoH: Int,
        margin: Float,
        mainBounds: RectF,
        logoBounds: RectF?,
    ): RectF {
        val bl = RectF(margin, photoH - margin - h, margin + w, photoH - margin)
        val tr = RectF(photoW - margin - w, margin, photoW - margin, margin + h)
        val tl = RectF(margin, margin, margin + w, margin + h)
        fun isFree(rect: RectF): Boolean =
            !overlaps(rect, mainBounds) && (logoBounds == null || !overlaps(rect, logoBounds))
        return when {
            isFree(preferredBr) -> preferredBr
            isFree(bl) -> bl
            isFree(tr) -> tr
            else -> tl
        }
    }

    private fun drawVerticalPhotoCodeStrip(
        context: Context,
        canvas: Canvas,
        photoWidth: Int,
        photoHeight: Int,
        baseDp: Float,
        fonts: TimemarkTypefaces,
        template: WatermarkTemplate,
        epochMillis: Long,
    ) {
        if (!template.showPhotoCode) return

        val code = generatePhotoCode(epochMillis, template.id)
        val labelText = "Timemark Verified"

        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * baseDp
            typeface = fonts.ptMonoRegular
            color = Color.argb(235, 255, 255, 255)
            setShadowLayer(2.2f * baseDp, 0f, 0.8f * baseDp, Color.argb(150, 0, 0, 0))
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * baseDp
            typeface = fonts.robotoCondensedRegular
            color = Color.argb(235, 255, 255, 255)
            setShadowLayer(2.2f * baseDp, 0f, 0.8f * baseDp, Color.argb(150, 0, 0, 0))
        }

        val shieldBmp = BrandAssetPackLoader.loadAssetBitmap(context, "anti_code_icon_small.webp")
            ?: BrandAssetPackLoader.loadAssetBitmap(context, "anti_code_icon_large.webp")
        val iconW = 9f * baseDp
        val iconH = 10f * baseDp
        val iconGap = 4f * baseDp
        val midGap = 8f * baseDp

        val codeW = codePaint.measureText(code)
        val labelW = labelPaint.measureText(labelText)
        val totalSpan = iconW + iconGap + codeW + midGap + labelW

        val stripCx = photoWidth - 9.5f * baseDp
        val stripCy = photoHeight * 0.44f

        canvas.save()
        canvas.translate(stripCx, stripCy)
        canvas.rotate(-90f)

        var x = -totalSpan / 2f
        if (shieldBmp != null && !shieldBmp.isRecycled) {
            canvas.drawBitmap(
                shieldBmp,
                null,
                RectF(x, -iconH / 2f, x + iconW, iconH / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            drawVectorShield(canvas, x, -iconH / 2f, iconW)
        }
        x += iconW + iconGap

        val codeBaseline = -(codePaint.ascent() + codePaint.descent()) / 2f
        canvas.drawText(code, x, codeBaseline, codePaint)
        x += codeW + midGap

        val labelBaseline = -(labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText(labelText, x, labelBaseline, labelPaint)

        canvas.restore()
    }

    private fun overlaps(a: RectF, b: RectF): Boolean = RectF.intersects(a, b)

    private fun drawVectorShield(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path().apply {
            moveTo(x + size * 0.5f, y)
            lineTo(x + size * 0.9f, y + size * 0.2f)
            lineTo(x + size * 0.85f, y + size * 0.65f)
            lineTo(x + size * 0.5f, y + size)
            lineTo(x + size * 0.15f, y + size * 0.65f)
            lineTo(x + size * 0.1f, y + size * 0.2f)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = size * 0.14f
        })
    }

    private data class DetailEntry(val text: String, val isMono: Boolean = false, val isTime: Boolean = false)

    private fun collectDetailItems(
        template: WatermarkTemplate,
        includeDateAsDetail: Boolean,
        dateStr: String?,
        isPreview: Boolean,
        strings: AppStrings,
    ): List<DetailEntry> {
        val list = mutableListOf<DetailEntry>()
        if (includeDateAsDetail && !dateStr.isNullOrBlank()) list += DetailEntry(dateStr, isTime = true)
        if (template.showAddress) {
            val addr = template.address.trim()
            if (addr.isNotEmpty()) list += DetailEntry(addr)
            else if (isPreview) list += DetailEntry(strings.wmTapAddLocation)
        }
        if (template.showCoordinates && template.coordinates.isNotBlank()) list += DetailEntry(template.coordinates.trim(), isMono = true)
        val env = buildList {
            if (template.showWeather && template.weather.isNotBlank()) add(template.weather.trim())
            if (template.showAltitude && template.altitude.isNotBlank()) add(template.altitude.trim())
            if (template.showCompass && template.compass.isNotBlank()) add(template.compass.trim())
        }
        if (env.isNotEmpty()) list += DetailEntry(env.joinToString("  "))
        template.customFields.filter { it.enabled && it.value.isNotBlank() }.forEach { f ->
            list += DetailEntry("${f.label}: ${f.value.trim()}")
        }
        if (template.showNote && template.note.isNotBlank()) list += DetailEntry("${strings.wmNote}: ${template.note.trim()}")
        return list
    }

    private fun collectKeyValueRows(
        template: WatermarkTemplate,
        epochMillis: Long,
        isPreview: Boolean,
        includeDefaultFields: Boolean,
        includeTimeRow: Boolean = true,
        strings: AppStrings,
    ): List<Pair<String, String>> = buildList {
        val locale = strings.language.toLocale()
        if (includeDefaultFields) {
            template.customFields.filter { it.enabled && it.value.isNotBlank() }.forEach {
                add(it.label to it.value.trim())
            }
        }
        if (includeTimeRow && template.showTime) {
            val t = TimeProvider.formatTemplateClock(epochMillis, template.use24Hour, template.showSeconds)
            val d = TimeProvider.formatDateOnly(epochMillis, template.dateFormat.pattern, locale = locale)
            add(strings.wmTime to "$d $t")
        }
        if (template.showWeather && template.weather.isNotBlank()) add(strings.wmWeather to template.weather.trim())
        if (template.showAddress) {
            val addr = template.address.trim()
            if (addr.isNotEmpty()) add(strings.wmLocation to addr)
            else if (isPreview) add(strings.wmLocation to strings.wmTapAddLocation)
        }
        if (template.showCoordinates && template.coordinates.isNotBlank()) add(strings.wmCoordinates to template.coordinates.trim())
        if (template.showAltitude && template.altitude.isNotBlank()) add(strings.wmAltitude to template.altitude.trim())
        if (template.showCompass && template.compass.isNotBlank()) add(strings.wmCompass to template.compass.trim())
        if (template.showNote && template.note.isNotBlank()) add(strings.wmNote to template.note.trim())
    }

    private fun buildDetailStaticLayouts(
        items: List<DetailEntry>,
        maxWidth: Int,
        dp: Float,
        regularPaint: TextPaint,
        monoPaint: TextPaint,
        lineSpacingExtraDp: Float = 2f,
    ): List<Pair<Boolean, StaticLayout>> = items.map { entry ->
        val p = if (entry.isMono) monoPaint else regularPaint
        val layout = StaticLayout.Builder.obtain(entry.text, 0, entry.text.length, p, maxWidth.coerceAtLeast(80))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(lineSpacingExtraDp * dp, 1.0f)
            .build()
        entry.isTime to layout
    }

    private fun buildBoldPrefixLayouts(
        rows: List<Pair<String, String>>,
        innerW: Int,
        dp: Float,
        paint: TextPaint,
    ): List<StaticLayout> = rows.map { (k, v) ->
        val full = "$k: $v"
        val builder = SpannableStringBuilder(full).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, min(k.length + 1, full.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        StaticLayout.Builder.obtain(builder, 0, builder.length, paint, innerW.coerceAtLeast(60))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f * dp, 1.0f)
            .build()
    }

    private fun headerTopBaseline(topY: Float, paint: TextPaint): Float = topY - paint.ascent()

    private fun TextPaint.applyTimemarkShadow(dp: Float) {
        val r = (3.5f * dp).coerceIn(1f, 22f)
        setShadowLayer(r, 0f, (1.5f * dp).coerceIn(0.5f, 10f), Color.argb(120, 0, 0, 0))
    }

    private fun drawStrokedShadowText(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        basePaint: TextPaint,
        fillColor: Int,
        dp: Float,
        strokeWidthDp: Float = 0.75f,
    ) {
        val strokePaint = TextPaint(basePaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (strokeWidthDp * dp).coerceIn(0.4f, 6f)
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(135, 0, 0, 0)
            setShadowLayer((3.5f * dp).coerceIn(1f, 22f), 0f, (1.5f * dp).coerceIn(0.5f, 10f), Color.argb(120, 0, 0, 0))
        }
        canvas.drawText(text, x, baselineY, strokePaint)

        val fillPaint = TextPaint(basePaint).apply {
            style = Paint.Style.FILL
            color = fillColor
            clearShadowLayer()
        }
        canvas.drawText(text, x, baselineY, fillPaint)
    }

    private fun textHeight(paint: TextPaint): Float = paint.descent() - paint.ascent()

    private fun parseColorSafe(hex: String, fallback: Int): Int =
        runCatching { Color.parseColor(hex) }.getOrDefault(fallback)

    private fun weatherAssetName(weatherCode: Int?): String = when (weatherCode) {
        null, 0 -> "icon_weather_sunny.webp"
        1 -> "icon_weather_cloudy1.webp"
        2 -> "icon_weather_cloudy2.webp"
        3 -> "icon_weather_cloudy3.webp"
        45, 48 -> "icon_weather_fog.webp"
        51, 53, 55, 56, 57, 61, 80 -> "icon_weather_rain1.webp"
        63, 66, 81 -> "icon_weather_rain2.webp"
        65, 67, 82, 95, 96, 99 -> "icon_weather_rain3.webp"
        71, 73, 75, 77, 85, 86 -> "icon_weather_snowny.webp"
        else -> "icon_weather_cloudy2.webp"
    }

    private fun drawWeatherSunFallback(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC233") }
        canvas.drawCircle(cx, cy, r * 0.58f, sunPaint)
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFC233")
            strokeWidth = r * 0.18f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 8) {
            val angle = i * (PI / 4.0)
            canvas.drawLine(
                cx + (r * 0.72f * cos(angle)).toFloat(),
                cy + (r * 0.72f * sin(angle)).toFloat(),
                cx + (r * 1.0f * cos(angle)).toFloat(),
                cy + (r * 1.0f * sin(angle)).toFloat(),
                rayPaint,
            )
        }
    }

    private fun drawDeliveryBoxFallback(canvas: Canvas, left: Float, top: Float, size: Float, dp: Float) {
        val boxRect = RectF(left + size * 0.08f, top + size * 0.12f, left + size * 0.92f, top + size * 0.92f)
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB100") }
        canvas.drawRoundRect(boxRect, 2f * dp, 2f * dp, goldPaint)
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#391A13")
            style = Paint.Style.STROKE
            strokeWidth = (1.8f * dp).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(boxRect.centerX(), boxRect.top, boxRect.centerX(), boxRect.top + boxRect.height() * 0.25f, darkPaint)
        canvas.drawLine(boxRect.left, boxRect.top + boxRect.height() * 0.25f, boxRect.right, boxRect.top + boxRect.height() * 0.25f, darkPaint)
        val p = Path().apply {
            moveTo(boxRect.left + boxRect.width() * 0.26f, boxRect.top + boxRect.height() * 0.60f)
            lineTo(boxRect.left + boxRect.width() * 0.44f, boxRect.top + boxRect.height() * 0.77f)
            lineTo(boxRect.left + boxRect.width() * 0.76f, boxRect.top + boxRect.height() * 0.43f)
        }
        canvas.drawPath(p, darkPaint)
    }

    private fun drawVectorStars(canvas: Canvas, left: Float, top: Float, starH: Float, dp: Float) {
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFE135") }
        val rOuter = starH * 0.50f
        val rInner = rOuter * 0.42f
        val step = starH + 4f * dp
        for (i in 0 until 5) {
            val cx = left + rOuter + i * step
            val cy = top + starH * 0.5f
            val path = Path()
            for (pt in 0 until 10) {
                val r = if (pt % 2 == 0) rOuter else rInner
                val angle = -PI / 2.0 + pt * (PI / 5.0)
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (pt == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, starPaint)
        }
    }

    private fun drawSecurityShieldFallback(canvas: Canvas, rect: RectF, dp: Float) {
        val cx = rect.centerX()
        val path = Path().apply {
            moveTo(cx, rect.top + rect.height() * 0.05f)
            lineTo(rect.right - rect.width() * 0.08f, rect.top + rect.height() * 0.22f)
            lineTo(rect.right - rect.width() * 0.14f, rect.top + rect.height() * 0.68f)
            lineTo(cx, rect.bottom - rect.height() * 0.04f)
            lineTo(rect.left + rect.width() * 0.14f, rect.top + rect.height() * 0.68f)
            lineTo(rect.left + rect.width() * 0.08f, rect.top + rect.height() * 0.22f)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B2A9E") })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFE64D")
                style = Paint.Style.STROKE
                strokeWidth = (1.8f * dp).coerceAtLeast(1f)
                strokeJoin = Paint.Join.ROUND
            },
        )
        val check = Path().apply {
            moveTo(rect.left + rect.width() * 0.30f, rect.top + rect.height() * 0.50f)
            lineTo(rect.left + rect.width() * 0.45f, rect.top + rect.height() * 0.64f)
            lineTo(rect.left + rect.width() * 0.72f, rect.top + rect.height() * 0.36f)
        }
        canvas.drawPath(
            check,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFE64D")
                style = Paint.Style.STROKE
                strokeWidth = (2.0f * dp).coerceAtLeast(1f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            },
        )
    }

    private fun drawCautionChevrons(canvas: Canvas, rect: RectF, dp: Float) {
        val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6D55B")
            style = Paint.Style.STROKE
            strokeWidth = (1.1f * dp).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val step = 5f * dp
        var x = rect.left + 3f * dp
        val midY = rect.centerY()
        val halfH = rect.height() * 0.30f
        while (x + step * 0.7f <= rect.right - 2f * dp) {
            val p = Path().apply {
                moveTo(x, midY - halfH)
                lineTo(x + step * 0.55f, midY)
                lineTo(x, midY + halfH)
            }
            canvas.drawPath(p, chevronPaint)
            x += step
        }
    }

    private fun blendAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
