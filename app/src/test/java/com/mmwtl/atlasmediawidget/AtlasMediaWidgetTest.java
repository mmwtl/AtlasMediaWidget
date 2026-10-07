package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class AtlasMediaWidgetTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final MediaCardView.Listener listener = new MediaCardView.Listener() {
        public boolean onDragTouch(View v, android.view.MotionEvent e) { return false; }
        public void onCommand(String command) {}
        public void onSeek(long value) {}
        public void onSource(MediaSource.Id id) {}
        public void onOpenSource() {}
        public void onRadioStationsRequested() {}
        public void onRadioStation(RadioStation station) {}
        public void onRadioArtworkRequested(RadioStation station) {}
        public void onCustomAction(String action) {}
    };

    @Test public void modeRoundTripAndLegacyBackupPreserveOverlayGeometry() throws Exception {
        Prefs prefs = new Prefs(context);
        assertFalse(prefs.isWidgetMode());
        prefs.putCardSizePx(590, 430);
        prefs.putPosition(OverlayCorner.BOTTOM_END, 71, 93);
        prefs.putBoolean(Prefs.KEY_AUTO_START, true);
        prefs.setWidgetMode(true);
        SettingsBackup.Data data = SettingsBackup.decode(SettingsBackup.encode(context, prefs));
        assertTrue(data.widgetMode);
        prefs.setWidgetMode(false);
        assertTrue(prefs.replacePortableSettings(data));
        assertTrue(new Prefs(context).isWidgetMode());
        assertEquals(590, prefs.cardWidthPx());
        assertEquals(71, prefs.getInt(Prefs.KEY_POSITION_X, 0));
        JSONObject legacy = new JSONObject(SettingsBackup.encode(context, prefs));
        legacy.put("schemaVersion", 10);
        legacy.getJSONObject("settings").remove("widgetMode");
        assertTrue(prefs.replacePortableSettings(SettingsBackup.decode(legacy.toString())));
        assertFalse(prefs.isWidgetMode());
        assertEquals(430, prefs.cardHeightPx());
        assertTrue(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
    }

    @Test public void configureRejectsUnknownAndForeignIds() {
        var manager = Shadows.shadowOf(AppWidgetManager.getInstance(context));
        AppWidgetProviderInfo foreign = new AppWidgetProviderInfo();
        foreign.provider = new ComponentName("other", "other.Provider");
        manager.addBoundWidget(92, foreign);
        for (int id : new int[]{0, 91, 92}) {
            var controller = Robolectric.buildActivity(WidgetSetupActivity.class, configure(id)).create();
            assertTrue(controller.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(controller.get()).getResultCode());
            controller.destroy();
        }
    }

    @Test public void setupCancelKeepsOriginalIdAndRestoresLook() {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        prefs.putCoverDimPreset(CardStyle.SQUARE, CoverDimPreset.WEAK);
        bind(41);
        var controller = Robolectric.buildActivity(WidgetSetupActivity.class, configure(41)).create();
        WidgetSetupActivity activity = controller.get();
        View root = activity.findViewById(android.R.id.content);
        assertFalse("setup must stay open", activity.isFinishing());
        find(root, "Просторная").performClick();
        find(root, "Максимум").performClick();
        assertEquals(CoverDimPreset.MAXIMUM, prefs.coverDimPreset(CardStyle.SQUARE));
        find(root, "✕").performClick();
        assertTrue(activity.isFinishing());
        controller.destroy();
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity).getResultCode());
        assertEquals(41, Shadows.shadowOf(activity).getResultIntent()
                .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
        assertEquals(CardStyle.COMPACT.preferenceValue,
                prefs.getInt(Prefs.KEY_CARD_STYLE, -1));
        assertEquals(CoverDimPreset.WEAK, prefs.coverDimPreset(CardStyle.SQUARE));
        assertFalse(prefs.isWidgetMode());
        assertArrayEquals(new int[]{41}, AtlasMediaWidgetProvider.ids(context));
    }

    @Test public void setupConfirmKeepsLookAndSwitchesModeOnlyWhenChecked() {
        for (boolean switchMode : new boolean[]{false, true}) {
            Prefs prefs = new Prefs(context);
            prefs.setWidgetMode(false);
            prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
            int id = switchMode ? 42 : 41;
            bind(id);
            var controller = Robolectric.buildActivity(WidgetSetupActivity.class,
                    configure(id)).create();
            WidgetSetupActivity activity = controller.get();
            View root = activity.findViewById(android.R.id.content);
            find(root, "Просторная").performClick();
            if (!switchMode) find(root, "Переключить приложение в режим «Виджет»").performClick();
            find(root, "Добавить").performClick();
            assertTrue(activity.isFinishing());
            controller.destroy();
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            assertEquals(id, Shadows.shadowOf(activity).getResultIntent()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
            assertEquals(CardStyle.SQUARE.preferenceValue,
                    prefs.getInt(Prefs.KEY_CARD_STYLE, -1));
            assertEquals(switchMode, prefs.isWidgetMode());
        }
    }

    @Test public void setupRemembersConfirmedIdsForReconfigurationUntilDeleted() {
        new Prefs(context).setWidgetMode(true);
        bind(43);
        var first = Robolectric.buildActivity(WidgetSetupActivity.class, configure(43)).create();
        find(first.get().findViewById(android.R.id.content), "Добавить").performClick();
        first.destroy();
        var again = Robolectric.buildActivity(WidgetSetupActivity.class, configure(43)).create();
        View root = again.get().findViewById(android.R.id.content);
        assertNotNull(find(root, "Настройка медиавиджета"));
        assertNull(find(root, "Добавить"));
        find(root, "Готово").performClick();
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(again.get()).getResultCode());
        again.destroy();
        new AtlasMediaWidgetProvider().onDeleted(context, new int[]{43});
        assertFalse(new Prefs(context).isWidgetConfigured(43));
    }

    @Test public void setupInWidgetModeHidesModeSwitchAndCanOpenSettings() {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        var controller = Robolectric.buildActivity(WidgetSetupActivity.class, configure(41)).create();
        WidgetSetupActivity activity = controller.get();
        View root = activity.findViewById(android.R.id.content);
        assertNull(find(root, "Переключить приложение в режим «Виджет»"));
        find(root, "Добавить и открыть все настройки").performClick();
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        Intent settings = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(new ComponentName(context, MainActivity.class), settings.getComponent());
        assertTrue((settings.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        controller.destroy();
    }

    @Test public void remoteViewsUseHostDimensionsCapabilitiesAndIndependentActions() {
        Prefs prefs = new Prefs(context);
        prefs.putCardSizePx(1500, 1300);
        for (CardStyle style : CardStyle.values()) {
            prefs.putInt(Prefs.KEY_CARD_STYLE, style.preferenceValue);
            for (int width : new int[]{280, 500}) {
                Bundle options = new Bundle();
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 240);
                var frame = new AtlasMediaWidgetProvider.Frame(context, prefs, options, 41,
                        snapshot(MediaBridgeContract.CAP_PLAY), null, true, listener);
                assertEquals(Ui.dp(context, width), frame.width);
                assertEquals(Ui.dp(context, 240), frame.height);
                assertTrue(frame.card.widgetBounds(frame.card.widgetTarget("PREVIOUS")).centerX()
                        < frame.card.widgetBounds(frame.card.widgetTarget("PLAY_PAUSE")).centerX());
                assertTrue(frame.card.widgetBounds(frame.card.widgetTarget("NEXT")).centerX()
                        > frame.card.widgetBounds(frame.card.widgetTarget("PLAY_PAUSE")).centerX());
                assertFalse(frame.card.widgetTarget("NEXT").isEnabled());
                assertTrue(frame.card.widgetTarget("PLAY_PAUSE").isEnabled());
                View rendered = frame.views.apply(context, null);
                assertNotNull(rendered.findViewById(R.id.widget_card));
                assertNotNull(frame.progress(context, snapshot(MediaBridgeContract.CAP_PLAY)));
            }
        }
        assertNotEquals(AtlasMediaWidgetProvider.click(context, 41, "NEXT", true),
                AtlasMediaWidgetProvider.click(context, 42, "NEXT", true));
        assertNotEquals(AtlasMediaWidgetProvider.click(context, 41, "NEXT", true),
                AtlasMediaWidgetProvider.click(context, 41, "PREVIOUS", true));
    }

    @Test public void widgetSettingsHideOnlyOverlayControls() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        var controller = Robolectric.buildActivity(MainActivity.class).create();
        View root = controller.get().findViewById(android.R.id.content);
        assertFalse(MainActivityTest.shown(find(root, "Разрешить поверх окон")));
        assertFalse(MainActivityTest.shown(find(root, "Размер карточки")));
        assertTrue(MainActivityTest.shown(find(root, "Добавить виджет")));
        assertTrue(MainActivityTest.shown(find(root, "Затемнение обложки")));
        find(root, "Медиа").performClick();
        assertTrue(MainActivityTest.shown(find(root, "Разрешить доступ к уведомлениям (медиа)")));
        controller.destroy();
        prefs.setWidgetMode(false);
        controller = Robolectric.buildActivity(MainActivity.class).create();
        root = controller.get().findViewById(android.R.id.content);
        assertTrue(MainActivityTest.shown(find(root, "Размер карточки")));
        assertTrue(MainActivityTest.shown(find(root, "Разрешить поверх окон")));
        assertFalse(MainActivityTest.shown(find(root, "Добавить виджет")));
        controller.destroy();
    }

    @Test public void widgetClicksKeepSettingsSeparateFromPlaybackUi() {
        for (String action : new String[]{"open", "settings"}) {
            Intent intent = Shadows.shadowOf(AtlasMediaWidgetProvider.click(context, 41, action, false))
                    .getSavedIntent();
            assertEquals(new ComponentName(context, "settings".equals(action)
                    ? MainActivity.class : WidgetControlActivity.class), intent.getComponent());
            assertEquals(41, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
        }
        for (String action : new String[]{"PREVIOUS", "PLAY_PAUSE", "NEXT", "sources",
                "source_RADIO", "dismiss_sources", "favorites", "dismiss_favorites"}) {
            assertEquals(new ComponentName(context, OverlayService.class),
                    Shadows.shadowOf(AtlasMediaWidgetProvider.click(context, 41, action, true))
                            .getSavedIntent().getComponent());
        }
    }

    @Test public void openSourceDoesNotBuildCardAndLaunchesOnlyOnce() {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        var controller = Robolectric.buildActivity(WidgetControlActivity.class,
                widgetControl("open")).create();
        WidgetControlActivity activity = controller.get();
        assertEquals(0, ((ViewGroup) activity.findViewById(android.R.id.content)).getChildCount());
        MediaSnapshot state = snapshot(MediaBridgeContract.CAP_PLAY);
        activity.onSnapshot(state);
        assertTrue(activity.isFinishing());
        Intent launch = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertNotNull(launch);
        assertNull(launch.getComponent()); // Music selector when the test player is not installed.
        assertNotNull(launch.getSelector());
        activity.onSnapshot(state);
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        controller.destroy();
    }

    @Test public void openSourceTimesOutAndCannotLaunchAfterCancellation() {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildActivity(WidgetControlActivity.class,
                widgetControl("open")).create().start();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5));
        assertTrue(controller.get().isFinishing());
        controller.get().onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        controller.stop().destroy();
    }

    @Test public void widgetDialogsShowOnlyTheCardAndRejectForeignIds() {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        for (String action : new String[]{"favorites", "seek"}) {
            var retired = Robolectric.buildActivity(WidgetControlActivity.class, widgetControl(action)).create();
            assertTrue(action + " no longer opens a card dialog", retired.get().isFinishing());
            retired.destroy();
        }
        var controller = Robolectric.buildActivity(WidgetControlActivity.class,
                widgetControl("open").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 99)).create();
        assertTrue(controller.get().isFinishing());
        controller.destroy();
    }

    @Test public void seekZonesMapToTheirCentreAndRejectMalformedControls() {
        assertEquals(3_750L, AtlasMediaWidgetProvider.seekZonePosition("seek_0_24", 180_000L));
        assertEquals(176_250L, AtlasMediaWidgetProvider.seekZonePosition("seek_23_24", 180_000L));
        assertEquals(90_000L, AtlasMediaWidgetProvider.seekZonePosition("seek_0_1", 180_000L));
        for (String control : new String[]{null, "seek", "seek_24_24", "seek_-1_24", "seek_1_0",
                "seek_a_24", "seek_1_2_3", "NEXT"}) {
            assertEquals(control, -1L, AtlasMediaWidgetProvider.seekZonePosition(control, 180_000L));
        }
        assertEquals(-1L, AtlasMediaWidgetProvider.seekZonePosition("seek_1_24", 0L));
    }

    @Test @SuppressWarnings("deprecation")
    public void progressStripSeeksInPlaceAndTimeLabelsOpenTheScrubber() {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        var readOnly = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                snapshot(MediaBridgeContract.CAP_PLAY), null, true, listener);
        var seekable = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                snapshot(MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_SEEK), null, true, listener);
        assertFalse(seekable.progressBounds.isEmpty());
        ViewGroup targets = seekable.views.apply(context, null).findViewById(R.id.widget_targets);
        int readOnlyTargets = ((ViewGroup) readOnly.views.apply(context, null)
                .findViewById(R.id.widget_targets)).getChildCount();
        int zones = 0;
        int scrubbers = 0;
        for (int index = 0; index < targets.getChildCount(); index++) {
            CharSequence description = targets.getChildAt(index).findViewById(R.id.widget_target)
                    .getContentDescription();
            if (description.toString().startsWith("Перемотать на ")) zones++;
            if ("Точная перемотка".contentEquals(description)) scrubbers++;
        }
        assertTrue(zones >= 8 && zones <= 32);
        assertEquals(2, scrubbers);
        assertEquals(readOnlyTargets + zones + scrubbers, targets.getChildCount());
        assertEquals(new ComponentName(context, OverlayService.class), Shadows.shadowOf(
                AtlasMediaWidgetProvider.click(context, 41, "seek_3_24", true)).getSavedIntent().getComponent());
        PendingIntent scrub = AtlasMediaWidgetProvider.scrubClick(context, 41, "elapsed",
                new android.graphics.Rect(1, 2, 3, 4), seekable.progressBounds);
        assertTrue((Shadows.shadowOf(scrub).getFlags() & PendingIntent.FLAG_IMMUTABLE) == 0);
        Intent intent = Shadows.shadowOf(scrub).getSavedIntent();
        assertEquals(new ComponentName(context, WidgetControlActivity.class), intent.getComponent());
        assertEquals("scrub", intent.getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL));
        assertEquals(seekable.progressBounds,
                intent.getParcelableExtra(AtlasMediaWidgetProvider.EXTRA_PROGRESS_BOUNDS));
        assertNull(intent.getStringExtra(AtlasMediaWidgetProvider.EXTRA_SEEK_ZONE));
        Intent zone = Shadows.shadowOf(AtlasMediaWidgetProvider.scrubClick(context, 41, "seek_3_24",
                new android.graphics.Rect(1, 2, 3, 4), seekable.progressBounds)).getSavedIntent();
        assertEquals("seek_3_24", zone.getStringExtra(AtlasMediaWidgetProvider.EXTRA_SEEK_ZONE));
        assertNotEquals(intent.getData(), zone.getData());
    }

    @Test public void scrubberSitsOverTheWidgetStripAndNeedsHostBounds() {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        var row = new android.graphics.Rect(20, 300, 480, 340);
        var label = new android.graphics.Rect(20, 290, 90, 350);
        Intent intent = Shadows.shadowOf(AtlasMediaWidgetProvider.scrubClick(context, 41,
                "elapsed", label, row)).getSavedIntent();
        var missing = Robolectric.buildActivity(WidgetControlActivity.class, new Intent(intent)).create();
        assertTrue(missing.get().isFinishing());
        missing.destroy();
        intent.setSourceBounds(new android.graphics.Rect(120, 1290, 190, 1350));
        var controller = Robolectric.buildActivity(WidgetControlActivity.class, intent).create();
        WidgetControlActivity activity = controller.get();
        assertFalse(activity.isFinishing());
        var attributes = activity.getWindow().getAttributes();
        assertEquals(120, attributes.x);
        assertEquals(1300 - Math.max(0, (Ui.dp(context, 56) - row.height()) / 2), attributes.y);
        assertEquals(row.width(), attributes.width);
        assertTrue((attributes.flags & android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
        ViewGroup strip = (ViewGroup) ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        assertTrue(strip.getChildAt(0) instanceof MediaCardView);
        activity.onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY));
        assertTrue("a source without seek closes the scrubber", activity.isFinishing());
        controller.destroy();

        Intent zone = Shadows.shadowOf(AtlasMediaWidgetProvider.scrubClick(context, 41,
                "seek_20_24", label, row)).getSavedIntent();
        zone.setSourceBounds(new android.graphics.Rect(120, 1290, 190, 1350));
        var zoneController = Robolectric.buildActivity(WidgetControlActivity.class, zone).create();
        Intent seek = null;
        for (Intent started; (started = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStartedService()) != null; ) {
            if (AtlasMediaWidgetProvider.ACTION_COMMAND.equals(started.getAction())) seek = started;
        }
        assertNotNull(seek);
        assertEquals(new ComponentName(context, OverlayService.class), seek.getComponent());
        assertEquals("seek_20_24", seek.getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL));
        assertEquals(41, seek.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
        assertFalse("the scrubber stays for fine dragging", zoneController.get().isFinishing());
        zoneController.destroy();
    }

    @Test public void scrubberCoversTheStripWithTheCardInsteadOfHidingIt() throws Exception {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var service = Robolectric.buildService(OverlayService.class).create();
        try {
            service.get().onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            service.get().onBridgeState(MediaBridgeClient.State.CONNECTED, "");
            service.get().onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_SEEK));
            var frame = service.get().widgetFrame(41);
            assertNotNull(frame.cardBitmap);
            android.graphics.Rect row = frame.progressBounds;
            Intent intent = Shadows.shadowOf(AtlasMediaWidgetProvider.scrubClick(context, 41,
                    "elapsed", row, row)).getSavedIntent();
            intent.setSourceBounds(new android.graphics.Rect(row.left + 100, row.top + 900,
                    row.right + 100, row.bottom + 900));
            var controller = Robolectric.buildActivity(WidgetControlActivity.class, intent).create();
            ViewGroup strip = (ViewGroup) ((ViewGroup) controller.get()
                    .findViewById(android.R.id.content)).getChildAt(0);
            assertTrue(strip.getBackground() instanceof android.graphics.drawable.BitmapDrawable);
            assertFalse(controller.get().isFinishing());
            assertTrue("the widget strip stays; the opaque window covers it",
                    ((java.util.Set<?>) field(service.get(), "scrubbingWidgetIds")).isEmpty());
            controller.destroy();
        } finally {
            service.destroy();
        }
    }

    @Test public void seekZoneCommandProjectsThePositionOnTheWidget() throws Exception {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            service.onBridgeState(MediaBridgeClient.State.CONNECTED, "");
            service.onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_SEEK));
            var frames = (java.util.Map<?, ?>) field(service, "widgetFrames");
            assertEquals("0:10", ((TextView) ((AtlasMediaWidgetProvider.Frame) frames.get(41))
                    .card.widgetTarget("elapsed")).getText().toString());
            service.onStartCommand(widgetCommand(41, "seek_23_24"), 0, 2);
            assertEquals("2:56", ((TextView) ((AtlasMediaWidgetProvider.Frame) frames.get(41))
                    .card.widgetTarget("elapsed")).getText().toString());
        } finally {
            controller.destroy();
        }
    }

    @Test public void favoritesGridOpensInsideTheWidgetAndScrollsInTheLauncher() {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400);
        var lists = new RadioStationLists(1L, List.of(), favoriteStations(9));
        var frame = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                radioSnapshot(), null, true, "favorites",
                new AtlasMediaWidgetProvider.Frame.Favorites(lists, false, null), listener);
        assertTrue(frame.favoritesGridShown);
        assertTrue(frame.progressBounds.isEmpty());
        assertEquals(9, frame.card.widgetFavoriteStations().size());
        Bitmap tile = frame.card.renderFavoriteTile(lists.favorites.get(8));
        assertNotNull(tile);
        assertTrue(tile.getWidth() > 0 && tile.getHeight() > 0);
        org.robolectric.shadows.ShadowLog.clear();
        View rendered = frame.views.apply(context, new android.appwidget.AppWidgetHostView(context));
        assertTrue("the launcher must accept the collection adapter",
                org.robolectric.shadows.ShadowLog.getLogsForTag("RemoteViews").isEmpty());
        assertEquals(View.VISIBLE, rendered.findViewById(R.id.widget_favorites_box).getVisibility());
        rendered.measure(View.MeasureSpec.makeMeasureSpec(frame.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(frame.height, View.MeasureSpec.EXACTLY));
        android.widget.GridView grid = rendered.findViewById(R.id.widget_favorites);
        assertEquals(frame.card.favoriteColumns(), grid.getNumColumns());
        assertEquals(0, grid.getHorizontalSpacing());
        ViewGroup targets = rendered.findViewById(R.id.widget_targets);
        assertEquals("backdrop and favorites button only", 2, targets.getChildCount());

        var closed = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                radioSnapshot(), null, true, null,
                new AtlasMediaWidgetProvider.Frame.Favorites(lists, false, null), listener);
        assertFalse(closed.favoritesGridShown);
        assertEquals(View.GONE, closed.views.apply(context, null)
                .findViewById(R.id.widget_favorites_box).getVisibility());
        var loading = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                radioSnapshot(), null, true, "favorites", new AtlasMediaWidgetProvider.Frame.Favorites(
                RadioStationLists.EMPTY, true, null), listener);
        assertFalse("the loading text is part of the card picture", loading.favoritesGridShown);
    }

    @Test public void favoriteTileTuneClosesOnlyItsWidget() throws Exception {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        bind(42);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            service.onBridgeState(MediaBridgeClient.State.CONNECTED, "");
            service.onSnapshot(radioSnapshot());
            service.onRadioStations(new RadioStationLists(1L, List.of(), favoriteStations(3)));
            service.onStartCommand(widgetCommand(41, "favorites"), 0, 2);
            var frames = (java.util.Map<?, ?>) field(service, "widgetFrames");
            assertTrue(((AtlasMediaWidgetProvider.Frame) frames.get(41)).favoritesGridShown);
            assertFalse(((AtlasMediaWidgetProvider.Frame) frames.get(42)).favoritesGridShown);
            assertEquals(3, service.widgetFavoriteStations(41).size());
            assertEquals(0, service.widgetFavoriteStations(42).size());

            var factory = new WidgetFavoritesService.Factory(context, 41);
            factory.onDataSetChanged();
            assertEquals(3, factory.getCount());
            assertNotNull(factory.getViewAt(2));
            assertNull(factory.getViewAt(3));

            service.onStartCommand(widgetCommand(41, "favorite")
                    .putExtra(WidgetFavoritesService.EXTRA_STATION_ID, "missing"), 0, 3);
            assertTrue(((AtlasMediaWidgetProvider.Frame) frames.get(41)).favoritesGridShown);
            service.onStartCommand(widgetCommand(41, "favorite")
                    .putExtra(WidgetFavoritesService.EXTRA_STATION_ID, "s1"), 0, 4);
            assertFalse(((AtlasMediaWidgetProvider.Frame) frames.get(41)).favoritesGridShown);

            service.onStartCommand(widgetCommand(41, "favorites"), 0, 5);
            assertTrue(((AtlasMediaWidgetProvider.Frame) frames.get(41)).favoritesGridShown);
            service.onStartCommand(widgetCommand(41, "dismiss_favorites"), 0, 6);
            assertFalse(((AtlasMediaWidgetProvider.Frame) frames.get(41)).favoritesGridShown);
            assertEquals(0, service.widgetFavoriteStations(41).size());
        } finally {
            controller.destroy();
        }
    }

    @Test public void sameTrackArtworkUpdateAndBufferingKeepTheWidgetCard() throws Exception {
        new Prefs(context).setWidgetMode(true);
        bind(41);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            service.onBridgeState(MediaBridgeClient.State.CONNECTED, "");
            service.onSnapshot(usbSnapshot(1, 3, "Track", "content://cover/a", 1));
            Bitmap cover = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            service.onArtwork((long) field(service, "expectedArtworkToken"), cover);
            var frames = (java.util.Map<?, ?>) field(service, "widgetFrames");
            Object frame = frames.get(41);

            // A seek on a real player: buffering and a re-published cover for the same track.
            service.onSnapshot(usbSnapshot(2, android.media.session.PlaybackState.STATE_BUFFERING,
                    "Track", "content://cover/b", 2));
            assertSame(cover, field(service, "currentArtwork"));
            assertSame("no full redraw while the control state is unchanged", frame, frames.get(41));

            service.onSnapshot(usbSnapshot(3, 3, "Next track", "content://cover/c", 3));
            assertNull("a new track never shows the previous cover", field(service, "currentArtwork"));
        } finally {
            controller.destroy();
        }
    }

    @Test public void transientPlaybackStatesKeepThePauseControl() {
        for (int state : new int[]{3, 4, 5, 6, 8, 9, 10, 11}) {
            assertTrue(String.valueOf(state), usbSnapshot(1, state, "T", "", 0).isPlaying());
        }
        for (int state : new int[]{0, 1, 2, 7}) {
            assertFalse(String.valueOf(state), usbSnapshot(1, state, "T", "", 0).isPlaying());
        }
        assertEquals(10_000L, ProgressEstimator.estimate(10_000L, 180_000L, 1L, 1f,
                android.media.session.PlaybackState.STATE_BUFFERING, 5_000L));
    }

    private MediaSnapshot usbSnapshot(long generation, int state, String title, String artwork,
            long revision) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, generation, generation, true, 0, "",
                MediaSource.Id.USB, "", List.of(), "usb", "USB", title, title, "Artist", "",
                180000, 10000, 1, 1, state, 0, "", 0,
                MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_PAUSE, artwork, revision);
    }

    private List<RadioStation> favoriteStations(int count) {
        var stations = new java.util.ArrayList<RadioStation>();
        for (int index = 0; index < count; index++) {
            stations.add(new RadioStation("s" + index, 87_500 + index * 100, "", 1, "FM",
                    "Station " + index, "", "", "", 0, 0, "", true, ""));
        }
        return stations;
    }

    private MediaSnapshot radioSnapshot() {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.RADIO, "", List.of(), "radio", "Radio", "", "Station 1", "", "",
                0, 0, 0, 1, 3, 0, "", 0,
                MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_TUNE_RADIO, "", 0);
    }

    private MediaSnapshot customActionSnapshot(MediaSource.Id source, String owner) {
        String self = context.getPackageName();
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                source, "", List.of(), owner, "Player", "one", "Track", "Artist", "",
                180000, 0, 0, 1, 3, 0, "", 0, MediaBridgeContract.CAP_PLAY, "", 0,
                List.of(new MediaCustomAction("LIKE", "Нравится", R.drawable.ic_transport_play, self),
                        new MediaCustomAction("SHUFFLE", "", R.drawable.ic_transport_previous, self),
                        new MediaCustomAction("REPEAT", "", R.drawable.ic_transport_pause, self),
                        new MediaCustomAction("FOREIGN", "", R.drawable.ic_transport_play, "other")));
    }

    // Icons are trimmed to their painted pixels, which needs real drawing.
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void playerCustomActionsBecomeCardButtonsAndWidgetTargets() {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        MediaSnapshot snapshot = customActionSnapshot(MediaSource.Id.ONLINE, context.getPackageName());
        assertEquals(List.of("LIKE", "SHUFFLE", "REPEAT"),
                snapshot.customActions.stream().map(a -> a.action).toList());

        var frame = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context),
                options, 41, snapshot, null, true, listener);
        assertEquals("LIKE", frame.card.widgetCustomAction(0).action);
        assertEquals("SHUFFLE", frame.card.widgetCustomAction(1).action);
        assertNull(frame.card.widgetCustomAction(2));
        assertEquals(View.VISIBLE, frame.card.widgetTarget("custom_0").getVisibility());
        assertEquals("Нравится", frame.card.widgetTarget("custom_0").getContentDescription());
        assertTrue(frame.card.widgetBounds(frame.card.widgetTarget("custom_0")).centerX()
                > frame.card.widgetBounds(frame.card.widgetTarget("sources")).centerX());

        Intent like = Shadows.shadowOf(
                AtlasMediaWidgetProvider.customActionClick(context, 41, "LIKE")).getSavedIntent();
        assertEquals(new ComponentName(context, OverlayService.class), like.getComponent());
        assertEquals("custom", like.getStringExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL));
        assertEquals("LIKE", like.getStringExtra(AtlasMediaWidgetProvider.EXTRA_CUSTOM_ACTION));
        assertNotEquals(like.getData(), Shadows.shadowOf(AtlasMediaWidgetProvider
                .customActionClick(context, 41, "SHUFFLE")).getSavedIntent().getData());

        Prefs prefs = new Prefs(context);
        prefs.putPlayerActionsCount(3);
        var three = new AtlasMediaWidgetProvider.Frame(context, prefs,
                options, 41, snapshot, null, true, listener);
        assertEquals("REPEAT", three.card.widgetCustomAction(2).action);
        prefs.putPlayerActionsCount(0);
        var none = new AtlasMediaWidgetProvider.Frame(context, prefs,
                options, 41, snapshot, null, true, listener);
        assertNull(none.card.widgetCustomAction(0));
        prefs.putPlayerActionsCount(2);
        List<String> published = List.of("LIKE", "SHUFFLE", "REPEAT");
        prefs.putPlayerActionRules(context.getPackageName(), published, List.of("LIKE"),
                List.of("REPEAT", "LIKE", "SHUFFLE"));
        var filtered = new AtlasMediaWidgetProvider.Frame(context, prefs,
                options, 41, snapshot, null, true, listener);
        assertEquals("REPEAT", filtered.card.widgetCustomAction(0).action);
        assertEquals("SHUFFLE", filtered.card.widgetCustomAction(1).action);
        assertNull(filtered.card.widgetCustomAction(2));
        prefs.putPlayerActionRules(context.getPackageName(), published, List.of(), published);
        prefs.putPlayerActionsCount(Prefs.DEFAULT_PLAYER_ACTIONS);

        var radio = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context),
                options, 41, customActionSnapshot(MediaSource.Id.RADIO, context.getPackageName()),
                null, true, listener);
        assertNull(radio.card.widgetCustomAction(0));
        assertEquals(View.GONE, radio.card.widgetTarget("custom_0").getVisibility());

        MediaSnapshot foreign = customActionSnapshot(MediaSource.Id.ONLINE, "other");
        assertEquals(List.of("FOREIGN"), foreign.customActions.stream().map(a -> a.action).toList());
    }

    private Intent widgetControl(String action) {
        return Shadows.shadowOf(AtlasMediaWidgetProvider.click(context, 41, action, false)).getSavedIntent();
    }

    @Test public void sourceChooserRendersAndRoutesSelectionInsideWidget() {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        var frame = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context),
                options, 41, sourceSnapshot(), null, true, true, listener);
        assertEquals(View.VISIBLE, frame.card.widgetTarget("source_chooser").getVisibility());
        assertTrue(frame.progressBounds.isEmpty());
        assertTrue(frame.card.widgetSourceOption(MediaSource.Id.RADIO).isEnabled());
        assertFalse(frame.card.widgetSourceOption(MediaSource.Id.BT).isEnabled());
        assertFalse(frame.card.widgetSourceOption(MediaSource.Id.ONLINE).isEnabled());
        View rendered = frame.views.apply(context, null);
        assertEquals(3, ((ViewGroup) rendered.findViewById(R.id.widget_targets)).getChildCount());
        PendingIntent source = AtlasMediaWidgetProvider.click(context, 41, "sources", true);
        Intent intent = Shadows.shadowOf(source).getSavedIntent();
        assertEquals(new ComponentName(context, OverlayService.class), intent.getComponent());
        assertEquals(AtlasMediaWidgetProvider.ACTION_COMMAND, intent.getAction());
        assertNotEquals(source, AtlasMediaWidgetProvider.click(context, 41, "source_RADIO", true));
    }

    @Test public void sourceChoiceChangesOnlyItsWidgetAndIgnoresUnavailableChoices() throws Exception {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        bind(41);
        bind(42);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            service.onBridgeState(MediaBridgeClient.State.CONNECTED, "");
            service.onSnapshot(sourceSnapshot());
            service.onStartCommand(widgetCommand(41, "sources"), 0, 2);
            var frames = (java.util.Map<?, ?>) field(service, "widgetFrames");
            assertEquals(View.VISIBLE, ((AtlasMediaWidgetProvider.Frame) frames.get(41))
                    .card.widgetTarget("source_chooser").getVisibility());
            assertEquals(View.GONE, ((AtlasMediaWidgetProvider.Frame) frames.get(42))
                    .card.widgetTarget("source_chooser").getVisibility());
            service.onStartCommand(widgetCommand(41, "source_BT"), 0, 3);
            assertEquals(View.VISIBLE, ((AtlasMediaWidgetProvider.Frame) frames.get(41))
                    .card.widgetTarget("source_chooser").getVisibility());
            service.onStartCommand(widgetCommand(41, "source_RADIO"), 0, 4);
            assertEquals(View.GONE, ((AtlasMediaWidgetProvider.Frame) frames.get(41))
                    .card.widgetTarget("source_chooser").getVisibility());
        } finally {
            controller.destroy();
        }
    }

    @Test public void unavailableWidgetDistinguishesMissingDataFromConnectionLoss() {
        for (boolean connected : new boolean[]{false, true}) {
            var frame = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context),
                    new Bundle(), 41, null, null, connected, listener);
            assertNotNull(find(frame.card, connected ? "Нет данных" : "Нет соединения"));
            assertFalse(frame.card.widgetTarget("PLAY_PAUSE").isEnabled());
        }
    }

    @Test public void widgetServiceKeepsBridgeWithoutOverlayPermissionOrHomeObservation() throws Exception {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        bind(41);
        bind(42);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            assertNull(field(service, "card"));
            assertNull(field(service, "foregroundDetector"));
            assertEquals(false, field(service, "visibilityReceiverRegistered"));
            assertNotNull(field(service, "bridge"));
            assertEquals(2, ((java.util.Map<?, ?>) field(service, "widgetFrames")).size());
            assertFalse(prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, true));
            var inFlight = OverlayService.class.getDeclaredField("foregroundQueryInFlight");
            inFlight.setAccessible(true);
            inFlight.set(service, true);
            var apply = OverlayService.class.getDeclaredMethod("applyForegroundResult", boolean.class, long.class);
            apply.setAccessible(true);
            apply.invoke(service, true, 0L);
            assertEquals(false, field(service, "foregroundQueryInFlight"));
            assertNull(field(service, "card"));
            SnapshotReducer reducer = (SnapshotReducer) field(service, "reducer");
            reducer.onConnected(android.os.SystemClock.elapsedRealtime());
            service.onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY));
            var frames = (java.util.Map<?, ?>) field(service, "widgetFrames");
            assertTrue(((AtlasMediaWidgetProvider.Frame)frames.get(41))
                    .card.widgetTarget("PLAY_PAUSE").isEnabled());
            var lastSnapshot = OverlayService.class.getDeclaredField("lastWidgetSnapshotAt");
            lastSnapshot.setAccessible(true);
            lastSnapshot.setLong(service, android.os.SystemClock.elapsedRealtime() - 21_000L);
            var render = OverlayService.class.getDeclaredMethod("renderWidgets", boolean.class);
            render.setAccessible(true);
            render.invoke(service, false);
            assertFalse(((AtlasMediaWidgetProvider.Frame)frames.get(41))
                    .card.widgetTarget("PLAY_PAUSE").isEnabled());
            service.onSnapshot(snapshot(MediaBridgeContract.CAP_PLAY));
            assertTrue(((AtlasMediaWidgetProvider.Frame)frames.get(42))
                    .card.widgetTarget("PLAY_PAUSE").isEnabled());
        } finally {
            controller.destroy();
        }
    }

    @Test public void widgetRefreshInOverlayModeKeepsVisibleOverlayCard() throws Exception {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(false);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        bind(41);
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .declareActionUnbindable(MediaBridgeContract.SERVICE_ACTION);
        var controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        try {
            MediaCardView card = new AtlasMediaWidgetProvider.Frame(context, prefs, new Bundle(), 41,
                    null, null, false, listener).card;
            var cardField = OverlayService.class.getDeclaredField("card");
            cardField.setAccessible(true);
            cardField.set(service, card);
            service.onStartCommand(new Intent(OverlayService.ACTION_WIDGET_REFRESH), 0, 1);
            assertSame(card, field(service, "card"));
            service.onStartCommand(new Intent(OverlayService.ACTION_REFRESH_STYLE), 0, 2);
            assertNull(field(service, "card"));
        } finally {
            controller.destroy();
        }
    }

    @Test public void widgetProgressChangesOnlyWhilePlaybackAdvances() {
        long now = 100_000L;
        MediaSnapshot paused = timedSnapshot(2, now);
        assertEquals(10L, AtlasMediaWidgetProvider.progressSecond(paused, now));
        assertEquals(10L, AtlasMediaWidgetProvider.progressSecond(paused, now + 5_000L));
        MediaSnapshot playing = timedSnapshot(MediaSnapshot.STATE_PLAYING, now);
        assertEquals(10L, AtlasMediaWidgetProvider.progressSecond(playing, now + 999L));
        assertEquals(11L, AtlasMediaWidgetProvider.progressSecond(playing, now + 1_000L));
        assertEquals(-1L, AtlasMediaWidgetProvider.progressSecond(null, now));
    }

    @Test public void liveStreamKeepsTheProgressRowWithoutSeeking() {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        MediaSnapshot live = new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), "test", "Player", "", "Song", "Station", "",
                0, -1, 0, 1, MediaSnapshot.STATE_PLAYING, 0, "", 0,
                MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_SEEK, "", 0);
        var frame = new AtlasMediaWidgetProvider.Frame(context, new Prefs(context), options, 41,
                live, null, true, listener);
        assertFalse(frame.progressBounds.isEmpty());
        assertEquals("∞", ((TextView) frame.card.widgetTarget("duration")).getText().toString());
        assertEquals("0:00", ((TextView) frame.card.widgetTarget("elapsed")).getText().toString());
        ViewGroup targets = frame.views.apply(context, null).findViewById(R.id.widget_targets);
        for (int index = 0; index < targets.getChildCount(); index++) {
            CharSequence description = targets.getChildAt(index)
                    .findViewById(R.id.widget_target).getContentDescription();
            assertFalse(String.valueOf(description).startsWith("Перемотать"));
            assertNotEquals("Точная перемотка", String.valueOf(description));
        }
    }

    @Test public void compactCardWithoutMediaKeepsThePlaceholderInTheThumbnailSlot() throws Exception {
        Prefs prefs = new Prefs(context);
        prefs.putInt(Prefs.KEY_CARD_STYLE, CardStyle.COMPACT.preferenceValue);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 500);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300);
        MediaSnapshot empty = new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.CPAA, "", List.of(), "", "", "", "", "", "",
                0, -1, 0, 0, 0, 0, "", 0, 0, "", 0);
        var frame = new AtlasMediaWidgetProvider.Frame(context, prefs, options, 41,
                empty, null, true, listener);
        assertEquals(View.GONE, ((View) field(frame.card, "placeholder")).getVisibility());
        assertEquals(View.VISIBLE, ((View) field(frame.card, "artworkThumbnail")).getVisibility());
        assertTrue(frame.progressBounds.isEmpty());
    }

    private MediaSnapshot timedSnapshot(int playbackState, long updateElapsedRealtime) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), "test", "Player", "one", "Track", "Artist", "",
                180000, 10000, updateElapsedRealtime, 1, playbackState, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }

    private Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private void bind(int id) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasMediaWidgetProvider.class);
        info.initialLayout = R.layout.media_widget_empty;
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(id, info);
    }

    private Intent configure(int id) {
        return new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    }

    private Intent widgetCommand(int id, String control) {
        return new Intent(context, OverlayService.class)
                .setAction(AtlasMediaWidgetProvider.ACTION_COMMAND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AtlasMediaWidgetProvider.EXTRA_CONTROL, control);
    }

    private MediaSnapshot sourceSnapshot() {
        MediaSnapshot base = snapshot(MediaBridgeContract.CAP_PLAY);
        return new MediaSnapshot(base.protocolVersion, base.generation, base.timestamp,
                base.backendConnected, base.backendErrorCode, base.backendErrorMessage,
                base.audioSource, base.appSource, List.of(
                new MediaSource(MediaSource.Id.RADIO, true, true, false,
                        MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.BT, false, false, false,
                        MediaBridgeContract.CAP_SET_SOURCE),
                new MediaSource(MediaSource.Id.ONLINE, true, true, true,
                        MediaBridgeContract.CAP_SET_SOURCE)),
                base.ownerPackage, base.ownerApp, base.mediaId, base.title, base.artist,
                base.album, base.duration, base.position, base.updateElapsedRealtime,
                base.speed, base.playbackState, base.playbackErrorCode,
                base.playbackErrorMessage, base.playbackActions, base.capabilities,
                base.artworkUri, base.artworkRevision);
    }

    private static View find(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = find(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private MediaSnapshot snapshot(long capabilities) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), "test", "Player", "one", "Track", "Artist", "",
                180000, 10000, 0, 1, 2, 0, "", 0, capabilities, "", 0);
    }
}
