package com.mmwtl.atlasmediawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DelayedBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !BootReceiver.ACTION_DELAYED_START.equals(intent.getAction())) return;
        Prefs prefs = new Prefs(context);
        if (prefs.getBoolean(Prefs.KEY_AUTO_START, false)
                && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            BootReceiver.startIfAllowed(context, prefs);
        }
    }
}
