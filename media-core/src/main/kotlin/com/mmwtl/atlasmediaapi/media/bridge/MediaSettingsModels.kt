package com.mmwtl.atlasmediaapi.media.bridge

import android.os.Bundle

/**
 * Immutable snapshot of media backend settings.
 */
data class MediaSettingsSnapshot(
    val revision: Long = 0L,
    val defaultAudioSource: String = "",
    val defaultAudioSourceDelaySec: Int = 0,
    val defaultAudioSourceAutoplayOnStartup: Boolean = true,
    val autoSwitchToDefaultOnSourceLost: Boolean = false,
    val autoSwitchToDefaultAutoplayOnSourceLost: Boolean = true,
    val defaultMediaPackage: String = "",
    val minimizeOnlinePlayerAfterAutostart: Boolean = false,
    val radioWidgetBroadcastEnabled: Boolean = true,
    val clusterCoversEnabled: Boolean = true,
    val clusterOnlineEnabled: Boolean = false,
    val clusterOnlineProgressEnabled: Boolean = false,
    val clusterWatchdogIntervalMs: Long = 1250L,
    val clusterReassertBurstIntervalMs: Long = 100L,
    val catalogType: String = "BUILT_IN",
    val catalogStationCount: Int = 0,
    val catalogDescription: String = "",
    val uiScaleTenths: Int = 15,
)

fun MediaSettingsSnapshot.toBundle(): Bundle = Bundle().apply {
    putLong(MediaBridgeContract.Key.SETTINGS_REVISION, revision)
    putString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE, defaultAudioSource)
    putInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC, defaultAudioSourceDelaySec)
    putBoolean(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY, defaultAudioSourceAutoplayOnStartup)
    putBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT, autoSwitchToDefaultOnSourceLost)
    putBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, autoSwitchToDefaultAutoplayOnSourceLost)
    putString(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE, defaultMediaPackage)
    putBoolean(
        MediaBridgeContract.Key.MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART,
        minimizeOnlinePlayerAfterAutostart,
    )
    putBoolean(MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED, radioWidgetBroadcastEnabled)
    putBoolean(MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED, clusterCoversEnabled)
    putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED, clusterOnlineEnabled)
    putBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_PROGRESS_ENABLED, clusterOnlineProgressEnabled)
    putLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, clusterWatchdogIntervalMs)
    putLong(MediaBridgeContract.Key.CLUSTER_REASSERT_BURST_INTERVAL_MS, clusterReassertBurstIntervalMs)
    putString(MediaBridgeContract.Key.CATALOG_TYPE, catalogType)
    putInt(MediaBridgeContract.Key.CATALOG_STATION_COUNT, catalogStationCount)
    putString(MediaBridgeContract.Key.CATALOG_DESCRIPTION, catalogDescription)
    putInt(MediaBridgeContract.Key.UI_SCALE_TENTHS, uiScaleTenths)
}

fun Bundle.toMediaSettingsSnapshot(): MediaSettingsSnapshot = MediaSettingsSnapshot(
    revision = getLong(MediaBridgeContract.Key.SETTINGS_REVISION, 0L),
    defaultAudioSource = getString(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE).orEmpty(),
    defaultAudioSourceDelaySec = getInt(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_DELAY_SEC, 0),
    defaultAudioSourceAutoplayOnStartup = getBoolean(MediaBridgeContract.Key.DEFAULT_AUDIO_SOURCE_AUTOPLAY, true),
    autoSwitchToDefaultOnSourceLost = getBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT, false),
    autoSwitchToDefaultAutoplayOnSourceLost = getBoolean(MediaBridgeContract.Key.AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, true),
    defaultMediaPackage = getString(MediaBridgeContract.Key.DEFAULT_MEDIA_PACKAGE).orEmpty(),
    minimizeOnlinePlayerAfterAutostart = getBoolean(
        MediaBridgeContract.Key.MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART,
        false,
    ),
    radioWidgetBroadcastEnabled = getBoolean(MediaBridgeContract.Key.RADIO_WIDGET_BROADCAST_ENABLED, true),
    clusterCoversEnabled = getBoolean(MediaBridgeContract.Key.CLUSTER_COVERS_ENABLED, true),
    clusterOnlineEnabled = getBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_ENABLED, false),
    clusterOnlineProgressEnabled = getBoolean(MediaBridgeContract.Key.CLUSTER_ONLINE_PROGRESS_ENABLED, false),
    clusterWatchdogIntervalMs = getLong(MediaBridgeContract.Key.CLUSTER_WATCHDOG_INTERVAL_MS, 1250L),
    clusterReassertBurstIntervalMs = getLong(MediaBridgeContract.Key.CLUSTER_REASSERT_BURST_INTERVAL_MS, 100L),
    catalogType = getString(MediaBridgeContract.Key.CATALOG_TYPE).orEmpty().ifBlank { "BUILT_IN" },
    catalogStationCount = getInt(MediaBridgeContract.Key.CATALOG_STATION_COUNT, 0),
    catalogDescription = getString(MediaBridgeContract.Key.CATALOG_DESCRIPTION).orEmpty(),
    uiScaleTenths = getInt(MediaBridgeContract.Key.UI_SCALE_TENTHS, 15),
)
