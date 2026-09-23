package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.content.SharedPreferences

class AtlasPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "atlas_media_api_settings"
        private const val KEY_DEFAULT_MEDIA_PACKAGE = "default_media_package"
        private const val KEY_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART =
            "minimize_online_player_after_autostart"
        private const val KEY_DIAGNOSTIC_LOGGING_ENABLED = "diagnostic_logging_enabled"
        private const val KEY_DEFAULT_AUDIO_SOURCE = "default_audio_source"
        private const val KEY_DEFAULT_AUDIO_SOURCE_DELAY_SEC = "default_audio_source_delay_sec"
        private const val KEY_DEFAULT_AUDIO_SOURCE_AUTOPLAY_ON_STARTUP = "default_audio_source_autoplay_on_startup"
        private const val KEY_AUTO_SWITCH_TO_DEFAULT_ON_SOURCE_LOST = "auto_switch_to_default_on_source_lost"
        private const val KEY_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY_ON_SOURCE_LOST = "auto_switch_to_default_autoplay_on_source_lost"
        private const val KEY_DEMO_MODE_ENABLED = "demo_mode_enabled"
        const val KEY_UI_SCALE_TENTHS = "ui_scale_tenths"
        const val EXTRA_UI_SCALE_TENTHS = "app_ui_scale_tenths"
        const val MIN_UI_SCALE_TENTHS = 10
        const val MAX_UI_SCALE_TENTHS = 20
        const val DEFAULT_UI_SCALE_TENTHS = 15
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultMediaPackage: String
        get() = prefs.getString(KEY_DEFAULT_MEDIA_PACKAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEFAULT_MEDIA_PACKAGE, value.trim()).apply()

    var minimizeOnlinePlayerAfterAutostart: Boolean
        get() = prefs.getBoolean(KEY_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART, false)
        set(value) = prefs.edit()
            .putBoolean(KEY_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART, value)
            .apply()

    var diagnosticLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTIC_LOGGING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DIAGNOSTIC_LOGGING_ENABLED, value).apply()

    var defaultAudioSource: String
        get() = prefs.getString(KEY_DEFAULT_AUDIO_SOURCE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEFAULT_AUDIO_SOURCE, value.trim()).apply()

    var defaultAudioSourceDelaySec: Int
        get() = prefs.getInt(KEY_DEFAULT_AUDIO_SOURCE_DELAY_SEC, 0).coerceIn(0, 30)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_AUDIO_SOURCE_DELAY_SEC, value.coerceIn(0, 30)).apply()

    var defaultAudioSourceAutoplayOnStartup: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_AUDIO_SOURCE_AUTOPLAY_ON_STARTUP, true)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_AUDIO_SOURCE_AUTOPLAY_ON_STARTUP, value).apply()

    var autoSwitchToDefaultOnSourceLost: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SWITCH_TO_DEFAULT_ON_SOURCE_LOST, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SWITCH_TO_DEFAULT_ON_SOURCE_LOST, value).apply()

    var autoSwitchToDefaultAutoplayOnSourceLost: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY_ON_SOURCE_LOST, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY_ON_SOURCE_LOST, value).apply()

    /** Uses synthetic media data and never connects to vendor OneOS services. */
    var demoModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_MODE_ENABLED, value).apply()

    var uiScaleTenths: Int
        get() = prefs.getInt(KEY_UI_SCALE_TENTHS, DEFAULT_UI_SCALE_TENTHS)
            .coerceIn(MIN_UI_SCALE_TENTHS, MAX_UI_SCALE_TENTHS)
        set(value) = prefs.edit()
            .putInt(KEY_UI_SCALE_TENTHS, value.coerceIn(MIN_UI_SCALE_TENTHS, MAX_UI_SCALE_TENTHS))
            .apply()
}
