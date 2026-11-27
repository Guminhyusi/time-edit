/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pastel atmospheric canvas backdrop behind Liquid Glass surfaces in Light mode,
 * or exact solid dark surface in Dark mode.
 */
fun Modifier.liquidGlassBackdrop(
    palette: TimeEditPalette,
    darkColor: Color = palette.cameraScaffoldBg,
): Modifier {
    if (!palette.isLight) return this.background(darkColor)
    return this.drawWithCache {
        val baseGradient = Brush.verticalGradient(
            colors = listOf(
                ChampagneCream,
                FrostPink,
                PeachApricot,
            ),
        )
        val topLeftOrb = Brush.radialGradient(
            colors = listOf(BlushRose.copy(alpha = 0.72f), Color.Transparent),
            center = Offset(size.width * 0.14f, size.height * 0.12f),
            radius = size.maxDimension * 0.48f,
        )
        val midRightOrb = Brush.radialGradient(
            colors = listOf(PeachApricot.copy(alpha = 0.78f), Color.Transparent),
            center = Offset(size.width * 0.90f, size.height * 0.44f),
            radius = size.maxDimension * 0.45f,
        )
        val bottomLeftOrb = Brush.radialGradient(
            colors = listOf(ChampagneCream.copy(alpha = 0.85f), Color.Transparent),
            center = Offset(size.width * 0.18f, size.height * 0.86f),
            radius = size.maxDimension * 0.46f,
        )
        val bottomRightOrb = Brush.radialGradient(
            colors = listOf(BlushRose.copy(alpha = 0.68f), Color.Transparent),
            center = Offset(size.width * 0.84f, size.height * 0.92f),
            radius = size.maxDimension * 0.45f,
        )
        onDrawBehind {
            drawRect(brush = baseGradient)
            drawRect(brush = topLeftOrb)
            drawRect(brush = midRightOrb)
            drawRect(brush = bottomLeftOrb)
            drawRect(brush = bottomRightOrb)
        }
    }
}

/**
 * Translucent Liquid Glass panel modifier for Light mode, using directional white-to-rose
 * specular border and top glass sheen over frosted pastel gradients.
 */
fun Modifier.liquidGlassCard(
    palette: TimeEditPalette,
    shape: Shape,
    selected: Boolean = false,
    elevated: Boolean = true,
    opaqueModal: Boolean = false,
): Modifier {
    if (!palette.isLight) return this.clip(shape)
    val fillColors = when {
        opaqueModal -> listOf(Color(0xFEFFF6E4), Color(0xFCFFE8EC))
        selected -> listOf(palette.glassSelectedTop, palette.glassSelectedBottom)
        else -> listOf(palette.glassFillTop, palette.glassFillBottom)
    }
    val borderBrush = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xF5FFFFFF),
                palette.accent.copy(alpha = 0.80f),
                palette.borderStroke,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                palette.glassHighlightRim,
                BlushRose.copy(alpha = 0.55f),
                palette.glassShadowRim,
            ),
        )
    }
    val borderWidth = if (selected) 1.4.dp else 1.1.dp
    val sheenAlpha = if (elevated) 0.36f else 0.24f

    return this
        .clip(shape)
        .background(brush = Brush.verticalGradient(fillColors), shape = shape)
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.52f,
                ),
            )
        }
        .border(width = borderWidth, brush = borderBrush, shape = shape)
}

/**
 * Unified glass-aware container that renders exact standard Material [Surface] in Dark mode
 * and layered frosted Liquid Glass in Light mode.
 */
@Composable
fun LiquidGlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    elevated: Boolean = true,
    opaqueModal: Boolean = false,
    darkColor: Color? = null,
    darkBorder: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalTimeEditColors.current
    if (!palette.isLight) {
        val baseColor = darkColor ?: if (selected) palette.accentContainer else palette.surfaceElevated
        val clippedModifier = modifier.clip(shape).let { m ->
            if (onClick != null) m.clickable(enabled = enabled, role = role, onClick = onClick) else m
        }
        Surface(
            color = baseColor,
            shape = shape,
            border = darkBorder,
            shadowElevation = shadowElevation,
            modifier = clippedModifier,
            content = content,
        )
        return
    }

    val glassModifier = modifier
        .liquidGlassCard(
            palette = palette,
            shape = shape,
            selected = selected,
            elevated = elevated,
            opaqueModal = opaqueModal,
        )
        .let { m ->
            if (onClick != null) m.clickable(enabled = enabled, role = role, onClick = onClick) else m
        }

    Box(modifier = glassModifier) {
        content()
    }
}
