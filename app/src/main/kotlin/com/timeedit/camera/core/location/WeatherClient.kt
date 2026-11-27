/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.core.location

import com.timeedit.camera.core.i18n.AppLanguage
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WeatherSnapshot(
    val tempCelsius: Double,
    val weatherCode: Int,
    val fullText: String,
    val compactText: String,
    val fetchedAtMillis: Long,
) {
    fun formattedFullText(language: AppLanguage): String =
        TimemarkLocationFormatter.formatWeather(tempCelsius, weatherCode, includeCondition = true, language = language)
}

object WeatherClient {
    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, WeatherSnapshot>()

    suspend fun fetchWeather(lat: Double, lng: Double): WeatherSnapshot? {
        val key = "${(lat * 100).roundToInt()}_${(lng * 100).roundToInt()}"
        val cached = cache[key]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAtMillis < CACHE_TTL_MS) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code&timezone=auto",
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "TimeEditCamera/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val current = JSONObject(body).getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val snapshot = WeatherSnapshot(
                    tempCelsius = temp,
                    weatherCode = code,
                    fullText = TimemarkLocationFormatter.formatWeather(temp, code, includeCondition = true),
                    compactText = TimemarkLocationFormatter.formatWeather(temp, code, includeCondition = false),
                    fetchedAtMillis = now,
                )
                cache[key] = snapshot
                snapshot
            }.getOrNull() ?: cached
        }
    }
}
