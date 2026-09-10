package com.mmwtl.atlasmediaapi.diagnostics

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.mmwtl.atlasmediaapi.settings.AtlasPreferences

/**
 * Утилита масштабирования контекста экрана настроек API (DiagnosticActivity).
 * Согласовывает плотность отображения с настройками AtlasMediaWidget.
 *
 * Helper for scaling the API diagnostics activity context (DiagnosticActivity).
 * Synchronizes display density with AtlasMediaWidget scale settings.
 */
object ScaledContextHelper {
    const val MIN_SCALE_TENTHS = AtlasPreferences.MIN_UI_SCALE_TENTHS
    const val MAX_SCALE_TENTHS = AtlasPreferences.MAX_UI_SCALE_TENTHS
    const val DEFAULT_SCALE_TENTHS = AtlasPreferences.DEFAULT_UI_SCALE_TENTHS

    private const val WIDGET_PREFS_NAME = "atlas_media_widget"
    private const val KEY_WIDGET_SCALE = "app_ui_scale_tenths"

    /**
     * Разрешает текущий масштаб в десятых долях (10..20, дефолт 15 = 1.5×).
     * Приоритеты:
     * 1. Настройки виджета (Device-protected или Credential storage)
     * 2. Настройки медиасервиса (AtlasPreferences)
     * 3. Значение по умолчанию (15 = 1.5×)
     *
     * Resolves the current scale in tenths (10..20, default 15 = 1.5×).
     * Priority:
     * 1. Widget preferences (Device-protected or Credential storage)
     * 2. Media API preferences (AtlasPreferences)
     * 3. Default scale (15 = 1.5×)
     */
    fun resolveScaleTenths(context: Context): Int {
        // 1. Проверяем настройки виджета (для integrated сборки и прямого доступа)
        try {
            val deviceContext = try {
                context.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {
                context
            }
            val widgetPrefs = deviceContext.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
            if (widgetPrefs.contains(KEY_WIDGET_SCALE)) {
                val scale = widgetPrefs.getInt(KEY_WIDGET_SCALE, DEFAULT_SCALE_TENTHS)
                if (scale in MIN_SCALE_TENTHS..MAX_SCALE_TENTHS) return scale
            }
            val credPrefs = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
            if (credPrefs.contains(KEY_WIDGET_SCALE)) {
                val scale = credPrefs.getInt(KEY_WIDGET_SCALE, DEFAULT_SCALE_TENTHS)
                if (scale in MIN_SCALE_TENTHS..MAX_SCALE_TENTHS) return scale
            }
        } catch (_: Throwable) {
            // Игнорируем ошибки доступа к SharedPreferences другого процесса/хранилища
        }

        // 2. Проверяем настройки самого API (синхронизируются через IPC / Intent)
        try {
            val apiPrefs = AtlasPreferences(context)
            val scale = apiPrefs.uiScaleTenths
            if (scale in MIN_SCALE_TENTHS..MAX_SCALE_TENTHS) return scale
        } catch (_: Throwable) {
            // Игнорируем ошибки инициализации prefs
        }

        return DEFAULT_SCALE_TENTHS
    }

    /**
     * Создаёт масштабированный контекст с модифицированным densityDpi.
     * Creates a scaled Context with adjusted densityDpi.
     */
    fun wrap(base: Context, scaleTenths: Int = resolveScaleTenths(base)): Context {
        val configuration = Configuration(base.resources.configuration)
        var baseDensityDpi = configuration.densityDpi
        if (baseDensityDpi == Configuration.DENSITY_DPI_UNDEFINED) {
            baseDensityDpi = base.resources.displayMetrics.densityDpi
        }
        val clamped = scaleTenths.coerceIn(MIN_SCALE_TENTHS, MAX_SCALE_TENTHS)
        configuration.densityDpi = Math.round(baseDensityDpi * clamped / 10f)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Извлекает масштаб из Intent extra и сохраняет в AtlasPreferences при наличии.
     * Extracts scale from Intent extra and persists to AtlasPreferences if present.
     */
    fun extractAndPersistIntentScale(context: Context, intent: Intent?): Int? {
        if (intent == null || !intent.hasExtra(AtlasPreferences.EXTRA_UI_SCALE_TENTHS)) {
            return null
        }
        val scale = intent.getIntExtra(AtlasPreferences.EXTRA_UI_SCALE_TENTHS, -1)
        if (scale in MIN_SCALE_TENTHS..MAX_SCALE_TENTHS) {
            try {
                AtlasPreferences(context).uiScaleTenths = scale
            } catch (_: Throwable) {}
            return scale
        }
        return null
    }
}
