package com.mmwtl.atlasmediaapi.media.cluster

import android.content.SharedPreferences
import com.ecarx.xui.adaptapi.diminteraction.IMediaInteraction
import ecarx.fw.api.ECarXAPI
import ecarx.fw.api.ICreator
import ecarx.fw.api.diminteraction.EcarxMediaInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClusterMediaBridgeTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
    }

    @Test
    fun `adaptPlayInfo sets and gets all radio playback fields correctly`() {
        val playInfo = ClusterRadioPlaybackInfo(
            "atlas-radio:1:100100",
            IMediaInteraction.SOURCE_TYPE_FM,
            "100.1",
            "Радио 7",
            "Радио 7",
            "100.1 MHz",
            "FM Radio",
            0L,
            IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING,
            IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING,
            null,
        )

        assertEquals(IMediaInteraction.SOURCE_TYPE_FM, playInfo.sourceType)
        assertEquals("100.1", playInfo.radioFrequency)
        assertEquals("Радио 7", playInfo.radioStationName)
        assertEquals("Радио 7", playInfo.title)
        assertEquals("100.1 MHz", playInfo.artist)
        assertEquals(IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING, playInfo.playbackStatus)
        assertEquals(IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING, playInfo.radioMode)
    }

    @Test
    fun `adaptPlayInfo handles AM mode and paused status`() {
        val playInfo = ClusterRadioPlaybackInfo(
            "atlas-radio:2:738",
            IMediaInteraction.SOURCE_TYPE_AM,
            "738",
            "Вести FM",
            "Вести FM",
            "738 kHz",
            "AM Radio",
            0L,
            IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED,
            IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING,
            null,
        )

        assertEquals(IMediaInteraction.SOURCE_TYPE_AM, playInfo.sourceType)
        assertEquals("738", playInfo.radioFrequency)
        assertEquals("Вести FM", playInfo.radioStationName)
        assertEquals(IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED, playInfo.playbackStatus)
    }

    @Test
    fun `compile-time ECarX API matches OneOS interface ABI`() {
        assertTrue(EcarxMediaInteraction::class.java.isInterface)
        val creatorMethod = ECarXAPI::class.java.getMethod("creator", Class::class.java)
        assertEquals(ICreator::class.java, creatorMethod.returnType)
    }

    @Test
    fun `QNX artwork URI contains only the shared filename`() {
        val sharedFile = java.io.File("/data/vendor/nfs/shared/radio_cover_abc.jpg")
        val wirePath = ClusterMediaBridge.qnxCoverWirePath(sharedFile)
        val uriString = ClusterMediaBridge.qnxCoverUriString(sharedFile)

        assertEquals("/radio_cover_abc.jpg", wirePath)
        assertEquals("file:///radio_cover_abc.jpg", uriString)
    }

    @Test
    fun `cluster presentation uses device-independent online path when artwork is present`() {
        assertEquals(
            IMediaInteraction.SOURCE_TYPE_ONLINE,
            ClusterMediaBridge.displaySourceType(
                IMediaInteraction.SOURCE_TYPE_FM,
                hasArtwork = true,
            ),
        )
        assertEquals(
            IMediaInteraction.SOURCE_TYPE_FM,
            ClusterMediaBridge.displaySourceType(IMediaInteraction.SOURCE_TYPE_FM, hasArtwork = false),
        )
    }

    @Test
    fun `DIM transport marker is stable and preserves an existing query`() {
        assertEquals(
            "content://atlas/cover.jpg?atlas_dim=online-qnx-owned-v9",
            ClusterMediaBridge.dimTransportUriString("content://atlas/cover.jpg"),
        )
        assertEquals(
            "content://atlas/cover.jpg?rev=2&atlas_dim=online-qnx-owned-v9",
            ClusterMediaBridge.dimTransportUriString("content://atlas/cover.jpg?rev=2"),
        )
        assertEquals(
            "content://atlas/cover.jpg?atlas_dim=online-qnx-owned-v9",
            ClusterMediaBridge.dimTransportUriString(
                "content://atlas/cover.jpg?atlas_dim=online-qnx-owned-v9",
            ),
        )
    }

    @Test
    fun `cluster overwrite watchdog starts after the event driven repair burst`() {
        assertEquals(
            listOf(100L, 150L, 250L, 500L, 500L),
            ClusterMediaBridge.REASSERT_BURST_DELAYS_MS,
        )
        assertEquals(listOf(100L, 150L), ClusterMediaBridge.DUPLICATE_REPAIR_DELAYS_MS)
        assertEquals(1_250L, ClusterMediaBridge.DEFAULT_REASSERT_WATCHDOG_INTERVAL_MS)
    }

    @Test
    fun `cluster overwrite watchdog interval is constrained to safe range`() {
        assertEquals(1_000L, ClusterMediaBridge.normalizeReassertWatchdogInterval(0L))
        assertEquals(1_000L, ClusterMediaBridge.normalizeReassertWatchdogInterval(250L))
        assertEquals(1_000L, ClusterMediaBridge.normalizeReassertWatchdogInterval(1_000L))
        assertEquals(1_250L, ClusterMediaBridge.normalizeReassertWatchdogInterval(1_250L))
        assertEquals(5_000L, ClusterMediaBridge.normalizeReassertWatchdogInterval(5_000L))
        assertEquals(5_000L, ClusterMediaBridge.normalizeReassertWatchdogInterval(50_000L))
    }

    @Test
    fun `adaptive watchdog jitter is bounded around the configured base`() {
        assertEquals(1_100L, ClusterMediaBridge.jitteredReassertWatchdogDelayMs(1_250L, -150L))
        assertEquals(1_250L, ClusterMediaBridge.jitteredReassertWatchdogDelayMs(1_250L, 0L))
        assertEquals(1_400L, ClusterMediaBridge.jitteredReassertWatchdogDelayMs(1_250L, 150L))
        assertEquals(1_100L, ClusterMediaBridge.jitteredReassertWatchdogDelayMs(1_250L, -999L))
        assertEquals(1_400L, ClusterMediaBridge.jitteredReassertWatchdogDelayMs(1_250L, 999L))
    }

    @Test
    fun `duplicate callbacks cannot restart the full adaptive burst`() {
        assertEquals(
            ReassertScheduleKind.DUPLICATE_REPAIR,
            ClusterMediaBridge.reassertScheduleKind(
                currentPayloadKey = "fm|97000|record",
                currentScheduleActive = true,
                incomingPayloadKey = "fm|97000|record",
            ),
        )
        assertEquals(
            ReassertScheduleKind.FULL,
            ClusterMediaBridge.reassertScheduleKind(
                currentPayloadKey = "fm|97000|record",
                currentScheduleActive = true,
                incomingPayloadKey = "fm|98000|pride",
            ),
        )
        assertEquals(
            ReassertScheduleKind.FULL,
            ClusterMediaBridge.reassertScheduleKind(
                currentPayloadKey = "fm|97000|record",
                currentScheduleActive = false,
                incomingPayloadKey = "fm|97000|record",
            ),
        )
    }

    @Test
    fun `cluster overwrite watchdog retries with capped exponential backoff`() {
        assertEquals(2_000L, ClusterMediaBridge.reassertRetryDelayMs(1_000L, 1))
        assertEquals(4_000L, ClusterMediaBridge.reassertRetryDelayMs(1_000L, 2))
        assertEquals(8_000L, ClusterMediaBridge.reassertRetryDelayMs(1_000L, 3))
        assertEquals(16_000L, ClusterMediaBridge.reassertRetryDelayMs(1_000L, 4))
        assertEquals(30_000L, ClusterMediaBridge.reassertRetryDelayMs(1_000L, 5))
        assertEquals(2_500L, ClusterMediaBridge.reassertRetryDelayMs(1_250L, 1))
        assertEquals(30_000L, ClusterMediaBridge.reassertRetryDelayMs(5_000L, 30))
    }

    @Test
    fun `watchdog interval store normalizes invalid persisted values`() {
        fakePrefs.edit()
            .putLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, 10L)
            .apply()

        val store = ReassertWatchdogIntervalStore(fakePrefs)

        assertEquals(1_000L, store.value)
        assertEquals(
            1_000L,
            fakePrefs.getLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, -1L),
        )
    }

    @Test
    fun `legacy fixed watchdog value migrates to the adaptive default`() {
        fakePrefs.edit().putLong("reassert_watchdog_interval_ms", 5_000L).apply()

        val store = ReassertWatchdogIntervalStore(fakePrefs)

        assertEquals(1_250L, store.value)
        assertEquals(
            1_250L,
            fakePrefs.getLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, -1L),
        )
    }

    @Test
    fun `watchdog interval store persists normalized updates`() {
        val store = ReassertWatchdogIntervalStore(fakePrefs)

        assertEquals(1_000L, store.set(750L))
        assertEquals(1_000L, store.value)
        assertEquals(
            1_000L,
            fakePrefs.getLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, -1L),
        )

        assertEquals(4_000L, store.set(4_000L))
        assertEquals(4_000L, store.value)
        assertEquals(
            4_000L,
            fakePrefs.getLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, -1L),
        )
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val map: MutableMap<String, Any>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null && value != null) temp[key] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { if (key != null && values != null) temp[key] = values; return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { if (key != null) temp.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { clearFlag = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearFlag) map.clear()
                map.putAll(temp)
            }
        }
    }
}
