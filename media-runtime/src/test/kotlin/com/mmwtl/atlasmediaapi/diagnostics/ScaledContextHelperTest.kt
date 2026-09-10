package com.mmwtl.atlasmediaapi.diagnostics

import android.content.Context
import android.content.Intent
import com.mmwtl.atlasmediaapi.settings.AtlasPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScaledContextHelperTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `resolveScaleTenths returns default 15 when no preferences are set`() {
        assertEquals(ScaledContextHelper.DEFAULT_SCALE_TENTHS, ScaledContextHelper.resolveScaleTenths(context))
    }

    @Test
    fun `resolveScaleTenths prefers widget preference when available`() {
        context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE)
            .edit()
            .putInt("app_ui_scale_tenths", 17)
            .commit()

        context.getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("ui_scale_tenths", 12)
            .commit()

        assertEquals(17, ScaledContextHelper.resolveScaleTenths(context))
    }

    @Test
    fun `resolveScaleTenths uses API preference when widget preference is absent`() {
        context.getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE)
            .edit()
            .putInt("ui_scale_tenths", 13)
            .commit()

        assertEquals(13, ScaledContextHelper.resolveScaleTenths(context))
    }

    @Test
    fun `wrap scales densityDpi proportionally`() {
        val baseDensity = context.resources.configuration.densityDpi
        val scaled = ScaledContextHelper.wrap(context, 15)
        val expected = Math.round(baseDensity * 15 / 10f)
        assertEquals(expected, scaled.resources.configuration.densityDpi)

        val scaled20 = ScaledContextHelper.wrap(context, 20)
        val expected20 = Math.round(baseDensity * 20 / 10f)
        assertEquals(expected20, scaled20.resources.configuration.densityDpi)
    }

    @Test
    fun `extractAndPersistIntentScale extracts valid extra and persists to AtlasPreferences`() {
        val intent = Intent().apply {
            putExtra(AtlasPreferences.EXTRA_UI_SCALE_TENTHS, 18)
        }
        val result = ScaledContextHelper.extractAndPersistIntentScale(context, intent)
        assertEquals(18, result)
        assertEquals(18, AtlasPreferences(context).uiScaleTenths)
    }

    @Test
    fun `extractAndPersistIntentScale ignores invalid extra`() {
        val outOfRangeIntent = Intent().apply {
            putExtra(AtlasPreferences.EXTRA_UI_SCALE_TENTHS, 25)
        }
        assertNull(ScaledContextHelper.extractAndPersistIntentScale(context, outOfRangeIntent))

        val emptyIntent = Intent()
        assertNull(ScaledContextHelper.extractAndPersistIntentScale(context, emptyIntent))
        assertNull(ScaledContextHelper.extractAndPersistIntentScale(context, null))
    }
}
