package com.mmwtl.atlasmediawidget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

final class MediaSourceLauncher {
    private static final String OEM_MEDIA_PACKAGE = "com.tencent.wecarflow";
    private static final String CARPLAY_PACKAGE = "com.autolink.carplay.app";
    private static final String OEM_JUMP_VIEW = "jumpView";

    private final Context context;

    MediaSourceLauncher(Context context) {
        this.context = context.getApplicationContext();
    }

    interface OpenCallback {
        void onOpened(boolean opened);
    }

    /**
     * Opens the source. Online without a session owner first asks the bridge for the configured
     * player and falls back to the system music chooser only when none is configured.
     */
    void open(MediaSnapshot snapshot, MediaBridgeClient bridge, OpenCallback callback) {
        if (bridge == null || !needsConfiguredPlayer(snapshot)) {
            callback.onOpened(open(snapshot, ""));
            return;
        }
        bridge.getSettings(new MediaBridgeClient.SettingsCallback() {
            @Override public void onSettings(MediaSettingsSnapshot settings) {
                callback.onOpened(open(snapshot, settings.defaultMediaPackage));
            }

            @Override public void onError(int status, String message) {
                AppLog.warn("Cannot read the configured Online player: " + message, null);
                callback.onOpened(open(snapshot, ""));
            }
        });
    }

    static boolean needsConfiguredPlayer(MediaSnapshot snapshot) {
        return snapshot != null && snapshot.audioSource.displayId() == MediaSource.Id.ONLINE
                && snapshot.ownerPackage.isBlank();
    }

    boolean open(MediaSnapshot snapshot, String configuredOnlinePackage) {
        if (snapshot == null) return false;
        MediaSource.Id source = snapshot.audioSource.displayId();
        if (!canOpen(source)) return false;
        if (source == MediaSource.Id.ONLINE) {
            if (!snapshot.ownerPackage.isBlank() && launchPackage(snapshot.ownerPackage)) return true;
            if (!configuredOnlinePackage.isBlank() && launchPackage(configuredOnlinePackage)) {
                return true;
            }
            return launchMusicSelector();
        }
        if (source == MediaSource.Id.CPAA) {
            if (!snapshot.ownerPackage.isBlank() && launchPackage(snapshot.ownerPackage)) return true;
            return launchPackage(CARPLAY_PACKAGE);
        }
        if (source == MediaSource.Id.BT || source == MediaSource.Id.RADIO
                || source == MediaSource.Id.USB) {
            if (launchOemMedia(source)) return true;
            return launchMusicSelector();
        }
        return false;
    }

    static boolean canOpen(MediaSource.Id source) {
        MediaSource.Id display = source.displayId();
        return display == MediaSource.Id.BT || display == MediaSource.Id.RADIO
                || display == MediaSource.Id.USB || display == MediaSource.Id.ONLINE
                || display == MediaSource.Id.CPAA;
    }

    static String oemJumpView(MediaSource.Id source) {
        return switch (source.displayId()) {
            case RADIO -> "localradio";
            case BT -> "bluetooth";
            case USB -> "usb";
            default -> "";
        };
    }

    private boolean launchMusicSelector() {
        Intent music = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC);
        music.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch(music);
    }

    private boolean launchPackage(String packageName) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return launch(intent);
    }

    private boolean launchOemMedia(MediaSource.Id source) {
        String jumpView = oemJumpView(source);
        if (jumpView.isBlank()) return false;
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(OEM_MEDIA_PACKAGE);
        if (intent == null) return false;
        intent.putExtra(OEM_JUMP_VIEW, jumpView);
        // Matches SourceBigWidgetProvider -> MediaViewModel.jumpToApp() on the tested firmware.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return launch(intent);
    }

    private boolean launch(Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Cannot open media source", error);
            return false;
        }
    }
}
