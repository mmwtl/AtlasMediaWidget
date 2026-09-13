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

    /**
     * Разрешает текущий масштаб в десятых долях (10..20, дефолт 15 = 1.5×).
     * Значение читается из настроек медиасервиса (AtlasPreferences), синхронизируемых через IPC / Intent.
     *
     * Resolves the current scale in tenths (10..20, default 15 = 1.5×).
     * Value is read from media service preferences (AtlasPreferences), synchronized via IPC / Intent.
     */
    fun resolveScaleTenths(context: Context): Int {
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
