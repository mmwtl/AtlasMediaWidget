package com.mmwtl.atlasmediawidget;

import android.graphics.Rect;

final class OverlayPositionMigration {
    private OverlayPositionMigration() {}

    static void migrate(Prefs prefs, Rect bounds, int cardWidth, int cardHeight) {
        if (prefs.getInt(Prefs.KEY_POSITION_CONTRACT_VERSION, 0)
                >= Prefs.POSITION_CONTRACT_VERSION) {
            return;
        }

        OverlayCorner corner = OverlayCorner.fromPreference(
                prefs.getString(Prefs.KEY_POSITION_CORNER, null));
        int storedX = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int storedY = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        if (corner == null) {
            int defaultX = bounds.left + Math.max(0, (bounds.width() - cardWidth) / 2);
            int defaultY = bounds.top + Math.max(0, Math.round(bounds.height() * 0.62f));
            int absoluteX = storedX == Prefs.POSITION_UNSET ? defaultX : storedX;
            int absoluteY = storedY == Prefs.POSITION_UNSET ? defaultY : storedY;
            OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(
                    OverlayCorner.TOP_START, bounds.left, bounds.top, bounds.right, bounds.bottom,
                    cardWidth, cardHeight, absoluteX, absoluteY);
            prefs.putPosition(OverlayCorner.TOP_START, offsets.x(), offsets.y());
            return;
        }

        int oldX = storedX == Prefs.POSITION_UNSET ? 0 : storedX;
        int oldY = storedY == Prefs.POSITION_UNSET ? 0 : storedY;
        OverlayGeometry.Offset offsets = migrateCornerOffsets(corner,
                bounds.left, bounds.top, oldX, oldY);
        prefs.putPosition(corner, offsets.x(), offsets.y());
    }

    static OverlayGeometry.Offset migrateCornerOffsets(OverlayCorner corner,
            int boundsLeft, int boundsTop, int oldX, int oldY) {
        long x = isEnd(corner) ? (long) oldX - boundsLeft : (long) oldX + boundsLeft;
        long y = isBottom(corner) ? (long) oldY - boundsTop : (long) oldY + boundsTop;
        return new OverlayGeometry.Offset(nonNegativeInt(x), nonNegativeInt(y));
    }

    private static boolean isEnd(OverlayCorner corner) {
        return corner == OverlayCorner.TOP_END || corner == OverlayCorner.BOTTOM_END;
    }

    private static boolean isBottom(OverlayCorner corner) {
        return corner == OverlayCorner.BOTTOM_START || corner == OverlayCorner.BOTTOM_END;
    }

    private static int nonNegativeInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }
}
