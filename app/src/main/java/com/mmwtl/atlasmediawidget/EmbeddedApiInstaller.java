package com.mmwtl.atlasmediawidget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

final class EmbeddedApiInstaller {
    static final String ASSET_NAME = "atlas-media-api.apk";
    static final String ACTION_INSTALL_RESULT =
            "com.mmwtl.atlasmediawidget.action.API_INSTALL_RESULT";

    interface Callback {
        void onCommitted();
        void onError(Exception error);
    }

    private EmbeddedApiInstaller() {}

    static boolean isAvailable(Context context) {
        try (InputStream ignored = context.getAssets().open(ASSET_NAME)) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    static void install(Context context, Executor executor, Handler callbackHandler,
            Callback callback) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(MediaBridgeContract.SERVICE_PACKAGE);
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream input = appContext.getAssets().open(ASSET_NAME);
                         OutputStream output = session.openWrite("base.apk", 0, -1)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                        session.fsync(output);
                    }

                    Intent result = new Intent(appContext, ApiInstallResultReceiver.class)
                            .setAction(ACTION_INSTALL_RESULT);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                    PendingIntent pending = PendingIntent.getBroadcast(
                            appContext, sessionId, result, flags);
                    session.commit(pending.getIntentSender());
                }
                callbackHandler.post(callback::onCommitted);
            } catch (Exception error) {
                if (sessionId >= 0) {
                    try {
                        installer.abandonSession(sessionId);
                    } catch (RuntimeException abandonError) {
                        error.addSuppressed(abandonError);
                    }
                }
                callbackHandler.post(() -> callback.onError(error));
            }
        });
    }
}
