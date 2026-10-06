package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/**
 * Loads a custom action's icon from the publishing player's own resources. Players draw these
 * icons with very different transparent margins, so the glyph is trimmed to its visible bounds
 * and the card alone decides how large it looks.
 */
final class CustomActionIcons {
    private static final int CACHE_ENTRIES = 32;
    private static final int RENDER_PX = 192;
    private static final Bitmap MISSING = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(CACHE_ENTRIES);

    private CustomActionIcons() {}

    static Drawable load(Context context, MediaCustomAction action) {
        String key = action.ownerPackage + ":" + action.iconResId;
        Bitmap bitmap = CACHE.get(key);
        if (bitmap == null) {
            bitmap = resolve(context, action);
            CACHE.put(key, bitmap);
        }
        return bitmap == MISSING ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    private static Bitmap resolve(Context context, MediaCustomAction action) {
        try {
            Drawable drawable = context.getPackageManager()
                    .getResourcesForApplication(action.ownerPackage)
                    .getDrawable(action.iconResId, null);
            Bitmap trimmed = drawable == null ? null : trimmed(drawable);
            return trimmed == null ? MISSING : trimmed;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            AppLog.info("Custom action icon unavailable: " + action.ownerPackage
                    + " 0x" + Integer.toHexString(action.iconResId));
            return MISSING;
        }
    }

    /** Renders the icon into a square and crops it to the pixels it actually paints. */
    static Bitmap trimmed(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        float scale = width > 0 && height > 0 ? RENDER_PX / (float) Math.max(width, height) : 1f;
        int renderWidth = width > 0 ? Math.max(1, Math.round(width * scale)) : RENDER_PX;
        int renderHeight = height > 0 ? Math.max(1, Math.round(height * scale)) : RENDER_PX;
        Bitmap full = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, renderWidth, renderHeight);
        drawable.draw(new Canvas(full));

        int[] pixels = new int[renderWidth * renderHeight];
        full.getPixels(pixels, 0, renderWidth, 0, 0, renderWidth, renderHeight);
        int left = renderWidth, top = renderHeight, right = -1, bottom = -1;
        for (int y = 0; y < renderHeight; y++) {
            for (int x = 0; x < renderWidth; x++) {
                if ((pixels[y * renderWidth + x] >>> 24) < 16) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        if (right < left || bottom < top) return null;
        // A square crop keeps narrow glyphs (a pause bar, an arrow) at their natural proportion.
        int side = Math.max(right - left + 1, bottom - top + 1);
        int cropLeft = Math.max(0, Math.min(renderWidth - side, (left + right + 1 - side) / 2));
        int cropTop = Math.max(0, Math.min(renderHeight - side, (top + bottom + 1 - side) / 2));
        int cropWidth = Math.min(side, renderWidth - cropLeft);
        int cropHeight = Math.min(side, renderHeight - cropTop);
        return Bitmap.createBitmap(full, cropLeft, cropTop, cropWidth, cropHeight);
    }
}
