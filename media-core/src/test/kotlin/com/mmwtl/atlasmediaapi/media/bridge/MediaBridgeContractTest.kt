package com.mmwtl.atlasmediaapi.media.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaBridgeContractTest {
    @Test
    fun `only the advertised protocol version is accepted`() {
        assertEquals(
            MediaBridgeContract.Status.UNSUPPORTED_VERSION,
            MediaBridgeContract.negotiate(MediaBridgeContract.MIN_PROTOCOL_VERSION - 1),
        )
        assertEquals(
            MediaBridgeContract.Status.OK,
            MediaBridgeContract.negotiate(MediaBridgeContract.PROTOCOL_VERSION),
        )
        assertEquals(
            MediaBridgeContract.Status.UNSUPPORTED_VERSION,
            MediaBridgeContract.negotiate(MediaBridgeContract.MAX_PROTOCOL_VERSION + 1),
        )
    }

    @Test
    fun `contract constants match expected values`() {
        assertEquals("com.mmwtl.atlasmediaapi.media.BIND", MediaBridgeContract.SERVICE_ACTION)
        assertEquals("com.mmwtl.atlasmediaapi", MediaBridgeContract.SERVICE_PACKAGE)
        assertEquals("com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService", MediaBridgeContract.SERVICE_CLASS)
        assertEquals(1, MediaBridgeContract.PROTOCOL_VERSION)
        assertEquals(1, MediaBridgeContract.ClientMessage.REGISTER)
        assertEquals(2, MediaBridgeContract.ClientMessage.UNREGISTER)
        assertEquals(3, MediaBridgeContract.ClientMessage.GET_SNAPSHOT)
        assertEquals(4, MediaBridgeContract.ClientMessage.COMMAND)
        assertEquals(5, MediaBridgeContract.ClientMessage.GET_RADIO_STATIONS)
        assertEquals(100, MediaBridgeContract.ServerMessage.REGISTERED)
        assertEquals(101, MediaBridgeContract.ServerMessage.SNAPSHOT)
        assertEquals(102, MediaBridgeContract.ServerMessage.COMMAND_RESULT)
        assertEquals(103, MediaBridgeContract.ServerMessage.ERROR)
        assertEquals(104, MediaBridgeContract.ServerMessage.RADIO_STATIONS)
        assertEquals(6, MediaBridgeContract.ClientMessage.GET_SETTINGS)
        assertEquals(7, MediaBridgeContract.ClientMessage.UPDATE_SETTINGS)
        assertEquals(8, MediaBridgeContract.ClientMessage.EXPORT_MEDIA_BACKUP)
        assertEquals(9, MediaBridgeContract.ClientMessage.PREPARE_MEDIA_IMPORT)
        assertEquals(10, MediaBridgeContract.ClientMessage.COMMIT_MEDIA_IMPORT)
        assertEquals(11, MediaBridgeContract.ClientMessage.GET_IMPORT_STATUS)
        assertEquals(12, MediaBridgeContract.ClientMessage.ABORT_MEDIA_IMPORT)
        assertEquals(13, MediaBridgeContract.ClientMessage.RESTORE_DEFAULT_CATALOG)
        assertEquals(14, MediaBridgeContract.ClientMessage.EXPORT_RADIO_CATALOG)
        assertEquals(15, MediaBridgeContract.ClientMessage.IMPORT_RADIO_CATALOG)
        assertEquals(105, MediaBridgeContract.ServerMessage.SETTINGS)
        assertEquals(106, MediaBridgeContract.ServerMessage.SETTINGS_UPDATED)
        assertEquals(107, MediaBridgeContract.ServerMessage.MEDIA_BACKUP_EXPORTED)
        assertEquals(108, MediaBridgeContract.ServerMessage.MEDIA_IMPORT_PREPARED)
        assertEquals(109, MediaBridgeContract.ServerMessage.MEDIA_IMPORT_COMMITTED)
        assertEquals(110, MediaBridgeContract.ServerMessage.MEDIA_IMPORT_STATUS)
        assertEquals(111, MediaBridgeContract.ServerMessage.MEDIA_IMPORT_ABORTED)
        assertEquals(112, MediaBridgeContract.ServerMessage.DEFAULT_CATALOG_RESTORED)
        assertEquals(114, MediaBridgeContract.ServerMessage.RADIO_CATALOG_EXPORTED)
        assertEquals(115, MediaBridgeContract.ServerMessage.RADIO_CATALOG_IMPORTED)
        assertEquals(9, MediaBridgeContract.Status.VALIDATION_ERROR)
        assertEquals(10, MediaBridgeContract.Status.CONFLICT)
        assertEquals(11, MediaBridgeContract.Status.IO_ERROR)
        assertEquals("uiScaleTenths", MediaBridgeContract.Key.UI_SCALE_TENTHS)
        assertEquals("settingsRevision", MediaBridgeContract.Key.SETTINGS_REVISION)
        assertEquals("defaultAudioSource", MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE)
        assertEquals(
            "minimizeOnlinePlayerAfterAutostart",
            MediaBridgeContract.Key.MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART,
        )
    }

    @Test
    fun `media settings snapshot bundle round trip preserves all fields`() {
        val snapshot = MediaSettingsSnapshot(
            revision = 42L,
            defaultAudioSource = "RADIO",
            defaultAudioSourceDelaySec = 5,
            defaultAudioSourceAutoplayOnStartup = false,
            autoSwitchToDefaultOnSourceLost = true,
            autoSwitchToDefaultAutoplayOnSourceLost = false,
            defaultMediaPackage = "ru.yandex.music",
            minimizeOnlinePlayerAfterAutostart = true,
            radioWidgetBroadcastEnabled = false,
            clusterCoversEnabled = false,
            clusterOnlineEnabled = true,
            clusterOnlineProgressEnabled = true,
            clusterWatchdogIntervalMs = 2500L,
            clusterReassertBurstIntervalMs = 200L,
            catalogType = "CUSTOM",
            catalogStationCount = 10,
            catalogDescription = "Custom Catalog",
            uiScaleTenths = 18,
        )
        val bundle = snapshot.toBundle()
        val restored = bundle.toMediaSettingsSnapshot()
        assertEquals(snapshot, restored)
    }

    @Test
    fun `default media source capabilities include playback controls for CPAA, BT, USB, Radio`() {
        val cpaaCaps = BridgeAudioSource.CPAA.defaultCapabilities()
        val expected = MediaCapabilities.BASIC_PLAYBACK or
                MediaCapabilities.TRACK_NAVIGATION or
                MediaCapabilities.SET_SOURCE
        assertEquals(expected, cpaaCaps)
        assertEquals(expected, BridgeAudioSource.BT.defaultCapabilities())
        assertEquals(expected, BridgeAudioSource.USB.defaultCapabilities())
        assertEquals(
            expected or MediaCapabilities.TUNE_RADIO,
            BridgeAudioSource.RADIO.defaultCapabilities(),
        )
    }

    @Test
    fun `custom action command carries the player action id`() {
        val bundle = android.os.Bundle().apply {
            putString(MediaBridgeContract.Key.REQUEST_ID, "req")
            putString(MediaBridgeContract.Key.COMMAND, MediaCommand.CUSTOM_ACTION.name)
            putString(MediaBridgeContract.Key.COMMAND_CUSTOM_ACTION, "com.player.LIKE")
        }
        val request = bundle.toMediaCommandRequest()
        assertNotNull(request)
        assertEquals(MediaCommand.CUSTOM_ACTION, request!!.command)
        assertEquals("com.player.LIKE", request.customAction)

        bundle.remove(MediaBridgeContract.Key.COMMAND_CUSTOM_ACTION)
        assertNull(bundle.toMediaCommandRequest())
        bundle.putString(
            MediaBridgeContract.Key.COMMAND_CUSTOM_ACTION,
            "x".repeat(MediaBridgeContract.MAX_CUSTOM_ACTION_LENGTH + 1),
        )
        assertNull(bundle.toMediaCommandRequest())
    }

    @Test
    fun `snapshot bundle lists custom actions in order`() {
        val bundle = MediaSnapshot(
            ownerPackage = "com.player",
            customActions = listOf(
                MediaCustomAction("LIKE", "Like", 0x7f010001, "com.player"),
                MediaCustomAction("SHUFFLE", "", 0x7f010002, "com.player"),
            ),
        ).toBundle()

        @Suppress("DEPRECATION")
        val actions = bundle.getParcelableArrayList<android.os.Bundle>(
            MediaBridgeContract.Key.CUSTOM_ACTIONS,
        )!!
        assertEquals(listOf("LIKE", "SHUFFLE"),
            actions.map { it.getString(MediaBridgeContract.Key.CUSTOM_ACTION_ID) })
        assertEquals("Like", actions[0].getString(MediaBridgeContract.Key.CUSTOM_ACTION_NAME))
        assertEquals(0x7f010002, actions[1].getInt(MediaBridgeContract.Key.CUSTOM_ACTION_ICON))
        assertEquals("com.player",
            actions[1].getString(MediaBridgeContract.Key.CUSTOM_ACTION_PACKAGE))
    }

    @Test
    fun `session custom actions skip entries without id or icon and are bounded`() {
        val state = android.media.session.PlaybackState.Builder()
            .addCustomAction("LIKE", "Like", 0x7f010001)
            .addCustomAction(
                android.media.session.PlaybackState.CustomAction.Builder("NO_ICON", "No icon", 1)
                    .build(),
            )
            .apply {
                (1..6).forEach { addCustomAction("EXTRA_$it", "Extra $it", 0x7f010010 + it) }
            }
            .build()

        val actions = state.bridgeCustomActions("com.player")

        assertEquals(MediaBridgeContract.MAX_CUSTOM_ACTIONS, actions.size)
        assertEquals(MediaCustomAction("LIKE", "Like", 0x7f010001, "com.player"), actions.first())
        assertEquals(emptyList<MediaCustomAction>(), state.bridgeCustomActions(""))
        assertEquals(emptyList<MediaCustomAction>(), null.bridgeCustomActions("com.player"))
    }
}
