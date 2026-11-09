package com.mmwtl.atlasmediawidget;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MediaSettingsSnapshot {
    final long revision;
    final String defaultAudioSource;
    final int defaultAudioSourceDelaySec;
    final boolean defaultAudioSourceAutoplayOnStartup;
    final boolean autoSwitchToDefaultOnSourceLost;
    final boolean autoSwitchToDefaultAutoplayOnSourceLost;
    final String defaultMediaPackage;
    final boolean minimizeOnlinePlayerAfterAutostart;
    final boolean radioWidgetBroadcastEnabled;
    final boolean clusterCoversEnabled;
    final boolean clusterOnlineEnabled;
    final boolean clusterOnlineProgressEnabled;
    final long clusterWatchdogIntervalMs;
    final long clusterReassertBurstIntervalMs;
    final String catalogType;
    final int catalogStationCount;
    final String catalogDescription;
    final int uiScaleTenths;
    static final int MIN_STARTUP_INTERNET_TIMEOUT_SEC = 10;
    static final int MAX_STARTUP_INTERNET_TIMEOUT_SEC = 300;
    static final int DEFAULT_STARTUP_INTERNET_TIMEOUT_SEC = 60;
    /** Startup waits for a validated internet connection; set only from a Bridge snapshot. */
    boolean startupWaitForInternet;
    int startupInternetTimeoutSec = DEFAULT_STARTUP_INTERNET_TIMEOUT_SEC;
    /** The active radio catalog and every catalog in order; set only from a Bridge snapshot. */
    String catalogActiveId = "";
    List<Catalog> catalogs = Collections.emptyList();

    static final class Catalog {
        final String id;
        final String name;
        final int stationCount;
        /** The pack this city belongs to; empty for a catalog imported on its own. */
        final String packId;
        final String packName;
        final String region;

        Catalog(String id, String name, int stationCount) {
            this(id, name, stationCount, "", "", "");
        }

        Catalog(String id, String name, int stationCount, String packId, String packName,
                String region) {
            this.id = id;
            this.name = name;
            this.stationCount = stationCount;
            this.packId = packId != null ? packId : "";
            this.packName = packName != null ? packName : "";
            this.region = region != null ? region : "";
        }

        boolean isInPack() {
            return !packId.isEmpty();
        }

        boolean isBuiltIn() {
            return MediaBridgeContract.BUILT_IN_CATALOG_ID.equals(id);
        }
    }

    Catalog activeCatalog() {
        for (Catalog catalog : catalogs) {
            if (catalog.id.equals(catalogActiveId)) return catalog;
        }
        return null;
    }

    MediaSettingsSnapshot(
            long revision,
            String defaultAudioSource,
            int defaultAudioSourceDelaySec,
            boolean defaultAudioSourceAutoplayOnStartup,
            boolean autoSwitchToDefaultOnSourceLost,
            boolean autoSwitchToDefaultAutoplayOnSourceLost,
            String defaultMediaPackage,
            boolean radioWidgetBroadcastEnabled,
            boolean clusterCoversEnabled,
            long clusterWatchdogIntervalMs,
            String catalogType,
            int catalogStationCount,
            String catalogDescription,
            int uiScaleTenths) {
        this(revision, defaultAudioSource, defaultAudioSourceDelaySec,
                defaultAudioSourceAutoplayOnStartup, autoSwitchToDefaultOnSourceLost,
                autoSwitchToDefaultAutoplayOnSourceLost, defaultMediaPackage,
                false, radioWidgetBroadcastEnabled,
                clusterCoversEnabled, false, false, clusterWatchdogIntervalMs, 100L, catalogType,
                catalogStationCount, catalogDescription, uiScaleTenths);
    }

    MediaSettingsSnapshot(
            long revision,
            String defaultAudioSource,
            int defaultAudioSourceDelaySec,
            boolean defaultAudioSourceAutoplayOnStartup,
            boolean autoSwitchToDefaultOnSourceLost,
            boolean autoSwitchToDefaultAutoplayOnSourceLost,
            String defaultMediaPackage,
            boolean radioWidgetBroadcastEnabled,
            boolean clusterCoversEnabled,
            boolean clusterOnlineEnabled,
            long clusterWatchdogIntervalMs,
            String catalogType,
            int catalogStationCount,
            String catalogDescription,
            int uiScaleTenths) {
        this(revision, defaultAudioSource, defaultAudioSourceDelaySec,
                defaultAudioSourceAutoplayOnStartup, autoSwitchToDefaultOnSourceLost,
                autoSwitchToDefaultAutoplayOnSourceLost, defaultMediaPackage,
                false, radioWidgetBroadcastEnabled,
                clusterCoversEnabled, clusterOnlineEnabled, false, clusterWatchdogIntervalMs, 100L,
                catalogType, catalogStationCount, catalogDescription, uiScaleTenths);
    }

    MediaSettingsSnapshot(
            long revision,
            String defaultAudioSource,
            int defaultAudioSourceDelaySec,
            boolean defaultAudioSourceAutoplayOnStartup,
            boolean autoSwitchToDefaultOnSourceLost,
            boolean autoSwitchToDefaultAutoplayOnSourceLost,
            String defaultMediaPackage,
            boolean minimizeOnlinePlayerAfterAutostart,
            boolean radioWidgetBroadcastEnabled,
            boolean clusterCoversEnabled,
            boolean clusterOnlineEnabled,
            boolean clusterOnlineProgressEnabled,
            long clusterWatchdogIntervalMs,
            long clusterReassertBurstIntervalMs,
            String catalogType,
            int catalogStationCount,
            String catalogDescription,
            int uiScaleTenths) {
        this.revision = revision;
        this.defaultAudioSource = defaultAudioSource != null ? defaultAudioSource : "";
        this.defaultAudioSourceDelaySec = defaultAudioSourceDelaySec;
        this.defaultAudioSourceAutoplayOnStartup = defaultAudioSourceAutoplayOnStartup;
        this.autoSwitchToDefaultOnSourceLost = autoSwitchToDefaultOnSourceLost;
        this.autoSwitchToDefaultAutoplayOnSourceLost = autoSwitchToDefaultAutoplayOnSourceLost;
        this.defaultMediaPackage = defaultMediaPackage != null ? defaultMediaPackage : "";
        this.minimizeOnlinePlayerAfterAutostart = minimizeOnlinePlayerAfterAutostart;
        this.radioWidgetBroadcastEnabled = radioWidgetBroadcastEnabled;
        this.clusterCoversEnabled = clusterCoversEnabled;
        this.clusterOnlineEnabled = clusterOnlineEnabled;
        this.clusterOnlineProgressEnabled = clusterOnlineProgressEnabled;
        this.clusterWatchdogIntervalMs = clusterWatchdogIntervalMs;
        this.clusterReassertBurstIntervalMs = clusterReassertBurstIntervalMs;
        this.catalogType = catalogType != null && !catalogType.isEmpty() ? catalogType : "BUILT_IN";
        this.catalogStationCount = catalogStationCount;
        this.catalogDescription = catalogDescription != null ? catalogDescription : "";
        this.uiScaleTenths = uiScaleTenths;
    }

    static MediaSettingsSnapshot fromBundle(Bundle bundle) {
        if (bundle == null) {
            return new MediaSettingsSnapshot(0L, "", 0, true, false, true, "", true, true, 1250L, "BUILT_IN", 0, "", 15);
        }
        MediaSettingsSnapshot snapshot = new MediaSettingsSnapshot(
                bundle.getLong(MediaBridgeContract.K_SETTINGS_REVISION, 0L),
                bundle.getString(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE, ""),
                bundle.getInt(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_DELAY_SEC, 0),
                bundle.getBoolean(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_AUTOPLAY, true),
                bundle.getBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT, false),
                bundle.getBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, true),
                bundle.getString(MediaBridgeContract.K_DEFAULT_MEDIA_PACKAGE, ""),
                bundle.getBoolean(MediaBridgeContract.K_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART, false),
                bundle.getBoolean(MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED, true),
                bundle.getBoolean(MediaBridgeContract.K_CLUSTER_COVERS_ENABLED, true),
                bundle.getBoolean(MediaBridgeContract.K_CLUSTER_ONLINE_ENABLED, false),
                bundle.getBoolean(MediaBridgeContract.K_CLUSTER_ONLINE_PROGRESS_ENABLED, false),
                bundle.getLong(MediaBridgeContract.K_CLUSTER_WATCHDOG_INTERVAL_MS, 1250L),
                bundle.getLong(MediaBridgeContract.K_CLUSTER_REASSERT_BURST_INTERVAL_MS, 100L),
                bundle.getString(MediaBridgeContract.K_CATALOG_TYPE, "BUILT_IN"),
                bundle.getInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, 0),
                bundle.getString(MediaBridgeContract.K_CATALOG_DESCRIPTION, ""),
                bundle.getInt(MediaBridgeContract.K_UI_SCALE_TENTHS, 15));
        snapshot.startupWaitForInternet =
                bundle.getBoolean(MediaBridgeContract.K_STARTUP_WAIT_FOR_INTERNET, false);
        snapshot.startupInternetTimeoutSec = bundle.getInt(
                MediaBridgeContract.K_STARTUP_INTERNET_TIMEOUT_SEC,
                DEFAULT_STARTUP_INTERNET_TIMEOUT_SEC);
        snapshot.catalogActiveId = bundle.getString(MediaBridgeContract.K_CATALOG_ACTIVE_ID, "");
        snapshot.catalogs = readCatalogs(bundle);
        return snapshot;
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putLong(MediaBridgeContract.K_SETTINGS_REVISION, revision);
        bundle.putString(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE, defaultAudioSource);
        bundle.putInt(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_DELAY_SEC, defaultAudioSourceDelaySec);
        bundle.putBoolean(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_AUTOPLAY, defaultAudioSourceAutoplayOnStartup);
        bundle.putBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT, autoSwitchToDefaultOnSourceLost);
        bundle.putBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, autoSwitchToDefaultAutoplayOnSourceLost);
        bundle.putString(MediaBridgeContract.K_DEFAULT_MEDIA_PACKAGE, defaultMediaPackage);
        bundle.putBoolean(MediaBridgeContract.K_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART,
                minimizeOnlinePlayerAfterAutostart);
        bundle.putBoolean(MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED, radioWidgetBroadcastEnabled);
        bundle.putBoolean(MediaBridgeContract.K_CLUSTER_COVERS_ENABLED, clusterCoversEnabled);
        bundle.putBoolean(MediaBridgeContract.K_CLUSTER_ONLINE_ENABLED, clusterOnlineEnabled);
        bundle.putBoolean(MediaBridgeContract.K_CLUSTER_ONLINE_PROGRESS_ENABLED, clusterOnlineProgressEnabled);
        bundle.putLong(MediaBridgeContract.K_CLUSTER_WATCHDOG_INTERVAL_MS, clusterWatchdogIntervalMs);
        bundle.putLong(MediaBridgeContract.K_CLUSTER_REASSERT_BURST_INTERVAL_MS, clusterReassertBurstIntervalMs);
        bundle.putString(MediaBridgeContract.K_CATALOG_TYPE, catalogType);
        bundle.putInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, catalogStationCount);
        bundle.putString(MediaBridgeContract.K_CATALOG_DESCRIPTION, catalogDescription);
        bundle.putInt(MediaBridgeContract.K_UI_SCALE_TENTHS, uiScaleTenths);
        bundle.putBoolean(MediaBridgeContract.K_STARTUP_WAIT_FOR_INTERNET, startupWaitForInternet);
        bundle.putInt(MediaBridgeContract.K_STARTUP_INTERNET_TIMEOUT_SEC, startupInternetTimeoutSec);
        bundle.putString(MediaBridgeContract.K_CATALOG_ACTIVE_ID, catalogActiveId);
        ArrayList<String> ids = new ArrayList<>(catalogs.size());
        ArrayList<String> names = new ArrayList<>(catalogs.size());
        int[] counts = new int[catalogs.size()];
        for (int i = 0; i < catalogs.size(); i++) {
            ids.add(catalogs.get(i).id);
            names.add(catalogs.get(i).name);
            counts[i] = catalogs.get(i).stationCount;
        }
        bundle.putStringArrayList(MediaBridgeContract.K_CATALOG_IDS, ids);
        bundle.putStringArrayList(MediaBridgeContract.K_CATALOG_NAMES, names);
        bundle.putIntArray(MediaBridgeContract.K_CATALOG_STATION_COUNTS, counts);
        ArrayList<String> packIds = new ArrayList<>(catalogs.size());
        ArrayList<String> packNames = new ArrayList<>(catalogs.size());
        ArrayList<String> regions = new ArrayList<>(catalogs.size());
        for (Catalog catalog : catalogs) {
            packIds.add(catalog.packId);
            packNames.add(catalog.packName);
            regions.add(catalog.region);
        }
        bundle.putStringArrayList(MediaBridgeContract.K_CATALOG_PACK_IDS, packIds);
        bundle.putStringArrayList(MediaBridgeContract.K_CATALOG_PACK_NAMES, packNames);
        bundle.putStringArrayList(MediaBridgeContract.K_CATALOG_REGIONS, regions);
        return bundle;
    }

    private static ArrayList<String> optionalColumn(Bundle bundle, String key, int size) {
        ArrayList<String> column = bundle.getStringArrayList(key);
        return column != null && column.size() == size ? column : null;
    }

    private static List<Catalog> readCatalogs(Bundle bundle) {
        ArrayList<String> ids = bundle.getStringArrayList(MediaBridgeContract.K_CATALOG_IDS);
        ArrayList<String> names = bundle.getStringArrayList(MediaBridgeContract.K_CATALOG_NAMES);
        int[] counts = bundle.getIntArray(MediaBridgeContract.K_CATALOG_STATION_COUNTS);
        if (ids == null || names == null || counts == null || ids.size() != names.size()
                || ids.size() != counts.length) {
            return Collections.emptyList();
        }
        ArrayList<String> packIds = optionalColumn(bundle, MediaBridgeContract.K_CATALOG_PACK_IDS, ids.size());
        ArrayList<String> packNames = optionalColumn(bundle, MediaBridgeContract.K_CATALOG_PACK_NAMES, ids.size());
        ArrayList<String> regions = optionalColumn(bundle, MediaBridgeContract.K_CATALOG_REGIONS, ids.size());
        ArrayList<Catalog> result = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isEmpty()) continue;
            String name = names.get(i);
            result.add(new Catalog(id, name != null ? name : "", counts[i],
                    packIds == null ? "" : packIds.get(i),
                    packNames == null ? "" : packNames.get(i),
                    regions == null ? "" : regions.get(i)));
        }
        return Collections.unmodifiableList(result);
    }
}
