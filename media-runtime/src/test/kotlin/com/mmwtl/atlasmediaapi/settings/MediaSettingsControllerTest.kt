package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.os.Bundle
import com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract
import com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogRepository
import com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class MediaSettingsControllerTest {

    private lateinit var context: Context
    private lateinit var preferences: AtlasPreferences
    private lateinit var radioCatalogRepository: RadioCatalogRepository
    private lateinit var clusterMediaBridge: ClusterMediaBridge
    private lateinit var controller: MediaSettingsController
    private var settingsChangedTriggered = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cluster_dim_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("media_settings_meta", Context.MODE_PRIVATE).edit().clear().commit()

        preferences = AtlasPreferences(context)
        radioCatalogRepository = RadioCatalogRepository(context)
        clusterMediaBridge = ClusterMediaBridge(context)
        settingsChangedTriggered = false
        controller = MediaSettingsController(
            context = context,
            preferences = preferences,
            radioCatalogRepository = radioCatalogRepository,
            clusterMediaBridge = clusterMediaBridge,
            onSettingsChanged = { settingsChangedTriggered = true }
        )
    }

    @Test
    fun `initial snapshot has defaults and positive revision`() {
        val snapshot = controller.getSnapshot()
        assertTrue(snapshot.revision >= 1L)
        assertEquals("", snapshot.defaultAudioSource)
        assertEquals(0, snapshot.defaultAudioSourceDelaySec)
        assertTrue(snapshot.defaultAudioSourceAutoplayOnStartup)
        assertFalse(snapshot.autoSwitchToDefaultOnSourceLost)
        assertTrue(snapshot.autoSwitchToDefaultAutoplayOnSourceLost)
        assertFalse(snapshot.switchToOnlineBeforeSessionPlay)
        assertTrue(snapshot.radioWidgetBroadcastEnabled)
        assertTrue(snapshot.clusterCoversEnabled)
        assertEquals(1250L, snapshot.clusterWatchdogIntervalMs)
        assertEquals("BUILT_IN", snapshot.catalogType)
    }

    @Test
    fun `updateSettings updates preferences and increments revision`() {
        val initialRev = controller.getRevision()
        val changes = Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "RADIO")
            putInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC, 5)
            putBoolean(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY, false)
            putBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT, true)
            putBoolean(MediaBridgeContract.Key.SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY, true)
            putLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, 2000L)
        }

        val result = controller.updateSettings(initialRev, changes)
        assertEquals(MediaBridgeContract.Status.OK, result.status)
        assertNotNull(result.snapshot)
        val snap = result.snapshot!!
        assertEquals(initialRev + 1, snap.revision)
        assertEquals("RADIO", snap.defaultAudioSource)
        assertEquals(5, snap.defaultAudioSourceDelaySec)
        assertFalse(snap.defaultAudioSourceAutoplayOnStartup)
        assertTrue(snap.autoSwitchToDefaultOnSourceLost)
        assertTrue(snap.switchToOnlineBeforeSessionPlay)
        assertEquals(2000L, snap.clusterWatchdogIntervalMs)
        assertTrue(settingsChangedTriggered)
    }

    @Test
    fun `updateSettings rejects optimistic lock mismatch`() {
        val initialRev = controller.getRevision()
        val changes = Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "BT")
        }

        val result = controller.updateSettings(initialRev + 999, changes)
        assertEquals(MediaBridgeContract.Status.CONFLICT, result.status)
        assertNull(result.snapshot)
        assertFalse(settingsChangedTriggered)
    }

    @Test
    fun `updateSettings validates source delay and watchdog range bounds`() {
        val badSource = Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "INVALID_SOURCE")
        }
        val res1 = controller.updateSettings(null, badSource)
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, res1.status)

        val badDelay = Bundle().apply {
            putInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC, 100) // max is 30
        }
        val res2 = controller.updateSettings(null, badDelay)
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, res2.status)

        val badWatchdog = Bundle().apply {
            putLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, 10_000L) // max is 5000
        }
        val res3 = controller.updateSettings(null, badWatchdog)
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, res3.status)
    }

    @Test
    fun `exportMediaBackup creates valid zip with media json`() {
        val baos = ByteArrayOutputStream()
        controller.exportMediaBackup(baos)
        val zipBytes = baos.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        var foundMediaJson = false
        var mediaJsonContent: String? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "media.json") {
                    foundMediaJson = true
                    mediaJsonContent = String(zis.readBytes(), StandardCharsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }

        assertTrue(foundMediaJson)
        assertNotNull(mediaJsonContent)
        val json = JSONObject(mediaJsonContent!!)
        assertTrue(json.has("defaultAudioSource"))
        assertTrue(json.has("radioWidgetBroadcastEnabled"))
        assertTrue(json.has("clusterCoversEnabled"))
    }

    @Test
    fun `prepareMediaImport and commitMediaImport two-phase workflow succeeds`() {
        val initialRev = controller.getRevision()
        val opId = UUID.randomUUID().toString()

        // Create media backup ZIP with changed default source
        val mediaJson = JSONObject().apply {
            put("format", "atlas-media-settings")
            put("schemaVersion", 1)
            put("defaultAudioSource", "USB")
            put("defaultAudioSourceDelaySec", 7)
            put("radioWidgetBroadcastEnabled", false)
            put("clusterCoversEnabled", true)
            put("clusterWatchdogIntervalMs", 3000L)
            put("catalogMode", "builtin")
        }

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("media.json"))
            zos.write(mediaJson.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val prepResult = controller.prepareMediaImport(opId, ByteArrayInputStream(zipBaos.toByteArray()))
        assertEquals(MediaBridgeContract.Status.OK, prepResult.status)
        assertTrue(prepResult.stagingToken.isNotEmpty())
        assertEquals("builtin", prepResult.catalogMode)

        // Snapshot should not change until commit
        assertEquals("", controller.getSnapshot().defaultAudioSource)

        val commitResult = controller.commitMediaImport(opId, prepResult.stagingToken)
        assertEquals(MediaBridgeContract.Status.OK, commitResult.status)
        assertNotNull(commitResult.snapshot)

        val snap = commitResult.snapshot!!
        assertEquals("USB", snap.defaultAudioSource)
        assertEquals(7, snap.defaultAudioSourceDelaySec)
        assertFalse(snap.radioWidgetBroadcastEnabled)
        assertEquals(3000L, snap.clusterWatchdogIntervalMs)
        assertTrue(snap.revision > initialRev)
    }

    @Test
    fun `abortMediaImport removes staged files and prevents commit`() {
        val opId = UUID.randomUUID().toString()
        val mediaJson = JSONObject().apply {
            put("format", "atlas-media-settings")
            put("schemaVersion", 1)
            put("defaultAudioSource", "CPAA")
        }
        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("media.json"))
            zos.write(mediaJson.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val prepResult = controller.prepareMediaImport(opId, ByteArrayInputStream(zipBaos.toByteArray()))
        assertEquals(MediaBridgeContract.Status.OK, prepResult.status)

        val abortResult = controller.abortMediaImport(opId)
        assertEquals(true, abortResult)

        // Attempting commit after abort must fail
        val commitResult = controller.commitMediaImport(opId, prepResult.stagingToken)
        assertEquals(MediaBridgeContract.Status.INVALID_REQUEST, commitResult.status)
    }

    @Test
    fun `prepareMediaImport detects path traversal and rejects zip`() {
        val opId = UUID.randomUUID().toString()
        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.json"))
            zos.write("{}".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val prepResult = controller.prepareMediaImport(opId, ByteArrayInputStream(zipBaos.toByteArray()))
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, prepResult.status)
        assertTrue(prepResult.errorMessage.contains("Небезопасный путь"))
    }
}
