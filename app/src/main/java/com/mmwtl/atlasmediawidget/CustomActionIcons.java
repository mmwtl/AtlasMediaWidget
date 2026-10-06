package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/** Loads a custom action's icon from the publishing player's own resources. */
final class CustomActionIcons {
    private static final int CACHE_ENTRIES = 32;
    private static final Drawable.ConstantState MISSING = new Drawable.ConstantState() {
        @Override public Drawable newDrawable() { return null; }
        @Override public int getChangingConfigurations() { return 0; }
    };
    private static final LruCache<String, Drawable.ConstantState> CACHE =
            new LruCache<>(CACHE_ENTRIES);

    private CustomActionIcons() {}

    static Drawable load(Context context, MediaCustomAction action) {
        String key = action.ownerPackage + ":" + action.iconResId;
        Drawable.ConstantState state = CACHE.get(key);
        if (state == null) {
            state = resolve(context, action);
            CACHE.put(key, state);
        }
        return state == MISSING ? null : state.newDrawable().mutate();
    }

    private static Drawable.ConstantState resolve(Context context, MediaCustomAction action) {
        try {
            Drawable drawable = context.getPackageManager()
                    .getResourcesForApplication(action.ownerPackage)
                    .getDrawable(action.iconResId, null);
            Drawable.ConstantState state = drawable == null ? null : drawable.getConstantState();
            return state == null ? MISSING : state;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            AppLog.info("Custom action icon unavailable: " + action.ownerPackage
                    + " 0x" + Integer.toHexString(action.iconResId));
            return MISSING;
        }
    }
}
