package com.mmwtl.atlasmediawidget;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    static final String ACTION_DELAYED_START =
            "com.mmwtl.atlasmediawidget.action.DELAYED_BOOT_START";
    private static final int REQUEST_CODE = 2408;

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
            schedule(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            startIfAllowed(context, prefs);
        }
    }

    @SuppressLint("MissingPermission")
    private void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE,
                new Intent(context, DelayedBootReceiver.class).setAction(ACTION_DELAYED_START),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + Prefs.BOOT_DELAY_SECONDS * 1000L, pending);
    }

    static void startIfAllowed(Context context, Prefs prefs) {
        if (!Settings.canDrawOverlays(context)
                || !ForegroundAppDetector.hasUsageAccess(context)) return;
        try {
            OverlayService.start(context);
        } catch (RuntimeException error) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            AppLog.warn("Boot start failed", error);
        }
    }
}
