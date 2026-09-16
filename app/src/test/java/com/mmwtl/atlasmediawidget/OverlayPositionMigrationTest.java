package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Rect;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class OverlayPositionMigrationTest {
    private static final Rect BOUNDS = new Rect(12, 24, 1452, 1920);

    private Prefs prefs;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE)
                .edit().clear().commit();
        prefs = new Prefs(context);
    }

    @Test public void oldCornerOffsetsAdjustForSafeAreaInAllDirections() {
        for (OverlayCorner corner : OverlayCorner.values()) {
            OverlayGeometry.Offset offsets = OverlayPositionMigration.migrateCornerOffsets(
                    corner, BOUNDS.left, BOUNDS.top, 100, 257);

            assertEquals(corner == OverlayCorner.TOP_END || corner == OverlayCorner.BOTTOM_END
                    ? 88 : 112, offsets.x());
            assertEquals(corner == OverlayCorner.BOTTOM_START || corner == OverlayCorner.BOTTOM_END
                    ? 233 : 281, offsets.y());
        }
    }

    @Test public void bottomInsetExampleMigrates257To233() {
        OverlayGeometry.Offset offsets = OverlayPositionMigration.migrateCornerOffsets(
                OverlayCorner.BOTTOM_START, 0, 24, 0, 257);

        assertEquals(0, offsets.x());
        assertEquals(233, offsets.y());
    }

    @Test public void legacyAbsoluteSettingsAreConvertedToTopStartOffsets() {
        prefs.putInt(Prefs.KEY_POSITION_X, 300);
        prefs.putInt(Prefs.KEY_POSITION_Y, 257);

        OverlayPositionMigration.migrate(prefs, BOUNDS, 500, 150);

        assertEquals("top_start", prefs.getString(Prefs.KEY_POSITION_CORNER, null));
        assertEquals(288, prefs.getInt(Prefs.KEY_POSITION_X, -1));
        assertEquals(233, prefs.getInt(Prefs.KEY_POSITION_Y, -1));
        assertEquals(Prefs.POSITION_CONTRACT_VERSION,
                prefs.getInt(Prefs.KEY_POSITION_CONTRACT_VERSION, 0));
    }

    @Test public void migrationVersionPreventsASecondConversion() {
        prefs.putString(Prefs.KEY_POSITION_CORNER, OverlayCorner.BOTTOM_START.preferenceValue);
        prefs.putInt(Prefs.KEY_POSITION_X, 100);
        prefs.putInt(Prefs.KEY_POSITION_Y, 257);

        OverlayPositionMigration.migrate(prefs, BOUNDS, 500, 150);
        OverlayPositionMigration.migrate(prefs, new Rect(12, 48, 1452, 1920), 500, 500);

        assertEquals(112, prefs.getInt(Prefs.KEY_POSITION_X, -1));
        assertEquals(233, prefs.getInt(Prefs.KEY_POSITION_Y, -1));
    }
}
