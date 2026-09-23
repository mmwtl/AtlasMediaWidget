package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.content.SharedPreferences
import com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract
import com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogRepository
import com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge
import org.json.JSONArray
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
        context.getSharedPreferences("radio_catalog_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cluster_dim_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("media_settings_meta", Context.MODE_PRIVATE).edit().clear().commit()
        listOf("custom_radio", "custom_radio_prev", "custom_radio_next").forEach {
            java.io.File(context.filesDir, it).deleteRecursively()
        }

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
        assertFalse(snapshot.minimizeOnlinePlayerAfterAutostart)
        assertTrue(snapshot.radioWidgetBroadcastEnabled)
        assertTrue(snapshot.clusterCoversEnabled)
        assertFalse(snapshot.clusterOnlineEnabled)
        assertFalse(snapshot.clusterOnlineProgressEnabled)
        assertEquals(1250L, snapshot.clusterWatchdogIntervalMs)
        assertEquals(100L, snapshot.clusterReassertBurstIntervalMs)
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
            putBoolean(MediaBridgeContract.Key.MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART, true)
            putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED, true)
            putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_PROGRESS_ENABLED, true)
            putLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, 2000L)
            putLong(MediaBridgeContract.Key.CLUSTER_REASSERT_BURST_INTERVAL_MS, 200L)
            putInt(MediaBridgeContract.Key.UI_SCALE_TENTHS, 18)
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
        assertTrue(snap.minimizeOnlinePlayerAfterAutostart)
        assertTrue(snap.clusterOnlineEnabled)
        assertTrue(snap.clusterOnlineProgressEnabled)
        assertEquals(2000L, snap.clusterWatchdogIntervalMs)
        assertEquals(200L, snap.clusterReassertBurstIntervalMs)
        assertEquals(18, snap.uiScaleTenths)
        assertTrue(settingsChangedTriggered)
    }

    @Test
    fun `online cluster transmission always includes facade progress`() {
        val enabled = controller.updateSettings(controller.getRevision(), Bundle().apply {
            putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED, true)
        }).snapshot!!
        assertTrue(enabled.clusterOnlineEnabled)
        assertTrue(enabled.clusterOnlineProgressEnabled)

        val legacyProgressUpdate = controller.updateSettings(controller.getRevision(), Bundle().apply {
            putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_PROGRESS_ENABLED, false)
        }).snapshot!!
        assertTrue(legacyProgressUpdate.clusterOnlineProgressEnabled)

        val disabled = controller.updateSettings(controller.getRevision(), Bundle().apply {
            putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED, false)
        }).snapshot!!
        assertFalse(disabled.clusterOnlineEnabled)
        assertFalse(disabled.clusterOnlineProgressEnabled)
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
    fun `successful settings update commits owners before publishing revision`() {
        val events = mutableListOf<String>()
        val recordingContext = RecordingContext(context, events)
        val recordingController = MediaSettingsController(
            context = recordingContext,
            preferences = AtlasPreferences(recordingContext),
            radioCatalogRepository = RadioCatalogRepository(recordingContext),
            clusterMediaBridge = ClusterMediaBridge(recordingContext),
        )
        events.clear()

        val result = recordingController.updateSettings(recordingController.getRevision(), Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "ONLINE")
            putInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC, 9)
            putBoolean(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY, false)
            putBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT, true)
            putBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, false)
            putString(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE, "com.example.player")
            putBoolean(MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED, false)
            putBoolean(MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED, false)
            putLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, 2500L)
            putLong(MediaBridgeContract.Key.CLUSTER_REASSERT_BURST_INTERVAL_MS, 150L)
        })
        assertEquals(MediaBridgeContract.Status.OK, result.status)
        assertEquals("com.example.player", result.snapshot?.defaultMediaPackage)
        assertEquals(
            listOf("atlas_media_api_settings", "radio_catalog_prefs", "cluster_dim_prefs", "media_settings_meta"),
            events,
        )
    }

    @Test
    fun `failed owner commit returns failure without publishing revision`() {
        val events = mutableListOf<String>()
        val failingContext = RecordingContext(context, events, failCommitFor = "atlas_media_api_settings")
        var callbackCalled = false
        val failingController = MediaSettingsController(
            context = failingContext,
            preferences = AtlasPreferences(failingContext),
            radioCatalogRepository = RadioCatalogRepository(failingContext),
            clusterMediaBridge = ClusterMediaBridge(failingContext),
            onSettingsChanged = { callbackCalled = true },
        )
        events.clear()
        val initialRevision = failingController.getRevision()

        val result = failingController.updateSettings(initialRevision, Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "ONLINE")
        })

        assertEquals(MediaBridgeContract.Status.FAILED, result.status)
        assertNull(result.snapshot)
        assertEquals(initialRevision, failingController.getRevision())
        assertFalse(callbackCalled)
        assertEquals(listOf("atlas_media_api_settings"), events)
    }

    @Test
    fun `failed revision commit returns failure after draining owners`() {
        val events = mutableListOf<String>()
        val failingContext = RecordingContext(context, events, failCommitFor = "media_settings_meta")
        var callbackCalled = false
        val failingController = MediaSettingsController(
            context = failingContext,
            preferences = AtlasPreferences(failingContext),
            radioCatalogRepository = RadioCatalogRepository(failingContext),
            clusterMediaBridge = ClusterMediaBridge(failingContext),
            onSettingsChanged = { callbackCalled = true },
        )
        events.clear()
        val initialRevision = failingController.getRevision()

        val result = failingController.updateSettings(initialRevision, Bundle().apply {
            putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, "ONLINE")
        })

        assertEquals(MediaBridgeContract.Status.FAILED, result.status)
        assertNull(result.snapshot)
        assertEquals(initialRevision, failingController.getRevision())
        assertFalse(callbackCalled)
        assertEquals(
            listOf("atlas_media_api_settings", "radio_catalog_prefs", "cluster_dim_prefs", "media_settings_meta"),
            events,
        )
    }

    @Test
    fun `updateSettings validates source delay and cluster interval bounds`() {
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

        val badBurst = Bundle().apply {
            putLong(MediaBridgeContract.Key.CLUSTER_REASSERT_BURST_INTERVAL_MS, 1_000L)
        }
        val res4 = controller.updateSettings(null, badBurst)
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, res4.status)
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
        assertFalse(json.has("clusterRadioFacadeEnabled"))
        assertTrue(json.has("clusterOnlineEnabled"))
        assertTrue(json.has("clusterOnlineProgressEnabled"))
        assertFalse(json.has("clusterOnlineFacadeProgressEnabled"))
        assertTrue(json.has("minimizeOnlinePlayerAfterAutostart"))
        assertTrue(json.has("clusterReassertBurstIntervalMs"))
        assertFalse(json.has("uiScaleTenths"))
    }

    @Test
    fun `settings export excludes the active custom radio catalog`() {
        assertEquals(1, radioCatalogRepository.importCustomZip(ByteArrayInputStream(radioZip("90000,Custom,FM,\n"))).getOrThrow())

        val output = ByteArrayOutputStream()
        controller.exportMediaBackup(output)
        val names = mutableListOf<String>()
        var mediaJson: JSONObject? = null
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                if (entry.name == "media.json") mediaJson = JSONObject(String(zip.readBytes(), StandardCharsets.UTF_8))
                entry = zip.nextEntry
            }
        }

        assertEquals(listOf("manifest.json", "media.json"), names)
        assertNotNull(mediaJson)
        assertFalse(mediaJson!!.has("catalogMode"))
    }

    @Test
    fun `radio export contains the active custom catalog`() {
        assertEquals(1, radioCatalogRepository.importCustomZip(ByteArrayInputStream(radioZip("90000,Custom,FM,\n"))).getOrThrow())

        val output = ByteArrayOutputStream()
        radioCatalogRepository.exportCatalogZip(output)
        var stationsCsv = ""
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "stations.csv") stationsCsv = String(zip.readBytes(), StandardCharsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertTrue(stationsCsv.contains("90000,Custom,FM,"))
    }

    @Test
    fun `legacy full settings import validates radio but preserves active catalog`() {
        assertEquals(1, radioCatalogRepository.importCustomZip(ByteArrayInputStream(radioZip("90000,Active,FM,\n"))).getOrThrow())
        val before = controller.getSnapshot()

        val operation = UUID.randomUUID().toString()
        val prepared = controller.prepareMediaImport(
            operation,
            ByteArrayInputStream(mediaArchiveWithRadio("BT", "91000,Legacy,FM,\n")),
        )
        assertEquals(MediaBridgeContract.Status.OK, prepared.status)
        assertEquals("custom", prepared.catalogMode)
        assertEquals(MediaBridgeContract.Status.OK, controller.commitMediaImport(operation, prepared.stagingToken).status)

        val after = controller.getSnapshot()
        assertEquals("BT", after.defaultAudioSource)
        assertEquals("CUSTOM", after.catalogType)
        assertEquals(before.catalogStationCount, after.catalogStationCount)
        assertEquals("Active", radioCatalogRepository.stations().single().name)
    }

    @Test
    fun `replaying committed import reports the actual catalog mode`() {
        assertEquals(1, radioCatalogRepository.importCustomZip(ByteArrayInputStream(radioZip("90000,Active,FM,\n"))).getOrThrow())
        val operation = UUID.randomUUID().toString()
        val prepared = controller.prepareMediaImport(operation, ByteArrayInputStream(mediaArchive("")))
        assertEquals(MediaBridgeContract.Status.OK, prepared.status)
        assertEquals("builtin", prepared.catalogMode)
        assertEquals(MediaBridgeContract.Status.OK, controller.commitMediaImport(operation, prepared.stagingToken).status)

        // The legacy idempotency response must describe the real catalog. Audio source is
        // unrelated and is deliberately empty here to catch the old fabricated value.
        val replay = controller.prepareMediaImport(operation, ByteArrayInputStream(byteArrayOf()))
        assertEquals(MediaBridgeContract.Status.OK, replay.status)
        assertEquals("custom", replay.catalogMode)
        assertEquals(1, replay.stationCount)
    }

    @Test
    fun `invalid legacy radio section is rejected without mutating active catalog`() {
        assertEquals(1, radioCatalogRepository.importCustomZip(ByteArrayInputStream(radioZip("90000,Active,FM,\n"))).getOrThrow())
        val operation = UUID.randomUUID().toString()
        val prepared = controller.prepareMediaImport(
            operation,
            ByteArrayInputStream(mediaArchiveWithRadio("USB", "91000,Legacy,FM,missing.png\n")),
        )

        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, prepared.status)
        assertEquals("CUSTOM", controller.getSnapshot().catalogType)
        assertEquals("Active", radioCatalogRepository.stations().single().name)
        assertEquals("", preferences.defaultAudioSource)
    }

    @Test
    fun `settings import resets omitted portable fields but preserves widget scale`() {
        preferences.defaultAudioSource = "BT"
        preferences.defaultAudioSourceDelaySec = 12
        preferences.defaultAudioSourceAutoplayOnStartup = false
        preferences.autoSwitchToDefaultOnSourceLost = true
        preferences.autoSwitchToDefaultAutoplayOnSourceLost = false
        preferences.defaultMediaPackage = "com.example.player"
        preferences.minimizeOnlinePlayerAfterAutostart = true
        radioCatalogRepository.setWidgetBroadcastEnabled(false)
        clusterMediaBridge.setClusterCoversEnabled(false)
        clusterMediaBridge.setClusterOnlineEnabled(true)
        clusterMediaBridge.setReassertWatchdogIntervalMs(4000L)
        clusterMediaBridge.setReassertBurstIntervalMs(300L)
        preferences.uiScaleTenths = 19

        val operation = UUID.randomUUID().toString()
        val minimal = JSONObject().put("format", "atlas-media-settings").put("schemaVersion", 1)
            // Legacy media archives may carry this derived Widget-owned field. It is validated
            // for compatibility, but must never overwrite the local diagnostic scale.
            .put("uiScaleTenths", 10)
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1).toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media.json"))
            zip.write(minimal.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val prepared = controller.prepareMediaImport(operation, ByteArrayInputStream(archive.toByteArray()))
        assertEquals(MediaBridgeContract.Status.OK, prepared.status)
        assertEquals(MediaBridgeContract.Status.OK, controller.commitMediaImport(operation, prepared.stagingToken).status)
        val snapshot = controller.getSnapshot()
        assertEquals("", snapshot.defaultAudioSource)
        assertEquals(0, snapshot.defaultAudioSourceDelaySec)
        assertTrue(snapshot.defaultAudioSourceAutoplayOnStartup)
        assertFalse(snapshot.autoSwitchToDefaultOnSourceLost)
        assertTrue(snapshot.autoSwitchToDefaultAutoplayOnSourceLost)
        assertEquals("", snapshot.defaultMediaPackage)
        assertFalse(snapshot.minimizeOnlinePlayerAfterAutostart)
        assertTrue(snapshot.radioWidgetBroadcastEnabled)
        assertTrue(snapshot.clusterCoversEnabled)
        assertFalse(snapshot.clusterOnlineEnabled)
        assertFalse(snapshot.clusterOnlineProgressEnabled)
        assertEquals(1250L, snapshot.clusterWatchdogIntervalMs)
        assertEquals(100L, snapshot.clusterReassertBurstIntervalMs)
        assertEquals(19, snapshot.uiScaleTenths)
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
            put("minimizeOnlinePlayerAfterAutostart", true)
            put("radioWidgetBroadcastEnabled", false)
            put("clusterCoversEnabled", true)
            put("clusterRadioFacadeEnabled", true)
            put("clusterOnlineEnabled", true)
            put("clusterOnlineFacadeProgressEnabled", true)
            put("clusterWatchdogIntervalMs", 3000L)
            put("clusterReassertBurstIntervalMs", 250L)
            put("catalogMode", "builtin")
        }

        val zipBaos = ByteArrayOutputStream()
        ZipOutputStream(zipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1).toString().toByteArray())
            zos.closeEntry()
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
        assertTrue(snap.minimizeOnlinePlayerAfterAutostart)
        assertFalse(snap.radioWidgetBroadcastEnabled)
        assertTrue(snap.clusterOnlineEnabled)
        assertTrue(snap.clusterOnlineProgressEnabled)
        assertEquals(3000L, snap.clusterWatchdogIntervalMs)
        assertEquals(250L, snap.clusterReassertBurstIntervalMs)
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
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1).toString().toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("media.json"))
            zos.write(mediaJson.toString().toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val prepResult = controller.prepareMediaImport(opId, ByteArrayInputStream(zipBaos.toByteArray()))
        assertEquals(MediaBridgeContract.Status.OK, prepResult.status)

        controller.abortMediaImport(opId)
        assertEquals("IDLE", controller.getImportStatus(opId))

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
    @Test
    fun `prepared import survives recreation and commit is idempotent`() {
        val operation = UUID.randomUUID().toString()
        val prepared = controller.prepareMediaImport(operation, ByteArrayInputStream(mediaArchive("USB")))
        assertEquals(MediaBridgeContract.Status.OK, prepared.status)
        val restarted = restartController()
        assertEquals("PREPARED", restarted.getImportStatus(operation))
        assertEquals(MediaBridgeContract.Status.OK, restarted.commitMediaImport(operation, prepared.stagingToken).status)
        val revision = restarted.getRevision()
        assertEquals("COMMITTED", restartController().getImportStatus(operation))
        assertEquals(MediaBridgeContract.Status.OK, restarted.commitMediaImport(operation, prepared.stagingToken).status)
        assertEquals(revision, restarted.getRevision())
    }

    @Test
    fun `committing import is finished when status is queried after recreation`() {
        val operation = UUID.randomUUID().toString()
        val prepared = controller.prepareMediaImport(operation, ByteArrayInputStream(mediaArchive("BT")))
        assertEquals(MediaBridgeContract.Status.OK, prepared.status)
        val metadata = java.io.File(context.filesDir, "staging_media_import_$operation/operation.json")
        val journal = JSONObject(metadata.readText()).put("status", "COMMITTING")
        metadata.writeText(journal.toString())
        val restarted = restartController()
        assertEquals("COMMITTED", restarted.getImportStatus(operation))
        assertEquals("BT", restarted.getSnapshot().defaultAudioSource)
    }

    @Test
    fun `invalid source and wrong bundle types leave settings unchanged`() {
        val revision = controller.getRevision()
        val prepared = controller.prepareMediaImport(UUID.randomUUID().toString(), ByteArrayInputStream(mediaArchive("INVALID")))
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, prepared.status)
        val invalid = Bundle().apply { putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY, "true") }
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, controller.updateSettings(revision, invalid).status)
        assertEquals(revision, controller.getRevision())
        assertEquals("", controller.getSnapshot().defaultAudioSource)
    }

    @Test
    fun `missing custom cover is rejected before settings change`() {
        val json = JSONObject().put("format", "atlas-media-settings").put("schemaVersion", 1)
            .put("defaultAudioSource", "BT").put("catalogMode", "custom")
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            listOf(
                "manifest.json" to JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1).toString(),
                "media.json" to json.toString(),
                "radio/stations.csv" to "frequency_khz,name,band,cover\n98800,Test,FM,missing.png\n"
            ).forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(contents.toByteArray()); zip.closeEntry()
            }
        }
        val prepared = controller.prepareMediaImport(UUID.randomUUID().toString(), ByteArrayInputStream(output.toByteArray()))
        assertEquals(MediaBridgeContract.Status.VALIDATION_ERROR, prepared.status)
        assertTrue(prepared.errorMessage.contains("missing.png"))
        assertEquals("", controller.getSnapshot().defaultAudioSource)
    }

    @Test
    fun `unsafe operation id is rejected without creating directories`() {
        val prepared = controller.prepareMediaImport("../../escape", ByteArrayInputStream(mediaArchive("BT")))
        assertEquals(MediaBridgeContract.Status.INVALID_REQUEST, prepared.status)
    }

    private fun restartController() = MediaSettingsController(context, preferences, radioCatalogRepository, clusterMediaBridge)

    private fun mediaArchive(source: String): ByteArray {
        val json = JSONObject().put("format", "atlas-media-settings").put("schemaVersion", 1)
            .put("defaultAudioSource", source).put("catalogMode", "builtin")
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            listOf("manifest.json" to JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1).toString(),
                "media.json" to json.toString()).forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(contents.toByteArray()); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun mediaArchiveWithRadio(source: String, stationsCsv: String): ByteArray {
        val json = JSONObject().put("format", "atlas-media-settings").put("schemaVersion", 1)
            .put("defaultAudioSource", source).put("catalogMode", "custom")
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("format", "atlas-media-backup").put("schemaVersion", 1)
                .put("sections", JSONArray().put("media").put("radio")).toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media.json"))
            zip.write(json.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("radio/stations.csv"))
            zip.write(("frequency_khz,name,band,cover\n" + stationsCsv).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun radioZip(stationsCsv: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("stations.csv"))
            zip.write(("frequency_khz,name,band,cover\n" + stationsCsv).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private class RecordingContext(
        base: Context,
        private val events: MutableList<String>,
        private val failCommitFor: String? = null,
    ) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            val preferenceName = name.orEmpty()
            return RecordingSharedPreferences(
                delegate = super.getSharedPreferences(name, mode),
                name = preferenceName,
                events = events,
                failCommit = preferenceName == failCommitFor,
            )
        }
    }

    private class RecordingSharedPreferences(
        private val delegate: SharedPreferences,
        private val name: String,
        private val events: MutableList<String>,
        private val failCommit: Boolean,
    ) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor = RecordingEditor(
            delegate = delegate.edit(),
            name = name,
            events = events,
            failCommit = failCommit,
        )
    }

    private class RecordingEditor(
        private val delegate: SharedPreferences.Editor,
        private val name: String,
        private val events: MutableList<String>,
        private val failCommit: Boolean,
    ) : SharedPreferences.Editor by delegate {
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            delegate.putLong(key, value)
            return this
        }

        override fun commit(): Boolean {
            events += name
            if (failCommit) return false
            return delegate.commit()
        }
    }

}
