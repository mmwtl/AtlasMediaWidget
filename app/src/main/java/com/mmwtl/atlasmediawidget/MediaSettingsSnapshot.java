package com.mmwtl.atlasmediawidget;

import android.os.Bundle;

final class MediaSettingsSnapshot {
    final long revision;
    final String defaultAudioSource;
    final int defaultAudioSourceDelaySec;
    final boolean defaultAudioSourceAutoplayOnStartup;
    final boolean autoSwitchToDefaultOnSourceLost;
    final boolean autoSwitchToDefaultAutoplayOnSourceLost;
    final String defaultMediaPackage;
    final boolean switchToOnlineBeforeSessionPlay;
    final boolean radioWidgetBroadcastEnabled;
    final boolean clusterCoversEnabled;
    final long clusterWatchdogIntervalMs;
    final String catalogType;
    final int catalogStationCount;
    final String catalogDescription;
    final int uiScaleTenths;

    MediaSettingsSnapshot(
            long revision,
            String defaultAudioSource,
            int defaultAudioSourceDelaySec,
            boolean defaultAudioSourceAutoplayOnStartup,
            boolean autoSwitchToDefaultOnSourceLost,
            boolean autoSwitchToDefaultAutoplayOnSourceLost,
            String defaultMediaPackage,
            boolean switchToOnlineBeforeSessionPlay,
            boolean radioWidgetBroadcastEnabled,
            boolean clusterCoversEnabled,
            long clusterWatchdogIntervalMs,
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
        this.switchToOnlineBeforeSessionPlay = switchToOnlineBeforeSessionPlay;
        this.radioWidgetBroadcastEnabled = radioWidgetBroadcastEnabled;
        this.clusterCoversEnabled = clusterCoversEnabled;
        this.clusterWatchdogIntervalMs = clusterWatchdogIntervalMs;
        this.catalogType = catalogType != null && !catalogType.isEmpty() ? catalogType : "BUILT_IN";
        this.catalogStationCount = catalogStationCount;
        this.catalogDescription = catalogDescription != null ? catalogDescription : "";
        this.uiScaleTenths = uiScaleTenths;
    }

    static MediaSettingsSnapshot fromBundle(Bundle bundle) {
        if (bundle == null) {
            return new MediaSettingsSnapshot(0L, "", 0, true, false, true, "", false, true, true, 1250L, "BUILT_IN", 0, "", 15);
        }
        return new MediaSettingsSnapshot(
                bundle.getLong(MediaBridgeContract.K_SETTINGS_REVISION, 0L),
                bundle.getString(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE, ""),
                bundle.getInt(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_DELAY_SEC, 0),
                bundle.getBoolean(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_AUTOPLAY, true),
                bundle.getBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT, false),
                bundle.getBoolean(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, true),
                bundle.getString(MediaBridgeContract.K_DEFAULT_MEDIA_PACKAGE, ""),
                bundle.getBoolean(MediaBridgeContract.K_SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY, false),
                bundle.getBoolean(MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED, true),
                bundle.getBoolean(MediaBridgeContract.K_CLUSTER_COVERS_ENABLED, true),
                bundle.getLong(MediaBridgeContract.K_CLUSTER_WATCHDOG_INTERVAL_MS, 1250L),
                bundle.getString(MediaBridgeContract.K_CATALOG_TYPE, "BUILT_IN"),
                bundle.getInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, 0),
                bundle.getString(MediaBridgeContract.K_CATALOG_DESCRIPTION, ""),
                bundle.getInt(MediaBridgeContract.K_UI_SCALE_TENTHS, 15));
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
        bundle.putBoolean(MediaBridgeContract.K_SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY, switchToOnlineBeforeSessionPlay);
        bundle.putBoolean(MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED, radioWidgetBroadcastEnabled);
        bundle.putBoolean(MediaBridgeContract.K_CLUSTER_COVERS_ENABLED, clusterCoversEnabled);
        bundle.putLong(MediaBridgeContract.K_CLUSTER_WATCHDOG_INTERVAL_MS, clusterWatchdogIntervalMs);
        bundle.putString(MediaBridgeContract.K_CATALOG_TYPE, catalogType);
        bundle.putInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, catalogStationCount);
        bundle.putString(MediaBridgeContract.K_CATALOG_DESCRIPTION, catalogDescription);
        bundle.putInt(MediaBridgeContract.K_UI_SCALE_TENTHS, uiScaleTenths);
        return bundle;
    }
}
