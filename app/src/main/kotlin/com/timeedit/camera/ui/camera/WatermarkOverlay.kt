/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.camera

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.sensors.FrameEdge
import com.timeedit.camera.core.sensors.OrientationMath
import com.timeedit.camera.domain.watermark.BrandBadgePreset
import com.timeedit.camera.domain.watermark.WatermarkCorner
import com.timeedit.camera.domain.watermark.WatermarkPosition
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.domain.watermark.clampFreePosition
import com.timeedit.camera.engine.renderer.BrandAssetPackLoader
import com.timeedit.camera.engine.renderer.WatermarkCanvasRenderer
import com.timeedit.camera.engine.renderer.WatermarkRenderMetrics
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DragTarget {
    MAIN,
    BRAND,
    LOGO,
}

@Composable
fun WatermarkOverlay(
    template: WatermarkTemplate,
    epochMillis: Long,
    containerSize: IntSize,
    weatherCode: Int? = null,
    uiRotationDegrees: Int = 0,
    language: AppLanguage = AppLanguage.VI,
    onPositionCommitted: (WatermarkPosition) -> Unit,
    onBrandPositionCommitted: (WatermarkPosition) -> Unit = {},
    onLogoPositionCommitted: (WatermarkPosition) -> Unit = {},
    onResetPositions: () -> Unit = {},
    onTapTime: () -> Unit,
    onTapWatermark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current

    var logoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(template.logoUri) {
        logoBitmap = withContext(Dispatchers.IO) {
            WatermarkCanvasRenderer.decodeLogoBitmap(context, template.logoUri)
        }
    }

    var customBrandBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(template.customBrandImageUri, template.brandBadgePreset) {
        customBrandBitmap = if (template.brandBadgePreset == BrandBadgePreset.CUSTOM_IMAGE) {
            withContext(Dispatchers.IO) {
                BrandAssetPackLoader.decodeCustomBrandBitmap(context, template.customBrandImageUri)
            }
        } else null
    }

    val orientationAlpha = remember { Animatable(1f) }
    var lastAnimatedRot by remember { mutableIntStateOf(uiRotationDegrees) }
    LaunchedEffect(uiRotationDegrees) {
        if (lastAnimatedRot != uiRotationDegrees) {
            lastAnimatedRot = uiRotationDegrees
            orientationAlpha.snapTo(0.25f)
            orientationAlpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }
    }

    var activeDragTarget by remember { mutableStateOf<DragTarget?>(null) }
    var virtualDragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragStartVirtualBounds by remember { mutableStateOf<RectF?>(null) }

    var virtualMainBounds by remember { mutableStateOf(RectF(16f, 400f, 320f, 620f)) }
    var screenHitRect by remember { mutableStateOf(IntRect(16, 400, 320, 620)) }
    var screenTimeRect by remember { mutableStateOf(IntRect(16, 400, 260, 500)) }

    var virtualBrandBounds by remember { mutableStateOf<RectF?>(null) }
    var screenBrandRect by remember { mutableStateOf<IntRect?>(null) }

    var virtualLogoBounds by remember { mutableStateOf<RectF?>(null) }
    var screenLogoRect by remember { mutableStateOf<IntRect?>(null) }

    val vfW = containerSize.width.toFloat().coerceAtLeast(1f)
    val vfH = containerSize.height.toFloat().coerceAtLeast(1f)
    val normalizedRot = ((uiRotationDegrees % 360) + 360) % 360
    val swapDims = normalizedRot == 90 || normalizedRot == 270
    val virtW = if (swapDims) vfH else vfW
    val virtH = if (swapDims) vfW else vfH

    val effectiveTemplate = remember(
        template,
        activeDragTarget,
        virtualDragDelta,
        dragStartVirtualBounds,
        virtW,
        virtH,
    ) {
        val target = activeDragTarget ?: return@remember template
        val base = dragStartVirtualBounds
        val fallbackPos = when (target) {
            DragTarget.MAIN -> template.position
            DragTarget.BRAND -> template.brandPosition ?: WatermarkPosition(0.8f, 0.9f, WatermarkCorner.BOTTOM_RIGHT)
            DragTarget.LOGO -> template.logoPosition ?: WatermarkPosition(0.025f, 0.025f, WatermarkCorner.TOP_LEFT)
        }
        val rawX = if (base != null) {
            (base.left + virtualDragDelta.x) / virtW
        } else {
            fallbackPos.xFraction + virtualDragDelta.x / virtW
        }
        val rawY = if (base != null) {
            (base.top + virtualDragDelta.y) / virtH
        } else {
            fallbackPos.yFraction + virtualDragDelta.y / virtH
        }
        val boxWFrac = ((base?.width() ?: 0f) / virtW).coerceIn(0.04f, 0.95f)
        val boxHFrac = ((base?.height() ?: 0f) / virtH).coerceIn(0.04f, 0.90f)
        val draggedPos = clampFreePosition(
            rawXFraction = if (target == DragTarget.MAIN && template.style.isFullWidth) 0.022f else rawX,
            rawYFraction = rawY,
            boxWidthFraction = boxWFrac,
            boxHeightFraction = boxHFrac,
        )
        when (target) {
            DragTarget.MAIN -> template.copy(position = draggedPos)
            DragTarget.BRAND -> template.copy(brandPosition = draggedPos)
            DragTarget.LOGO -> template.copy(logoPosition = draggedPos)
        }
    }

    val latestTemplate by rememberUpdatedState(template)
    val latestDragStart by rememberUpdatedState(dragStartVirtualBounds)
    val latestVirtualMainBounds by rememberUpdatedState(virtualMainBounds)
    val latestVirtualBrandBounds by rememberUpdatedState(virtualBrandBounds)
    val latestVirtualLogoBounds by rememberUpdatedState(virtualLogoBounds)
    val latestScreenHitRect by rememberUpdatedState(screenHitRect)
    val latestScreenTimeRect by rememberUpdatedState(screenTimeRect)
    val latestUiRot by rememberUpdatedState(normalizedRot)
    val latestVirtW by rememberUpdatedState(virtW)
    val latestVirtH by rememberUpdatedState(virtH)
    val latestOnPositionCommitted by rememberUpdatedState(onPositionCommitted)
    val latestOnBrandCommitted by rememberUpdatedState(onBrandPositionCommitted)
    val latestOnLogoCommitted by rememberUpdatedState(onLogoPositionCommitted)
    val latestOnTapTime by rememberUpdatedState(onTapTime)
    val latestOnTapWatermark by rememberUpdatedState(onTapWatermark)

    val guideAccent = colors.accent
    val guideColor = guideAccent.copy(alpha = 0.78f)
    val guideFrameColor = guideAccent.copy(alpha = 0.85f)
    val guideLenPx = with(density) { 18.dp.toPx() }
    val guideStrokePx = with(density) { 2.dp.toPx() }
    val frameRadiusPx = with(density) { 8.dp.toPx() }
    val frameStrokePx = with(density) { 1.5.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Exact pixel-matched Canvas renderer rotated to match physical hold orientation
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = orientationAlpha.value },
        ) {
            val canvasMatrix = Matrix().apply {
                when (normalizedRot) {
                    90 -> {
                        postRotate(90f)
                        postTranslate(size.width, 0f)
                    }
                    180 -> {
                        postRotate(180f)
                        postTranslate(size.width, size.height)
                    }
                    270 -> {
                        postRotate(-90f)
                        postTranslate(0f, size.height)
                    }
                }
            }

            var renderedMetrics = WatermarkRenderMetrics(RectF(), RectF())
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.concat(canvasMatrix)

                renderedMetrics = WatermarkCanvasRenderer.drawWatermarkOnCanvas(
                    context = context,
                    canvas = native,
                    photoWidth = virtW.roundToInt(),
                    photoHeight = virtH.roundToInt(),
                    template = effectiveTemplate,
                    epochMillis = epochMillis,
                    logoBitmap = logoBitmap,
                    customBrandBitmap = customBrandBitmap,
                    weatherCode = weatherCode,
                    isPreview = true,
                    language = language,
                )

                native.restore()
            }

            // Draw drag guides in virtual space around whichever element is being moved
            val activeBounds = when (activeDragTarget) {
                DragTarget.MAIN -> renderedMetrics.mainBounds
                DragTarget.BRAND -> renderedMetrics.brandBounds
                DragTarget.LOGO -> renderedMetrics.logoBounds
                null -> null
            }
            if (activeBounds != null) {
                val pad = minOf(virtW, virtH) * 0.022f
                drawScopeRotated(
                    canvasMatrix = canvasMatrix,
                    virtW = virtW,
                    virtH = virtH,
                    pad = pad,
                    len = guideLenPx,
                    stroke = guideStrokePx,
                    guideColor = guideColor,
                    frameColor = guideFrameColor,
                    frameRadius = frameRadiusPx,
                    frameStroke = frameStrokePx,
                    elementBounds = activeBounds,
                )
            }

            if (activeDragTarget == null && renderedMetrics.mainBounds.width() > 4f && renderedMetrics.mainBounds.height() > 4f) {
                val mappedMain = RectF(renderedMetrics.mainBounds)
                val mappedTime = RectF(renderedMetrics.timeBounds)
                canvasMatrix.mapRect(mappedMain)
                canvasMatrix.mapRect(mappedTime)

                val nextScreenHit = mappedMain.toIntRect()
                val nextScreenTime = mappedTime.toIntRect()
                val nextVirtMain = RectF(renderedMetrics.mainBounds)

                val nextVirtBrand = renderedMetrics.brandBounds?.let { RectF(it) }
                val nextScreenBrand = renderedMetrics.brandBounds?.let { raw ->
                    val mapped = RectF(raw)
                    canvasMatrix.mapRect(mapped)
                    mapped.toIntRect()
                }

                val nextVirtLogo = renderedMetrics.logoBounds?.let { RectF(it) }
                val nextScreenLogo = renderedMetrics.logoBounds?.let { raw ->
                    val mapped = RectF(raw)
                    canvasMatrix.mapRect(mapped)
                    mapped.toIntRect()
                }

                if (nextScreenHit != screenHitRect ||
                    nextScreenTime != screenTimeRect ||
                    nextVirtMain != virtualMainBounds ||
                    nextScreenBrand != screenBrandRect ||
                    nextVirtBrand != virtualBrandBounds ||
                    nextScreenLogo != screenLogoRect ||
                    nextVirtLogo != virtualLogoBounds
                ) {
                    view.post {
                        virtualMainBounds = nextVirtMain
                        screenHitRect = nextScreenHit
                        screenTimeRect = nextScreenTime
                        virtualBrandBounds = nextVirtBrand
                        screenBrandRect = nextScreenBrand
                        virtualLogoBounds = nextVirtLogo
                        screenLogoRect = nextScreenLogo
                    }
                }
            }
        }

        // Layer 2: Draggable / tappable hit-target for Main Watermark block
        val hitWdp = with(density) { screenHitRect.width.coerceAtLeast(60).toDp() }
        val hitHdp = with(density) { screenHitRect.height.coerceAtLeast(40).toDp() }
        Box(
            modifier = Modifier
                .offset { IntOffset(screenHitRect.left, screenHitRect.top) }
                .size(width = hitWdp, height = hitHdp)
                .semantics { contentDescription = "Watermark stamp, tap to edit or drag to move" }
                .pointerInput(containerSize) {
                    detectTapGestures { localOffset ->
                        val absX = (latestScreenHitRect.left + localOffset.x).roundToInt()
                        val absY = (latestScreenHitRect.top + localOffset.y).roundToInt()
                        if (absX in latestScreenTimeRect.left..latestScreenTimeRect.right &&
                            absY in latestScreenTimeRect.top..latestScreenTimeRect.bottom
                        ) {
                            latestOnTapTime()
                        } else {
                            latestOnTapWatermark()
                        }
                    }
                }
                .pointerInput(containerSize) {
                    detectDragGestures(
                        onDragStart = {
                            dragStartVirtualBounds = RectF(latestVirtualMainBounds)
                            activeDragTarget = DragTarget.MAIN
                            virtualDragDelta = Offset.Zero
                        },
                        onDragCancel = {
                            activeDragTarget = null
                            virtualDragDelta = Offset.Zero
                            dragStartVirtualBounds = null
                        },
                        onDragEnd = {
                            val vw = latestVirtW
                            val vh = latestVirtH
                            val base = latestDragStart
                            val rawX = if (latestTemplate.style.isFullWidth) {
                                0.022f
                            } else if (base != null) {
                                (base.left + virtualDragDelta.x) / vw
                            } else {
                                latestTemplate.position.xFraction + virtualDragDelta.x / vw
                            }
                            val rawY = if (base != null) {
                                (base.top + virtualDragDelta.y) / vh
                            } else {
                                latestTemplate.position.yFraction + virtualDragDelta.y / vh
                            }
                            val boxWFrac = (latestVirtualMainBounds.width() / vw).coerceIn(0.05f, 0.96f)
                            val boxHFrac = (latestVirtualMainBounds.height() / vh).coerceIn(0.05f, 0.90f)
                            val freePos = clampFreePosition(
                                rawXFraction = rawX,
                                rawYFraction = rawY,
                                boxWidthFraction = boxWFrac,
                                boxHeightFraction = boxHFrac,
                            )
                            activeDragTarget = null
                            virtualDragDelta = Offset.Zero
                            dragStartVirtualBounds = null
                            latestOnPositionCommitted(freePos)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val (vx, vy) = OrientationMath.mapScreenDeltaToVirtual(
                            dx = dragAmount.x,
                            dy = dragAmount.y,
                            uiRotationDegrees = latestUiRot,
                        )
                        virtualDragDelta += Offset(vx, vy)
                    }
                },
        )

        // Layer 3: Draggable / tappable hit-target for Brand Badge (e.g. Timemark 100% Chân thực)
        screenBrandRect?.let { rawRect ->
            val minW = with(density) { 56.dp.roundToPx() }
            val minH = with(density) { 40.dp.roundToPx() }
            val targetRect = expandHitRect(rawRect, minW, minH, containerSize)
            Box(
                modifier = Modifier
                    .offset { IntOffset(targetRect.left, targetRect.top) }
                    .size(
                        width = with(density) { targetRect.width.toDp() },
                        height = with(density) { targetRect.height.toDp() },
                    )
                    .pointerInput(containerSize) {
                        detectTapGestures { latestOnTapWatermark() }
                    }
                    .pointerInput(containerSize) {
                        detectDragGestures(
                            onDragStart = {
                                dragStartVirtualBounds = latestVirtualBrandBounds?.let { RectF(it) }
                                activeDragTarget = DragTarget.BRAND
                                virtualDragDelta = Offset.Zero
                            },
                            onDragCancel = {
                                activeDragTarget = null
                                virtualDragDelta = Offset.Zero
                                dragStartVirtualBounds = null
                            },
                            onDragEnd = {
                                val vw = latestVirtW
                                val vh = latestVirtH
                                val base = latestDragStart
                                if (base != null) {
                                    val freePos = clampFreePosition(
                                        rawXFraction = (base.left + virtualDragDelta.x) / vw,
                                        rawYFraction = (base.top + virtualDragDelta.y) / vh,
                                        boxWidthFraction = (base.width() / vw).coerceIn(0.03f, 0.90f),
                                        boxHeightFraction = (base.height() / vh).coerceIn(0.03f, 0.90f),
                                        margin = 0.015f,
                                    )
                                    latestOnBrandCommitted(freePos)
                                }
                                activeDragTarget = null
                                virtualDragDelta = Offset.Zero
                                dragStartVirtualBounds = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val (vx, vy) = OrientationMath.mapScreenDeltaToVirtual(
                                dx = dragAmount.x,
                                dy = dragAmount.y,
                                uiRotationDegrees = latestUiRot,
                            )
                            virtualDragDelta += Offset(vx, vy)
                        }
                    },
            )
        }

        // Layer 4: Draggable / tappable hit-target for User Logo
        screenLogoRect?.let { rawRect ->
            val minSize = with(density) { 48.dp.roundToPx() }
            val targetRect = expandHitRect(rawRect, minSize, minSize, containerSize)
            Box(
                modifier = Modifier
                    .offset { IntOffset(targetRect.left, targetRect.top) }
                    .size(
                        width = with(density) { targetRect.width.toDp() },
                        height = with(density) { targetRect.height.toDp() },
                    )
                    .pointerInput(containerSize) {
                        detectTapGestures { latestOnTapWatermark() }
                    }
                    .pointerInput(containerSize) {
                        detectDragGestures(
                            onDragStart = {
                                dragStartVirtualBounds = latestVirtualLogoBounds?.let { RectF(it) }
                                activeDragTarget = DragTarget.LOGO
                                virtualDragDelta = Offset.Zero
                            },
                            onDragCancel = {
                                activeDragTarget = null
                                virtualDragDelta = Offset.Zero
                                dragStartVirtualBounds = null
                            },
                            onDragEnd = {
                                val vw = latestVirtW
                                val vh = latestVirtH
                                val base = latestDragStart
                                if (base != null) {
                                    val freePos = clampFreePosition(
                                        rawXFraction = (base.left + virtualDragDelta.x) / vw,
                                        rawYFraction = (base.top + virtualDragDelta.y) / vh,
                                        boxWidthFraction = (base.width() / vw).coerceIn(0.04f, 0.90f),
                                        boxHeightFraction = (base.height() / vh).coerceIn(0.04f, 0.90f),
                                    )
                                    latestOnLogoCommitted(freePos)
                                }
                                activeDragTarget = null
                                virtualDragDelta = Offset.Zero
                                dragStartVirtualBounds = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val (vx, vy) = OrientationMath.mapScreenDeltaToVirtual(
                                dx = dragAmount.x,
                                dy = dragAmount.y,
                                uiRotationDegrees = latestUiRot,
                            )
                            virtualDragDelta += Offset(vx, vy)
                        }
                    },
            )
        }

        // Layer 5: Floating rotation-aware "Reset" popup positioned along the physical top edge
        val edge = OrientationMath.topEdgeForUiRotation(normalizedRot)
        val resetAlignment = when (edge) {
            FrameEdge.TOP -> Alignment.TopCenter
            FrameEdge.RIGHT -> Alignment.CenterEnd
            FrameEdge.BOTTOM -> Alignment.BottomCenter
            FrameEdge.LEFT -> Alignment.CenterStart
        }
        val quarterTurn = normalizedRot == 90 || normalizedRot == 270
        AnimatedVisibility(
            visible = template.hasCustomElementPositions && activeDragTarget == null,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
            modifier = Modifier
                .align(resetAlignment)
                .padding(12.dp),
        ) {
            Layout(
                content = {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        opaqueModal = true,
                        darkColor = colors.glassDialogFill.copy(alpha = 0.92f),
                        darkBorder = BorderStroke(1.dp, colors.accent.copy(alpha = 0.65f)),
                        onClick = onResetPositions,
                        role = Role.Button,
                        modifier = Modifier.semantics {
                            contentDescription = strings.cdResetWatermarkPositions
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = strings.btnResetPositions,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val placeable = measurables.first().measure(loose)
                val outW = if (quarterTurn) placeable.height else placeable.width
                val outH = if (quarterTurn) placeable.width else placeable.height
                layout(outW, outH) {
                    placeable.placeRelativeWithLayer(
                        x = (outW - placeable.width) / 2,
                        y = (outH - placeable.height) / 2,
                    ) {
                        rotationZ = normalizedRot.toFloat()
                    }
                }
            }
        }
    }
}

private fun expandHitRect(
    raw: IntRect,
    minWidthPx: Int,
    minHeightPx: Int,
    containerSize: IntSize,
): IntRect {
    val w = maxOf(raw.width, minWidthPx).coerceAtMost(containerSize.width.coerceAtLeast(1))
    val h = maxOf(raw.height, minHeightPx).coerceAtMost(containerSize.height.coerceAtLeast(1))
    val cx = (raw.left + raw.right) / 2
    val cy = (raw.top + raw.bottom) / 2
    val maxLeft = (containerSize.width - w).coerceAtLeast(0)
    val maxTop = (containerSize.height - h).coerceAtLeast(0)
    val left = (cx - w / 2).coerceIn(0, maxLeft)
    val top = (cy - h / 2).coerceIn(0, maxTop)
    return IntRect(left, top, left + w, top + h)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScopeRotated(
    canvasMatrix: Matrix,
    virtW: Float,
    virtH: Float,
    pad: Float,
    len: Float,
    stroke: Float,
    guideColor: Color,
    frameColor: Color,
    frameRadius: Float,
    frameStroke: Float,
    elementBounds: RectF,
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.concat(canvasMatrix)
    }
    drawLine(guideColor, Offset(pad, pad), Offset(pad + len, pad), stroke)
    drawLine(guideColor, Offset(pad, pad), Offset(pad, pad + len), stroke)
    drawLine(guideColor, Offset(virtW - pad, pad), Offset(virtW - pad - len, pad), stroke)
    drawLine(guideColor, Offset(virtW - pad, pad), Offset(virtW - pad, pad + len), stroke)
    drawLine(guideColor, Offset(pad, virtH - pad), Offset(pad + len, virtH - pad), stroke)
    drawLine(guideColor, Offset(pad, virtH - pad), Offset(pad, virtH - pad - len), stroke)
    drawLine(guideColor, Offset(virtW - pad, virtH - pad), Offset(virtW - pad - len, virtH - pad), stroke)
    drawLine(guideColor, Offset(virtW - pad, virtH - pad), Offset(virtW - pad, virtH - pad - len), stroke)

    drawRoundRect(
        color = frameColor,
        topLeft = Offset(elementBounds.left - 4f, elementBounds.top - 4f),
        size = Size(elementBounds.width() + 8f, elementBounds.height() + 8f),
        cornerRadius = CornerRadius(frameRadius, frameRadius),
        style = Stroke(
            width = frameStroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
        ),
    )
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.restore()
    }
}

private fun RectF.toIntRect(): IntRect = IntRect(
    left = left.roundToInt(),
    top = top.roundToInt(),
    right = right.roundToInt(),
    bottom = bottom.roundToInt(),
)

fun parseComposeColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
