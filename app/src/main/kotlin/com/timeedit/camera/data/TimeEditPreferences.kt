/*
 * Time Edit — Open-Source Camera & Timestamp Watermark App
 * Copyright (C) 2026 Time Edit Contributors
 * Licensed under GNU GPL-3.0-or-later. See LICENSE in project root.
 */

package com.timeedit.camera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timeedit.camera.core.i18n.AppLanguage
import com.timeedit.camera.core.time.TimeConfig
import com.timeedit.camera.domain.watermark.DefaultWatermarkTemplates
import com.timeedit.camera.domain.watermark.WatermarkJsonCodec
import com.timeedit.camera.domain.watermark.WatermarkTemplate
import com.timeedit.camera.engine.camera.PhotoQuality
import com.timeedit.camera.engine.camera.VideoQualityPreset
import com.timeedit.camera.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "time_edit_prefs")

data class AppPreferences(
    val appLanguage: AppLanguage = AppLanguage.VI,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val timeConfig: TimeConfig = TimeConfig(),
    val activeTemplate: WatermarkTemplate = DefaultWatermarkTemplates.timeLocation(),
    val customPresets: List<WatermarkTemplate> = emptyList(),
    val saveOriginalPhoto: Boolean = false,
    val mirrorFrontCamera: Boolean = true,
    val showGrid: Boolean = false,
    val photoQuality: PhotoQuality = PhotoQuality.HD,
    val videoQuality: VideoQualityPreset = VideoQualityPreset.HD_30,
)

class TimeEditPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language_code")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val TIME_CONFIG = stringPreferencesKey("time_config_json")
        val ACTIVE_TEMPLATE = stringPreferencesKey("active_template_json")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original_photo")
        val MIRROR_FRONT = booleanPreferencesKey("mirror_front_camera")
        val SHOW_GRID = booleanPreferencesKey("show_grid")
        val PHOTO_QUALITY = stringPreferencesKey("photo_quality_code")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality_code")
        val DEFAULTS_VERSION = intPreferencesKey("defaults_schema_version")
    }

    private companion object {
        const val CURRENT_DEFAULTS_VERSION = 6
    }

    val preferencesFlow: Flow<AppPreferences> = appContext.dataStore.data.map { prefs ->
        val appLanguage = AppLanguage.fromCode(prefs[Keys.APP_LANGUAGE])
        val themeMode = AppThemeMode.fromCode(prefs[Keys.THEME_MODE])
        val timeConfig = prefs[Keys.TIME_CONFIG]?.let { raw ->
            runCatching { json.decodeFromString(TimeConfig.serializer(), raw) }.getOrNull()
        } ?: TimeConfig()

        val schemaVersion = prefs[Keys.DEFAULTS_VERSION] ?: 1
        val activeTemplate = if (schemaVersion < CURRENT_DEFAULTS_VERSION) {
            DefaultWatermarkTemplates.timeLocation(appLanguage)
        } else {
            prefs[Keys.ACTIVE_TEMPLATE]?.let { raw ->
                WatermarkJsonCodec.decodeTemplate(raw).getOrNull()
            } ?: DefaultWatermarkTemplates.timeLocation(appLanguage)
        }

        val customPresets = prefs[Keys.CUSTOM_PRESETS]?.let { raw ->
            WatermarkJsonCodec.decodePresetList(raw).getOrNull()
        } ?: emptyList()

        AppPreferences(
            appLanguage = appLanguage,
            themeMode = themeMode,
            timeConfig = timeConfig,
            activeTemplate = activeTemplate,
            customPresets = customPresets,
            saveOriginalPhoto = prefs[Keys.SAVE_ORIGINAL] ?: false,
            mirrorFrontCamera = prefs[Keys.MIRROR_FRONT] ?: true,
            showGrid = prefs[Keys.SHOW_GRID] ?: false,
            photoQuality = PhotoQuality.fromCode(prefs[Keys.PHOTO_QUALITY]),
            videoQuality = VideoQualityPreset.fromCode(prefs[Keys.VIDEO_QUALITY]),
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.code
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.code
        }
    }

    suspend fun saveTimeConfig(config: TimeConfig) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.TIME_CONFIG] = json.encodeToString(TimeConfig.serializer(), config)
        }
    }

    suspend fun saveActiveTemplate(template: WatermarkTemplate) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_TEMPLATE] = WatermarkJsonCodec.encodeTemplate(template)
            prefs[Keys.DEFAULTS_VERSION] = CURRENT_DEFAULTS_VERSION
        }
    }

    suspend fun saveCustomPresets(presets: List<WatermarkTemplate>) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PRESETS] = WatermarkJsonCodec.encodePresetList(presets)
        }
    }

    suspend fun setSaveOriginalPhoto(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SAVE_ORIGINAL] = enabled
        }
    }

    suspend fun setMirrorFrontCamera(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.MIRROR_FRONT] = enabled
        }
    }

    suspend fun setShowGrid(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SHOW_GRID] = enabled
        }
    }

    suspend fun setPhotoQuality(quality: PhotoQuality) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.PHOTO_QUALITY] = quality.code
        }
    }

    suspend fun setVideoQuality(preset: VideoQualityPreset) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.VIDEO_QUALITY] = preset.code
        }
    }
}
