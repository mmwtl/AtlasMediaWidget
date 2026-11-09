package com.mmwtl.atlasmediawidget;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class MediaBridgeContractTest {
    @Test public void protocolV1ConstantsMatchAtlasMediaApi() {
        assertEquals("com.mmwtl.atlasmediaapi.media.BIND", MediaBridgeContract.SERVICE_ACTION);
        assertEquals("com.mmwtl.atlasmediawidget",
                MediaBridgeContract.SERVICE_PACKAGE);
        assertEquals("com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeService",
                MediaBridgeContract.SERVICE_CLASS);
        assertEquals(1, MediaBridgeContract.VERSION);
        assertEquals(1, MediaBridgeContract.REGISTER);
        assertEquals(4, MediaBridgeContract.COMMAND);
        assertEquals(5, MediaBridgeContract.GET_RADIO_STATIONS);
        assertEquals(100, MediaBridgeContract.REGISTERED);
        assertEquals(103, MediaBridgeContract.ERROR);
        assertEquals(104, MediaBridgeContract.RADIO_STATIONS);
        assertEquals(0x40L, MediaBridgeContract.CAP_SET_SOURCE);
        assertEquals(0x80L, MediaBridgeContract.CAP_TUNE_RADIO);
        assertEquals("radioFavoriteStations",
                MediaBridgeContract.K_RADIO_FAVORITE_STATIONS);
        assertEquals("uiScaleTenths", MediaBridgeContract.K_UI_SCALE_TENTHS);
        assertEquals(6, MediaBridgeContract.GET_SETTINGS);
        assertEquals(7, MediaBridgeContract.UPDATE_SETTINGS);
        assertEquals(8, MediaBridgeContract.EXPORT_MEDIA_BACKUP);
        assertEquals(9, MediaBridgeContract.PREPARE_MEDIA_IMPORT);
        assertEquals(10, MediaBridgeContract.COMMIT_MEDIA_IMPORT);
        assertEquals(11, MediaBridgeContract.GET_IMPORT_STATUS);
        assertEquals(12, MediaBridgeContract.ABORT_MEDIA_IMPORT);
        assertEquals(13, MediaBridgeContract.RESTORE_DEFAULT_CATALOG);
        assertEquals(105, MediaBridgeContract.SETTINGS);
        assertEquals(106, MediaBridgeContract.SETTINGS_UPDATED);
        assertEquals(107, MediaBridgeContract.MEDIA_BACKUP_EXPORTED);
        assertEquals(108, MediaBridgeContract.MEDIA_IMPORT_PREPARED);
        assertEquals(109, MediaBridgeContract.MEDIA_IMPORT_COMMITTED);
        assertEquals(110, MediaBridgeContract.MEDIA_IMPORT_STATUS);
        assertEquals(111, MediaBridgeContract.MEDIA_IMPORT_ABORTED);
        assertEquals(112, MediaBridgeContract.DEFAULT_CATALOG_RESTORED);
        assertEquals(9, MediaBridgeContract.STATUS_VALIDATION_ERROR);
        assertEquals(10, MediaBridgeContract.STATUS_CONFLICT);
        assertEquals(11, MediaBridgeContract.STATUS_IO_ERROR);
        assertEquals("settingsRevision", MediaBridgeContract.K_SETTINGS_REVISION);
        assertEquals("defaultAudioSource", MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE);
    }

    @Test public void radioTransferConstantsMatchRuntimeContract() {
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ClientMessage.EXPORT_RADIO_CATALOG,
                MediaBridgeContract.EXPORT_RADIO_CATALOG);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ClientMessage.IMPORT_RADIO_CATALOG,
                MediaBridgeContract.IMPORT_RADIO_CATALOG);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ServerMessage.RADIO_CATALOG_EXPORTED,
                MediaBridgeContract.RADIO_CATALOG_EXPORTED);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ServerMessage.RADIO_CATALOG_IMPORTED,
                MediaBridgeContract.RADIO_CATALOG_IMPORTED);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.FILE_DESCRIPTOR,
                MediaBridgeContract.K_FILE_DESCRIPTOR);
    }

    @Test public void radioCatalogCollectionConstantsMatchRuntimeContract() {
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ClientMessage.MANAGE_RADIO_CATALOG,
                MediaBridgeContract.MANAGE_RADIO_CATALOG);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.ServerMessage.RADIO_CATALOG_MANAGED,
                MediaBridgeContract.RADIO_CATALOG_MANAGED);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_ACTIVE_ID,
                MediaBridgeContract.K_CATALOG_ACTIVE_ID);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_IDS,
                MediaBridgeContract.K_CATALOG_IDS);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_NAMES,
                MediaBridgeContract.K_CATALOG_NAMES);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_STATION_COUNTS,
                MediaBridgeContract.K_CATALOG_STATION_COUNTS);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_ID,
                MediaBridgeContract.K_CATALOG_ID);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_NAME,
                MediaBridgeContract.K_CATALOG_NAME);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_ACTION,
                MediaBridgeContract.K_CATALOG_ACTION);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.CatalogAction.ACTIVATE,
                MediaBridgeContract.CATALOG_ACTION_ACTIVATE);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.CatalogAction.RENAME,
                MediaBridgeContract.CATALOG_ACTION_RENAME);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.CatalogAction.DELETE,
                MediaBridgeContract.CATALOG_ACTION_DELETE);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.CatalogAction.DELETE_PACK,
                MediaBridgeContract.CATALOG_ACTION_DELETE_PACK);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_PACK_IDS,
                MediaBridgeContract.K_CATALOG_PACK_IDS);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_PACK_NAMES,
                MediaBridgeContract.K_CATALOG_PACK_NAMES);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_REGIONS,
                MediaBridgeContract.K_CATALOG_REGIONS);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_PACK_ID,
                MediaBridgeContract.K_CATALOG_PACK_ID);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_PACK_NAME,
                MediaBridgeContract.K_CATALOG_PACK_NAME);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract.Key.CATALOG_COUNT,
                MediaBridgeContract.K_CATALOG_COUNT);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogRepository.PACK_MANIFEST_NAME,
                RadioPackPreview.MANIFEST);
        assertEquals(com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogRepository.BUILT_IN_ID,
                MediaBridgeContract.BUILT_IN_CATALOG_ID);
    }

    @Test public void runtimeCatalogListReachesTheWidgetSnapshot() {
        android.os.Bundle bundle = com.mmwtl.atlasmediaapi.media.bridge.MediaSettingsModelsKt.toBundle(
                new com.mmwtl.atlasmediaapi.media.bridge.MediaSettingsSnapshot().copy(
                        0L, "", 0, true, false, true, "", false, false, 60, true, true, false,
                        false, 1250L, 100L, "CUSTOM", 3, "", 15, "abc",
                        java.util.List.of(
                                new com.mmwtl.atlasmediaapi.media.bridge.MediaSettingsCatalog(
                                        "builtin", "Встроенный (Пенза)", 25, "", "", ""),
                                new com.mmwtl.atlasmediaapi.media.bridge.MediaSettingsCatalog(
                                        "abc", "Москва", 3, "russia", "Россия",
                                        "Москва и область"))));
        MediaSettingsSnapshot snapshot = MediaSettingsSnapshot.fromBundle(bundle);
        assertEquals("abc", snapshot.catalogActiveId);
        assertEquals(2, snapshot.catalogs.size());
        assertEquals("Москва", snapshot.activeCatalog().name);
        assertEquals(3, snapshot.activeCatalog().stationCount);
        assertTrue(snapshot.catalogs.get(0).isBuiltIn());

        MediaSettingsSnapshot restored = MediaSettingsSnapshot.fromBundle(snapshot.toBundle());
        assertEquals("abc", restored.catalogActiveId);
        assertEquals("Встроенный (Пенза)", restored.catalogs.get(0).name);
        assertTrue(snapshot.activeCatalog().isInPack());
        assertEquals("Россия", restored.activeCatalog().packName);
        assertEquals("Москва и область", restored.activeCatalog().region);
        assertEquals("", restored.catalogs.get(0).packId);
    }

    @Test public void mediaSettingsSnapshotRoundTrip() {
        MediaSettingsSnapshot original = new MediaSettingsSnapshot(
                123L, "RADIO", 4, true, true, false, "com.test.player",
                true, false, true, true, true, 2000L, 200L,
                "CUSTOM", 15, "Test Catalog", 18);
        MediaSettingsSnapshot restored = MediaSettingsSnapshot.fromBundle(original.toBundle());
        assertEquals(original.revision, restored.revision);
        assertEquals(original.defaultAudioSource, restored.defaultAudioSource);
        assertEquals(original.defaultAudioSourceDelaySec, restored.defaultAudioSourceDelaySec);
        assertEquals(original.defaultAudioSourceAutoplayOnStartup, restored.defaultAudioSourceAutoplayOnStartup);
        assertEquals(original.autoSwitchToDefaultOnSourceLost, restored.autoSwitchToDefaultOnSourceLost);
        assertEquals(original.autoSwitchToDefaultAutoplayOnSourceLost, restored.autoSwitchToDefaultAutoplayOnSourceLost);
        assertEquals(original.defaultMediaPackage, restored.defaultMediaPackage);
        assertEquals(original.minimizeOnlinePlayerAfterAutostart,
                restored.minimizeOnlinePlayerAfterAutostart);
        assertEquals(original.radioWidgetBroadcastEnabled, restored.radioWidgetBroadcastEnabled);
        assertEquals(original.clusterCoversEnabled, restored.clusterCoversEnabled);
        assertEquals(original.clusterOnlineEnabled, restored.clusterOnlineEnabled);
        assertEquals(original.clusterOnlineProgressEnabled, restored.clusterOnlineProgressEnabled);
        assertEquals(original.clusterWatchdogIntervalMs, restored.clusterWatchdogIntervalMs);
        assertEquals(original.clusterReassertBurstIntervalMs, restored.clusterReassertBurstIntervalMs);
        assertEquals(original.catalogType, restored.catalogType);
        assertEquals(original.catalogStationCount, restored.catalogStationCount);
        assertEquals(original.catalogDescription, restored.catalogDescription);
        assertEquals(original.uiScaleTenths, restored.uiScaleTenths);
    }
}
