package com.mmwtl.atlasmediawidget;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

final class MediaSourceLauncher {
    private static final String OEM_MEDIA_PACKAGE = "com.geely.mediawidget";

    private final Context context;

    MediaSourceLauncher(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean open(MediaSnapshot snapshot) {
        if (snapshot == null) return false;
        MediaSource.Id source = snapshot.audioSource.displayId();
        if (!canOpen(source)) return false;
        if (source == MediaSource.Id.ONLINE) {
            if (!snapshot.ownerPackage.isBlank() && launchPackage(snapshot.ownerPackage)) return true;
            return launchMusicSelector();
        }
        if (source == MediaSource.Id.BT || source == MediaSource.Id.RADIO
                || source == MediaSource.Id.USB) {
            if (launchPackage(OEM_MEDIA_PACKAGE)) return true;
            return launchMusicSelector();
        }
        return false;
    }

    static boolean canOpen(MediaSource.Id source) {
        MediaSource.Id display = source.displayId();
        return display == MediaSource.Id.BT || display == MediaSource.Id.RADIO
                || display == MediaSource.Id.USB || display == MediaSource.Id.ONLINE;
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
