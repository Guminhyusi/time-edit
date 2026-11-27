/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.timeedit.camera.core.i18n.LocalAppStrings
import com.timeedit.camera.core.location.FormattedLocationAddress
import com.timeedit.camera.core.location.PlaceSearchResult
import com.timeedit.camera.core.location.TimemarkLocationFormatter
import com.timeedit.camera.domain.watermark.CoordinateFormatStyle
import com.timeedit.camera.ui.theme.LiquidGlassSurface
import com.timeedit.camera.ui.theme.LocalTimeEditColors
import com.timeedit.camera.ui.theme.liquidGlassBackdrop
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

private const val DEFAULT_VN_LAT = 10.7769
private const val DEFAULT_VN_LNG = 106.7009

@Composable
fun LocationPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    initialSmallPosition: String,
    initialFullAddress: String,
    initialLargePosition: String,
    coordinateFormat: CoordinateFormatStyle,
    onCoordinateFormatChanged: (CoordinateFormatStyle) -> Unit,
    onSearchPlaces: suspend (String) -> List<PlaceSearchResult>,
    onReverseGeocode: suspend (Double, Double) -> FormattedLocationAddress,
    onResumeLiveGps: () -> Unit,
    onApplyPinnedLocation: (lat: Double, lng: Double, address: FormattedLocationAddress) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val colors = LocalTimeEditColors.current
    val scope = rememberCoroutineScope()

    var pinLat by remember { mutableDoubleStateOf(initialLat ?: DEFAULT_VN_LAT) }
    var pinLng by remember { mutableDoubleStateOf(initialLng ?: DEFAULT_VN_LNG) }
    var smallPosInput by remember { mutableStateOf(initialSmallPosition) }
    var fullAddrInput by remember { mutableStateOf(initialFullAddress) }
    var largePos by remember { mutableStateOf(initialLargePosition) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isGeocodingPin by remember { mutableStateOf(false) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        if (fullAddrInput.isBlank()) {
            isGeocodingPin = true
            val resolved = onReverseGeocode(pinLat, pinLng)
            if (resolved.fullAddress.isNotBlank()) {
                fullAddrInput = resolved.fullAddress
                smallPosInput = resolved.smallPosition
                largePos = resolved.largePosition
            }
            isGeocodingPin = false
        }
    }

    fun triggerPinReverseGeocode(lat: Double, lng: Double) {
        geocodeJob?.cancel()
        geocodeJob = scope.launch {
            delay(450L)
            isGeocodingPin = true
            val resolved = onReverseGeocode(lat, lng)
            if (resolved.fullAddress.isNotBlank()) {
                fullAddrInput = resolved.fullAddress
                smallPosInput = resolved.smallPosition
                largePos = resolved.largePosition
            }
            isGeocodingPin = false
        }
    }

    fun runSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        scope.launch {
            isSearching = true
            searchResults = onSearchPlaces(q)
            isSearching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            color = colors.surface,
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdrop(colors, darkColor = colors.surface)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header + Search Bar
                LiquidGlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    elevated = false,
                    darkColor = colors.surfaceElevated,
                    modifier = Modifier.zIndex(2f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = strings.locDialogTitle,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.cancel, tint = colors.textPrimary)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(strings.locSearchHint, fontSize = 13.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { runSearch() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                modifier = Modifier.height(52.dp),
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.onAccent, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Search, contentDescription = strings.locSearchHint)
                                }
                            }
                        }
                    }
                }

                // Search results overlay list when non-empty
                if (searchResults.isNotEmpty()) {
                    Surface(color = colors.glassDialogFill) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                        ) {
                            items(searchResults) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pinLat = item.latitude
                                            pinLng = item.longitude
                                            smallPosInput = item.title
                                            fullAddrInput = item.fullAddress
                                            largePos = item.subtitle
                                            searchResults = emptyList()
                                            mapViewRef?.controller?.animateTo(GeoPoint(item.latitude, item.longitude))
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(item.title, color = colors.accentSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.fullAddress, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = colors.accentContainer)
                            }
                        }
                    }
                }

                // Interactive OSM Map + Center Pin
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val baseDir = File(ctx.cacheDir, "osmdroid").apply { mkdirs() }
                            val tileDir = File(baseDir, "tiles").apply { mkdirs() }
                            Configuration.getInstance().apply {
                                load(
                                    ctx,
                                    ctx.getSharedPreferences("osmdroid_prefs", android.content.Context.MODE_PRIVATE),
                                )
                                userAgentValue = "TimeEditCamera/1.0 (${ctx.packageName})"
                                osmdroidBasePath = baseDir
                                osmdroidTileCache = tileDir
                            }
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                                setBackgroundColor(AndroidColor.parseColor("#F6F3EC"))
                                controller.setZoom(16.5)
                                controller.setCenter(GeoPoint(pinLat, pinLng))
                                addMapListener(
                                    object : MapListener {
                                        override fun onScroll(event: ScrollEvent?): Boolean {
                                            val center = mapCenter
                                            pinLat = center.latitude
                                            pinLng = center.longitude
                                            triggerPinReverseGeocode(center.latitude, center.longitude)
                                            return false
                                        }

                                        override fun onZoom(event: ZoomEvent?): Boolean = false
                                    },
                                )
                                onResume()
                                mapViewRef = this
                            }
                        },
                        onRelease = { view ->
                            view.onPause()
                            view.onDetach()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Fixed Center Pin
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Surface(
                            color = colors.accent,
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                    }

                    if (isGeocodingPin) {
                        LiquidGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            darkColor = colors.surfaceElevated.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
                                Text(strings.locResolvingAddress, color = colors.textPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Bottom Details + Actions
                LiquidGlassSurface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    opaqueModal = true,
                    darkColor = colors.surfaceElevated,
                    modifier = Modifier.zIndex(2f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = TimemarkLocationFormatter.formatCoordinates(pinLat, pinLng, coordinateFormat),
                            color = colors.accentSoft,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(CoordinateFormatStyle.entries, key = { it.name }) { fmt ->
                                FilterChip(
                                    selected = coordinateFormat == fmt,
                                    onClick = { onCoordinateFormatChanged(fmt) },
                                    label = { Text(fmt.label, fontSize = 11.sp) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = smallPosInput,
                            onValueChange = { smallPosInput = it },
                            label = { Text(strings.locShortLabelHint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = fullAddrInput,
                            onValueChange = { fullAddrInput = it },
                            label = { Text(strings.locFullAddressHint) },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onResumeLiveGps()
                                    onDismiss()
                                },
                                border = BorderStroke(1.dp, colors.borderStroke),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = colors.accentSoft, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.locUseLiveGps, color = colors.accentSoft, fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    val finalFull = fullAddrInput.trim().ifBlank {
                                        TimemarkLocationFormatter.formatCoordinates(pinLat, pinLng, coordinateFormat)
                                    }
                                    val finalSmall = smallPosInput.trim().ifBlank {
                                        finalFull.substringBefore(",").trim()
                                    }
                                    val finalLarge = largePos.trim().ifBlank {
                                        finalFull.substringAfter(",", "").trim()
                                    }
                                    onApplyPinnedLocation(
                                        pinLat,
                                        pinLng,
                                        FormattedLocationAddress(
                                            fullAddress = finalFull,
                                            smallPosition = finalSmall,
                                            largePosition = finalLarge,
                                        ),
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.locApplyLocation, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
