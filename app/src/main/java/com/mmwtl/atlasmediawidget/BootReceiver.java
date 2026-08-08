package com.mmwtl.atlasmediawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Prefs prefs = new Prefs(context);
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (!prefs.getBoolean(Prefs.KEY_AUTO_START, false)) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                return;
            }
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
            AppLog.info("Boot completed; starting overlay service without delay");
            startIfAllowed(context, prefs);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            startIfAllowed(context, prefs);
        }
    }

    static void startIfAllowed(Context context, Prefs prefs) {
        if (!Settings.canDrawOverlays(context)) {
            AppLog.info("Boot start skipped: overlay permission is missing");
            return;
        }
        if (!ForegroundAppDetector.hasUsageAccess(context)) {
            AppLog.info("Boot start skipped: usage access is missing");
            return;
        }
        try {
            OverlayService.start(context);
        } catch (RuntimeException error) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            AppLog.warn("Boot start failed", error);
        }
    }
}
