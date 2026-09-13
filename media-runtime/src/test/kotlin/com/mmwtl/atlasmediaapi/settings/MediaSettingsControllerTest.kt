package com.mmwtl.atlasmediaapi.settings

import android.content.Context
import android.os.Bundle
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
    fun `settings import resets omitted portable fields to defaults`() {
        preferences.defaultAudioSource = "BT"
        preferences.defaultAudioSourceDelaySec = 12
        preferences.defaultAudioSourceAutoplayOnStartup = false
        preferences.autoSwitchToDefaultOnSourceLost = true
        preferences.autoSwitchToDefaultAutoplayOnSourceLost = false
        preferences.defaultMediaPackage = "com.example.player"
        preferences.switchToOnlineBeforeSessionPlay = true
        radioCatalogRepository.setWidgetBroadcastEnabled(false)
        clusterMediaBridge.setClusterCoversEnabled(false)
        clusterMediaBridge.setReassertWatchdogIntervalMs(4000L)
        preferences.uiScaleTenths = 19

        val operation = UUID.randomUUID().toString()
        val minimal = JSONObject().put("format", "atlas-media-settings").put("schemaVersion", 1)
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
        assertFalse(snapshot.switchToOnlineBeforeSessionPlay)
        assertTrue(snapshot.radioWidgetBroadcastEnabled)
        assertTrue(snapshot.clusterCoversEnabled)
        assertEquals(1250L, snapshot.clusterWatchdogIntervalMs)
        assertEquals(15, snapshot.uiScaleTenths)
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

}
