package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
            var controller = Robolectric.buildActivity(MainActivity.class, configure(id)).create();
            assertTrue(controller.get().isFinishing());
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(controller.get()).getResultCode());
            controller.destroy();
        }
    }

    @Test public void configureDoneAndBackKeepOriginalIdAndImmediateSettings() {
        bind(41);
        for (boolean done : new boolean[]{false, true}) {
            bind(41);
            var controller = Robolectric.buildActivity(MainActivity.class, configure(41)).create();
            MainActivity activity = controller.get();
            assertFalse("configure must stay open", activity.isFinishing());
            assertTrue("provider ownership", AtlasMediaWidgetProvider.owns(activity, 41));
            new Prefs(context).setWidgetMode(done);
            if (done) {
                View doneButton = find(activity.findViewById(android.R.id.content), "Готово");
                assertNotNull("done button", doneButton);
                doneButton.performClick();
            }
            else activity.onBackPressed();
            assertEquals(done ? Activity.RESULT_OK : Activity.RESULT_CANCELED,
                    Shadows.shadowOf(activity).getResultCode());
            assertEquals(41, Shadows.shadowOf(activity).getResultIntent()
                    .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0));
            assertEquals(done, new Prefs(context).isWidgetMode());
            assertArrayEquals(new int[]{41}, AtlasMediaWidgetProvider.ids(context));
            controller.destroy();
        }
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
        assertEquals(View.GONE, find(root, "Разрешить поверх окон").getVisibility());
        assertEquals(View.VISIBLE, find(root, "Разрешить доступ к уведомлениям (медиа)").getVisibility());
        assertEquals(View.VISIBLE, find(root, "Добавить виджет").getVisibility());
        assertEquals(View.GONE, find(root, "Размер карточки").getVisibility());
        assertEquals(View.VISIBLE, find(root, "Затемнение обложки").getVisibility());
        controller.destroy();
        prefs.setWidgetMode(false);
        controller = Robolectric.buildActivity(MainActivity.class).create();
        root = controller.get().findViewById(android.R.id.content);
        assertEquals(View.VISIBLE, find(root, "Размер карточки").getVisibility());
        assertEquals(View.VISIBLE, find(root, "Разрешить поверх окон").getVisibility());
        controller.destroy();
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
