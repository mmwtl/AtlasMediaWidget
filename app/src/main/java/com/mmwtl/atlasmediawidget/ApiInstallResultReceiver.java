package com.mmwtl.atlasmediawidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public final class ApiInstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = confirmationIntent(intent);
            if (confirmation == null) {
                Toast.makeText(context, "Не удалось открыть подтверждение установки API",
                        Toast.LENGTH_LONG).show();
                return;
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(confirmation);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot open API installation confirmation", error);
                Toast.makeText(context, "Не удалось открыть установщик Atlas Media API",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Atlas Media API установлен", Toast.LENGTH_LONG).show();
            Intent widget = new Intent(context, MainActivity.class)
                    .setAction(EmbeddedApiInstaller.ACTION_INSTALL_RESULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                context.startActivity(widget);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot return to Widget after API installation", error);
            }
            return;
        }

        String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        AppLog.warn("Atlas Media API installation failed: status=" + status
                + ", detail=" + detail, null);
        Toast.makeText(context, "Не удалось установить Atlas Media API"
                + (detail == null || detail.isBlank() ? "" : ": " + detail),
                Toast.LENGTH_LONG).show();
    }

    @SuppressWarnings("deprecation")
    private static Intent confirmationIntent(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        return source.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
