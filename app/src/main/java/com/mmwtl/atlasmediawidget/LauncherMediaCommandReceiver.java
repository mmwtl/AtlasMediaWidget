package com.mmwtl.atlasmediawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Transport commands from AtlasLauncher gestures. Only play/pause, next and previous are
 * accepted, which any app can already send as a media key, so the receiver has no permission.
 * A command runs only on the already running OverlayService and its connected Bridge client;
 * otherwise it is dropped without starting or binding anything. For an ordered broadcast the
 * result data names the command actually sent (PLAY, PAUSE, NEXT or PREVIOUS); a dropped command
 * leaves it null.
 */
public final class LauncherMediaCommandReceiver extends BroadcastReceiver {
    static final String ACTION_MEDIA_COMMAND = "com.mmwtl.atlasmediawidget.action.MEDIA_COMMAND";
    static final String EXTRA_COMMAND = "command";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_MEDIA_COMMAND.equals(intent.getAction())) return;
        String command = command(intent.getStringExtra(EXTRA_COMMAND));
        if (command == null) return;
        OverlayService service = OverlayService.current();
        if (service == null) {
            AppLog.info("Launcher media command " + command + " ignored: service not running");
            return;
        }
        String sent = service.executeLauncherCommand(command);
        if (sent != null && isOrderedBroadcast()) setResultData(sent);
    }

    /** The command reported to the launcher; TOGGLE becomes PAUSE while playing, else PLAY. */
    static String reported(String transport, boolean playing) {
        if ("TOGGLE".equals(transport)) return playing ? "PAUSE" : "PLAY";
        return transport;
    }

    /** Returns the accepted command or null for anything outside the contract. */
    static String command(String value) {
        if (value == null) return null;
        return switch (value) {
            case "PLAY_PAUSE", "NEXT", "PREVIOUS" -> value;
            default -> null;
        };
    }
}
