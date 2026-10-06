package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRenderingTest {
    @Test public void everyAppearanceParameterChangesRealRemoteViewsPixelsInBothLayouts() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        Bitmap cover = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        cover.eraseColor(Color.rgb(150, 120, 80));
        String[] keys = {"metadataProgressGap", "controlHeight", "controlIconScale", "controlSpread",
                "controlBottomInset", "topInset", "contentInset", "topRowTextSize", "titleTextSize",
                "subtitleTextSize", "subtitleGap", "timeTextSize", "progressGap", "progressThickness"};
        int[] values = {30, 140, 130, 43, 25, 32, 55, 25, 40, 28, 15, 22, 35, 14};
        for (CardStyle style : CardStyle.values()) {
            prefs.putInt(Prefs.KEY_CARD_STYLE, style.preferenceValue);
            WidgetAppearance defaults = WidgetAppearance.defaults(style);
            prefs.putAppearance(style, defaults);
            int[] defaultsArray = {defaults.metadataProgressGapDp, defaults.controlPanelHeightDp,
                    defaults.controlIconScalePercent, defaults.controlSpreadPercent, defaults.controlBottomInsetDp,
                    defaults.topInsetDp, defaults.contentInsetDp, defaults.topRowTextSizeSp,
                    defaults.titleTextSizeSp, defaults.subtitleTextSizeSp, defaults.subtitleGapDp,
                    defaults.timeTextSizeSp, defaults.progressGapDp, defaults.progressThicknessDp};
            int[] original = pixels(context, prefs, cover);
            for (int i = 0; i < keys.length; i++) {
                prefs.putAppearance(style, defaults);
                int[] changed = defaultsArray.clone();
                changed[i] = values[i];
                prefs.putAppearance(style, appearance(changed, defaults.coverDimPreset));
                assertFalse(style + ": " + keys[i] + " must affect actual widget pixels",
                        Arrays.equals(original, pixels(context, prefs, cover)));
            }
            prefs.putAppearance(style, defaults);
            for (CoverDimPreset preset : CoverDimPreset.values()) {
                prefs.putAppearance(style, appearance(defaultsArray, preset));
                if (preset != defaults.coverDimPreset) assertFalse(preset.name(),
                        Arrays.equals(original, pixels(context, prefs, cover)));
            }
            if (style == CardStyle.COMPACT) {
                prefs.putAppearance(style, appearance(defaultsArray,
                        defaults.coverDimPreset, 104));
                assertFalse("compact thumbnail size must affect actual widget pixels",
                        Arrays.equals(original, pixels(context, prefs, cover)));
            }
        }
    }

    @Test public void customActionIconsAreTrimmedToTheirGlyph() {
        var glyph = new android.graphics.drawable.InsetDrawable(
                new android.graphics.drawable.ColorDrawable(0xFFFFFFFF), 30, 40, 30, 40) {
            @Override public int getIntrinsicWidth() { return 96; }
            @Override public int getIntrinsicHeight() { return 96; }
        };
        Bitmap trimmed = CustomActionIcons.trimmed(glyph);
        assertEquals(trimmed.getWidth(), trimmed.getHeight());
        assertTrue("transparent margins are cropped", trimmed.getWidth() < 192);
        assertNull(CustomActionIcons.trimmed(
                new android.graphics.drawable.ColorDrawable(0x00000000)));
    }

    private WidgetAppearance appearance(int[] a, CoverDimPreset preset) {
        return appearance(a, preset, Prefs.DEFAULT_THUMBNAIL_SIZE_DP);
    }

    private WidgetAppearance appearance(int[] a, CoverDimPreset preset, int thumbnailSizeDp) {
        return new WidgetAppearance(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7],
                a[8], a[9], a[10], a[11], a[12], a[13], preset, thumbnailSizeDp);
    }

    private int[] pixels(Context context, Prefs prefs, Bitmap cover) {
        MediaSnapshot snapshot = new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), "test", "Player", "one", "Track title", "Artist", "Album",
                180000, 30000, 0, 0, 2, 0, "", 0, 127, "", 1);
        Bundle options = new Bundle();
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400);
        var frame = new AtlasMediaWidgetProvider.Frame(context, prefs, options, 0, snapshot, cover, true,
                new MediaCardView.Listener() {
                    public boolean onDragTouch(View v, android.view.MotionEvent e) { return false; }
                    public void onCommand(String command) {}
                    public void onSeek(long value) {}
                    public void onSource(MediaSource.Id id) {}
                    public void onOpenSource() {}
                    public void onRadioStationsRequested() {}
                    public void onRadioStation(RadioStation station) {}
                    public void onRadioArtworkRequested(RadioStation station) {}
                    public void onCustomAction(String action) {}
                });
        View view = frame.views.apply(context, null);
        Bitmap card = ((BitmapDrawable)((ImageView)view.findViewById(R.id.widget_card)).getDrawable()).getBitmap();
        Bitmap progress = ((BitmapDrawable)((ImageView)view.findViewById(R.id.widget_progress)).getDrawable()).getBitmap();
        int[] pixels = new int[card.getWidth() * card.getHeight() + progress.getWidth() * progress.getHeight()];
        card.getPixels(pixels, 0, card.getWidth(), 0, 0, card.getWidth(), card.getHeight());
        progress.getPixels(pixels, card.getWidth() * card.getHeight(), progress.getWidth(), 0, 0, progress.getWidth(), progress.getHeight());
        return pixels;
    }
}
