package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Names and icons of player apps for the source tiles. The card redraws on every snapshot, so the
 * package manager is read once per package; a reinstall or a new app updates on the next process.
 */
final class PlayerApps {
    record Info(boolean installed, String label, Drawable.ConstantState icon) {
        /** A fresh drawable: one instance cannot sit in several views. */
        Drawable newIcon() { return icon == null ? null : icon.newDrawable().mutate(); }
    }

    private static final Map<String, Info> CACHE = new HashMap<>();

    private PlayerApps() {}

    static synchronized Info get(Context context, String packageName) {
        Info cached = CACHE.get(packageName);
        if (cached != null && cached.installed()) return cached;
        Info info = load(context.getApplicationContext().getPackageManager(), packageName);
        CACHE.put(packageName, info);
        return info;
    }

    private static Info load(PackageManager packageManager, String packageName) {
        try {
            ApplicationInfo app = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(app);
            Drawable icon = packageManager.getApplicationIcon(app);
            return new Info(true, label == null || label.toString().isBlank()
                    ? packageName : label.toString(),
                    icon == null ? null : icon.getConstantState());
        } catch (PackageManager.NameNotFoundException | RuntimeException missing) {
            return new Info(false, packageName, null);
        }
    }
}
