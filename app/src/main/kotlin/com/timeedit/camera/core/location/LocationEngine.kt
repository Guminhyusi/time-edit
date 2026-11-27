/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import com.timeedit.camera.core.i18n.AppLanguage
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class LocationFixState {
    IDLE,
    PERMISSION_NEEDED,
    SEARCHING,
    LOCKED,
    MANUAL_PIN,
    UNAVAILABLE,
}

data class LiveLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val address: FormattedLocationAddress,
    val timestampMillis: Long,
)

data class PlaceSearchResult(
    val title: String,
    val subtitle: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
)

class LocationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var activeListener: LocationListenerCompat? = null
    private var bestFix: Location? = null
    private var lastGeocodeLat: Double? = null
    private var lastGeocodeLng: Double? = null
    private var lastGeocodeMs: Long = 0L
    private var lastAddress = FormattedLocationAddress("", "", "")

    @Volatile
    private var language: AppLanguage = AppLanguage.VI

    fun setLanguage(lang: AppLanguage) {
        if (language != lang) {
            language = lang
            lastGeocodeMs = 0L
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationServiceEnabled(): Boolean {
        val lm = locationManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun startUpdates(
        scope: CoroutineScope,
        onStateChanged: (LocationFixState) -> Unit,
        onLocationUpdate: (LiveLocationSnapshot) -> Unit,
    ) {
        if (!hasLocationPermission()) {
            onStateChanged(LocationFixState.PERMISSION_NEEDED)
            return
        }
        val lm = locationManager
        if (lm == null || !isLocationServiceEnabled()) {
            onStateChanged(LocationFixState.UNAVAILABLE)
            return
        }

        stopUpdates()
        onStateChanged(LocationFixState.SEARCHING)

        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        // Seed immediately from best last known location
        var initialBest: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (isBetterLocation(loc, initialBest)) {
                initialBest = loc
            }
        }
        if (initialBest != null) {
            bestFix = initialBest
            handleNewLocation(initialBest, scope, onStateChanged, onLocationUpdate)
        }

        val listener = LocationListenerCompat { location ->
            if (isBetterLocation(location, bestFix)) {
                bestFix = location
                handleNewLocation(location, scope, onStateChanged, onLocationUpdate)
            }
        }
        activeListener = listener

        var registeredAny = false
        for (p in providers) {
            val enabled = runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
            if (enabled) {
                runCatching {
                    // Request GPS/Network updates at 5s interval or 10m displacement
                    lm.requestLocationUpdates(p, 5000L, 10f, listener, Looper.getMainLooper())
                    registeredAny = true
                }
            }
        }
        if (!registeredAny && initialBest == null) {
            onStateChanged(LocationFixState.UNAVAILABLE)
        }
    }

    fun stopUpdates() {
        val lm = locationManager ?: return
        val listener = activeListener ?: return
        runCatching { lm.removeUpdates(listener) }
        activeListener = null
    }

    private fun handleNewLocation(
        loc: Location,
        scope: CoroutineScope,
        onStateChanged: (LocationFixState) -> Unit,
        onLocationUpdate: (LiveLocationSnapshot) -> Unit,
    ) {
        onStateChanged(LocationFixState.LOCKED)
        val alt = if (loc.hasAltitude()) loc.altitude else null
        val acc = if (loc.hasAccuracy()) loc.accuracy else null

        val movedMeters = if (lastGeocodeLat != null && lastGeocodeLng != null) {
            TimemarkLocationFormatter.haversineMeters(lastGeocodeLat!!, lastGeocodeLng!!, loc.latitude, loc.longitude)
        } else Double.MAX_VALUE
        val elapsedMs = System.currentTimeMillis() - lastGeocodeMs
        val shouldRegeocode = lastAddress.fullAddress.isBlank() || movedMeters >= 15.0 || elapsedMs > 30_000L

        onLocationUpdate(
            LiveLocationSnapshot(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitudeMeters = alt,
                accuracyMeters = acc,
                address = lastAddress,
                timestampMillis = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )

        if (shouldRegeocode) {
            lastGeocodeLat = loc.latitude
            lastGeocodeLng = loc.longitude
            lastGeocodeMs = System.currentTimeMillis()
            scope.launch {
                val formatted = reverseGeocode(loc.latitude, loc.longitude)
                if (formatted.fullAddress.isNotBlank()) {
                    lastAddress = formatted
                    onLocationUpdate(
                        LiveLocationSnapshot(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            altitudeMeters = alt,
                            accuracyMeters = acc,
                            address = formatted,
                            timestampMillis = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    private data class GeocodeCandidate(
        val address: FormattedLocationAddress,
        val hasStreet: Boolean,
    )

    suspend fun reverseGeocode(lat: Double, lng: Double): FormattedLocationAddress {
        val fromSystem = querySystemGeocoder(lat, lng)
        if (fromSystem != null && !TimemarkLocationFormatter.isCoarseAddress(fromSystem.address, fromSystem.hasStreet)) {
            return fromSystem.address
        }
        val fromNominatim = queryNominatimReverse(lat, lng)
        return TimemarkLocationFormatter.selectRicherAddress(
            primary = fromSystem?.address,
            primaryHasStreet = fromSystem?.hasStreet == true,
            fallback = fromNominatim?.address,
            fallbackHasStreet = fromNominatim?.hasStreet == true,
        )
    }

    suspend fun searchPlaces(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null,
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val nominatimResults = queryNominatimSearch(trimmed, nearLat, nearLng)
        if (nominatimResults.isNotEmpty()) return@withContext nominatimResults

        if (!Geocoder.isPresent()) return@withContext emptyList()
        val addresses = querySystemSearch(trimmed)
        addresses.mapNotNull { addr ->
            val formatted = formatFromAndroidAddress(addr)
            if (formatted.fullAddress.isBlank()) return@mapNotNull null
            PlaceSearchResult(
                title = formatted.smallPosition.ifBlank { formatted.fullAddress },
                subtitle = formatted.largePosition,
                fullAddress = formatted.fullAddress,
                latitude = addr.latitude,
                longitude = addr.longitude,
            )
        }
    }

    private suspend fun querySystemGeocoder(lat: Double, lng: Double): GeocodeCandidate? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, language.toLocale())
        val addresses: List<Address> = withTimeoutOrNull(5000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        lat,
                        lng,
                        5,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<Address>) {
                                if (cont.isActive) cont.resume(results)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lng, 5).orEmpty() }.getOrDefault(emptyList())
                }
            }
        } ?: emptyList()

        var best: GeocodeCandidate? = null
        for (addr in addresses) {
            val formatted = formatFromAndroidAddress(addr)
            if (formatted.fullAddress.isBlank()) continue
            val candidate = GeocodeCandidate(
                address = formatted,
                hasStreet = hasSystemStreetDetail(addr),
            )
            if (best == null) {
                best = candidate
            } else {
                val chosen = TimemarkLocationFormatter.selectRicherAddress(
                    primary = best.address,
                    primaryHasStreet = best.hasStreet,
                    fallback = candidate.address,
                    fallbackHasStreet = candidate.hasStreet,
                )
                if (chosen == candidate.address) {
                    best = candidate
                }
            }
        }
        return best
    }

    private fun hasSystemStreetDetail(addr: Address): Boolean {
        if (!addr.thoroughfare.isNullOrBlank() || !addr.subThoroughfare.isNullOrBlank() || !addr.premises.isNullOrBlank()) {
            return true
        }
        val feat = addr.featureName?.trim().orEmpty()
        if (feat.isEmpty() || feat.contains("+")) return false
        return !feat.equals(addr.subLocality, ignoreCase = true) &&
            !feat.equals(addr.locality, ignoreCase = true) &&
            !feat.equals(addr.subAdminArea, ignoreCase = true) &&
            !feat.equals(addr.adminArea, ignoreCase = true) &&
            !feat.equals(addr.countryName, ignoreCase = true)
    }

    private suspend fun querySystemSearch(query: String): List<Address> {
        val geocoder = Geocoder(appContext, language.toLocale())
        return withTimeoutOrNull(5000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        query,
                        6,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<Address>) {
                                if (cont.isActive) cont.resume(results)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocationName(query, 6).orEmpty() }.getOrDefault(emptyList())
                }
            }
        } ?: emptyList()
    }

    private fun formatFromAndroidAddress(addr: Address): FormattedLocationAddress {
        val line0 = if (addr.maxAddressLineIndex >= 0) addr.getAddressLine(0) else null
        return TimemarkLocationFormatter.formatTimemarkAddress(
            rawAddressLine = line0,
            featureName = addr.featureName,
            subThoroughfare = addr.subThoroughfare,
            thoroughfare = addr.thoroughfare,
            subLocality = addr.subLocality,
            locality = addr.locality,
            adminArea = addr.adminArea,
            countryName = addr.countryName,
            countryCode = addr.countryCode,
        )
    }

    private suspend fun queryNominatimReverse(lat: Double, lng: Double): GeocodeCandidate? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&zoom=18&accept-language=${language.nominatimLang}&addressdetails=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "TimeEditCamera/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val obj = JSONObject(body)
                parseNominatimCandidate(obj)
            }.getOrNull()
        }

    private fun queryNominatimSearch(
        query: String,
        nearLat: Double?,
        nearLng: Double?,
    ): List<PlaceSearchResult> =
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val viewboxParam = if (nearLat != null && nearLng != null) {
                "&viewbox=${nearLng - 0.25},${nearLat + 0.25},${nearLng + 0.25},${nearLat - 0.25}&bounded=0"
            } else ""
            val url = URL("https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=8&accept-language=${language.nominatimLang}&addressdetails=1$viewboxParam")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TimeEditCamera/1.0")
                connectTimeout = 6000
                readTimeout = 6000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(body)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                    val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                    val fmt = parseNominatimCandidate(obj).address
                    val title = obj.optString("name").ifBlank { fmt.smallPosition.ifBlank { fmt.fullAddress } }
                    add(
                        PlaceSearchResult(
                            title = title,
                            subtitle = fmt.largePosition.ifBlank { fmt.fullAddress },
                            fullAddress = fmt.fullAddress.ifBlank { obj.optString("display_name") },
                            latitude = lat,
                            longitude = lon,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun parseNominatimCandidate(obj: JSONObject): GeocodeCandidate {
        val display = obj.optString("display_name")
        val addr = obj.optJSONObject("address")
        val poiName = obj.optString("name").takeIf { it.isNotBlank() }
        val houseNumber = addr?.optString("house_number")?.takeIf { it.isNotBlank() }
        val road = addr?.optString("road")?.takeIf { it.isNotBlank() }
        val subLocality = addr?.optString("suburb")
            ?.ifBlank { addr.optString("neighbourhood") }
            ?.ifBlank { addr.optString("quarter") }
            ?.ifBlank { addr.optString("hamlet") }
            ?.ifBlank { addr.optString("village") }
        val locality = addr?.optString("city_district")
            ?.ifBlank { addr.optString("town") }
            ?.ifBlank { addr.optString("county") }
        val adminArea = addr?.optString("city")?.ifBlank { addr.optString("state") }
        val countryName = addr?.optString("country")
        val countryCode = addr?.optString("country_code")?.uppercase()

        val formatted = TimemarkLocationFormatter.formatTimemarkAddress(
            rawAddressLine = display,
            featureName = poiName,
            subThoroughfare = houseNumber,
            thoroughfare = road,
            subLocality = subLocality,
            locality = locality,
            adminArea = adminArea,
            countryName = countryName,
            countryCode = countryCode,
        )
        val hasStreet = !road.isNullOrBlank() ||
            !houseNumber.isNullOrBlank() ||
            (!poiName.isNullOrBlank() &&
                !poiName.equals(subLocality, ignoreCase = true) &&
                !poiName.equals(locality, ignoreCase = true) &&
                !poiName.equals(adminArea, ignoreCase = true))
        return GeocodeCandidate(address = formatted, hasStreet = hasStreet)
    }

    private fun isBetterLocation(candidate: Location, currentBest: Location?): Boolean {
        if (currentBest == null) return true
        val timeDelta = candidate.time - currentBest.time
        val isSignificantlyNewer = timeDelta > 60_000L
        val isSignificantlyOlder = timeDelta < -60_000L
        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (candidate.accuracy - currentBest.accuracy).toInt()
        val isMoreAccurate = accuracyDelta < 0
        val isMuchLessAccurate = accuracyDelta > 100
        val sameProvider = candidate.provider == currentBest.provider
        return when {
            isMoreAccurate -> true
            timeDelta > 0 && accuracyDelta <= 0 -> true
            timeDelta > 0 && !isMuchLessAccurate && sameProvider -> true
            else -> false
        }
    }
}
