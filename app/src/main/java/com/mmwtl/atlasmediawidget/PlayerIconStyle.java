package com.mmwtl.atlasmediawidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/**
 * Player app icons drawn like the card's own source glyphs, in the card's text colour. The logo
 * layer of an adaptive icon (or the whole icon of an older app) is reduced to one tone: a logo
 * on transparency keeps its outline and its inner marks become cut-outs (the AIMP triangle, the
 * VLC stripes); an icon filled edge to edge loses its background and keeps the mark on it. When
 * nothing usable is left the icon is shown in greyscale.
 */
final class PlayerIconStyle {
    /** Above this share of opaque pixels the layer is a filled background, not a logo. */
    static final float MAX_LOGO_COVERAGE = 0.55f;
    /** A filled icon needs one background tone holding this share; two-tone art stays greyscale. */
    static final float MIN_BACKGROUND_SHARE = 0.65f;
    /** The glyph's content fills this share of the tile icon box, like the card's own glyphs. */
    private static final float GLYPH_FILL = 0.84f;
    /** Below this share a glyph is noise or empty. */
    static final float MIN_GLYPH_COVERAGE = 0.03f;
    /** Luminance distance from the main tone where a pixel starts and fully becomes a mark. */
    private static final float MARK_START = 0.12f;
    private static final float MARK_FULL = 0.24f;
    /** An adaptive layer is 108 dp with the logo in the middle 72 dp. */
    private static final float LAYER_TO_LOGO = 108f / 72f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);

    private PlayerIconStyle() {}

    static synchronized Bitmap glyph(String packageName, PlayerApps.Info app, int color, int sizePx) {
        String key = packageName + '|' + color + '|' + sizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) return cached;
        Drawable icon = app.newIcon();
        if (icon == null || sizePx <= 0) return null;
        Bitmap source = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        if (icon instanceof AdaptiveIconDrawable adaptive && adaptive.getForeground() != null) {
            Drawable logo = adaptive.getForeground();
            int inset = Math.round(sizePx * (LAYER_TO_LOGO - 1f) / 2f);
            logo.setBounds(-inset, -inset, sizePx + inset, sizePx + inset);
            logo.draw(new Canvas(source));
        } else {
            icon.setBounds(0, 0, sizePx, sizePx);
            icon.draw(new Canvas(source));
        }
        int[] pixels = new int[sizePx * sizePx];
        source.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx);
        source.recycle();
        int[] glyph = oneTone(pixels, color);
        Bitmap bitmap = glyph != null
                ? fitted(Bitmap.createBitmap(glyph, sizePx, sizePx, Bitmap.Config.ARGB_8888))
                : greyscale(icon, sizePx);
        CACHE.put(key, bitmap);
        return bitmap;
    }

    /** The one-tone glyph as ARGB pixels in {@code color}, or null when nothing usable is left. */
    static int[] oneTone(int[] pixels, int color) {
        int opaque = 0;
        float total = 0f;
        float[] weight = new float[32];
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha < 128) continue;
            opaque++;
            total += alpha;
            weight[Math.min(31, (int) (luminance(pixel) * 32f))] += alpha;
        }
        if (pixels.length == 0 || opaque / (float) pixels.length < MIN_GLYPH_COVERAGE) return null;
        int main = 0;
        for (int bucket = 1; bucket < weight.length; bucket++) {
            if (weight[bucket] > weight[main]) main = bucket;
        }
        float mainTone = (main + 0.5f) / 32f;
        boolean filled = opaque / (float) pixels.length > MAX_LOGO_COVERAGE;
        // Two neighbouring buckets: a flat tone with slight shading still counts as one.
        float mainWeight = weight[main] + Math.max(main > 0 ? weight[main - 1] : 0f,
                main < 31 ? weight[main + 1] : 0f);
        if (filled && mainWeight < total * MIN_BACKGROUND_SHARE) return null;
        int rgb = color & 0xFFFFFF;
        float colorAlpha = (color >>> 24) / 255f;
        int[] out = new int[pixels.length];
        int kept = 0;
        for (int index = 0; index < pixels.length; index++) {
            int alpha = pixels[index] >>> 24;
            if (alpha == 0) continue;
            float mark = clamp((Math.abs(luminance(pixels[index]) - mainTone) - MARK_START)
                    / (MARK_FULL - MARK_START));
            float share = filled ? mark : 1f - mark;
            int value = Math.round(alpha * share * colorAlpha);
            if (value >= 128) kept++;
            out[index] = (value << 24) | rgb;
        }
        float keptShare = kept / (float) pixels.length;
        return keptShare < MIN_GLYPH_COVERAGE || keptShare > MAX_LOGO_COVERAGE ? null : out;
    }

    /** Scales the glyph's content to {@link #GLYPH_FILL} of the box, centred. */
    private static Bitmap fitted(Bitmap glyph) {
        int size = glyph.getWidth();
        int[] pixels = new int[size * size];
        glyph.getPixels(pixels, 0, size, 0, 0, size, size);
        int left = size, top = size, right = -1, bottom = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((pixels[y * size + x] >>> 24) < 24) continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left) return glyph;
        float width = right - left + 1;
        float height = bottom - top + 1;
        float scale = size * GLYPH_FILL / Math.max(width, height);
        float dx = (size - width * scale) / 2f;
        float dy = (size - height * scale) / 2f;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(glyph, new android.graphics.Rect(left, top, right + 1, bottom + 1),
                new android.graphics.RectF(dx, dy, dx + width * scale, dy + height * scale),
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        glyph.recycle();
        return out;
    }

    private static Bitmap greyscale(Drawable icon, int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        icon.setColorFilter(new ColorMatrixColorFilter(matrix));
        icon.setBounds(0, 0, size, size);
        icon.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static float luminance(int pixel) {
        return (0.299f * ((pixel >> 16) & 0xFF) + 0.587f * ((pixel >> 8) & 0xFF)
                + 0.114f * (pixel & 0xFF)) / 255f;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
