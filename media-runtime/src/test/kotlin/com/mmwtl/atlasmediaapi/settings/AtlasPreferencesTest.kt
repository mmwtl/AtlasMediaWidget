package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AtlasPreferencesTest {
    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class FakeEditor(private val backing: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) backing.clear()
                pending.forEach { (k, v) ->
                    if (v == null) backing.remove(k) else backing[k] = v
                }
                pending.clear()
            }
        }
    }

    private class FakeContext(private val prefs: SharedPreferences) : android.content.ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.mmwtl.atlasmediaapi"
    }

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var context: FakeContext
    private lateinit var preferences: AtlasPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        context = FakeContext(fakePrefs)
        preferences = AtlasPreferences(context)
    }

    @Test
    fun `default values are correct`() {
        assertEquals("", preferences.defaultMediaPackage)
        assertFalse(preferences.switchToOnlineBeforeSessionPlay)
        assertFalse(preferences.diagnosticLoggingEnabled)
        assertEquals("", preferences.defaultAudioSource)
        assertEquals(0, preferences.defaultAudioSourceDelaySec)
        assertTrue(preferences.defaultAudioSourceAutoplayOnStartup)
        assertFalse(preferences.autoSwitchToDefaultOnSourceLost)
        assertTrue(preferences.autoSwitchToDefaultAutoplayOnSourceLost)
    }

    @Test
    fun `default audio source persistence`() {
        preferences.defaultAudioSource = "RADIO"
        assertEquals("RADIO", preferences.defaultAudioSource)

        preferences.defaultAudioSource = "CPAA"
        assertEquals("CPAA", preferences.defaultAudioSource)

        preferences.defaultAudioSource = "   "
        assertEquals("", preferences.defaultAudioSource)
    }

    @Test
    fun `default audio source delay is clamped to 0 to 30`() {
        preferences.defaultAudioSourceDelaySec = 15
        assertEquals(15, preferences.defaultAudioSourceDelaySec)

        preferences.defaultAudioSourceDelaySec = -5
        assertEquals(0, preferences.defaultAudioSourceDelaySec)

        preferences.defaultAudioSourceDelaySec = 50
        assertEquals(30, preferences.defaultAudioSourceDelaySec)
    }

    @Test
    fun `autoplay and auto switch flags persistence`() {
        preferences.defaultAudioSourceAutoplayOnStartup = false
        assertFalse(preferences.defaultAudioSourceAutoplayOnStartup)

        preferences.autoSwitchToDefaultOnSourceLost = true
        assertTrue(preferences.autoSwitchToDefaultOnSourceLost)

        preferences.autoSwitchToDefaultAutoplayOnSourceLost = false
        assertFalse(preferences.autoSwitchToDefaultAutoplayOnSourceLost)
    }
}
