package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowAlertDialog;
import org.junit.rules.TemporaryFolder;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, shadows = MainActivityTest.StorageEnvironment.class)
public final class MainActivityTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void settingsTabsSeparateSectionsAndKeepSelectionAcrossRecreate() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        try {
            View root = controller.get().findViewById(android.R.id.content);
            for (String tab : new String[]{"Карточка", "Медиа", "Радио", "Система"}) {
                assertTrue(tab, findLabel(root, tab) != null);
            }
            assertTrue(shown(findLabel(root, "Режим отображения")));
            assertFalse(shown(findLabel(root, "Медиасервис OneOS")));

            findLabel(root, "Медиа").performClick();
            assertTrue(shown(findLabel(root, "Медиасервис OneOS")));
            assertFalse(shown(findLabel(root, "Режим отображения")));
            assertFalse(findLabel(root, "Radio").isEnabled());

            findLabel(root, "Система").performClick();
            List<String> labels = new ArrayList<>();
            collectShownLabels(root, labels);
            MainActivity activity = controller.get();
            assertTrue(labels.indexOf("Резервная копия настроек")
                    < labels.indexOf("Диагностика OneOS"));
            assertTrue(labels.indexOf("Диагностика OneOS")
                    < labels.indexOf(activity.getString(R.string.scale_title)));

            android.os.Bundle state = new android.os.Bundle();
            controller.saveInstanceState(state);
            ActivityController<MainActivity> restored =
                    Robolectric.buildActivity(MainActivity.class).create(state);
            root = restored.get().findViewById(android.R.id.content);
            assertTrue(shown(findLabel(root, "Резервная копия настроек")));
            assertFalse(shown(findLabel(root, "Режим отображения")));
            restored.destroy();
        } finally {
            controller.pause().stop().destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    @Test
    public void fineTuningStartsCollapsedAndExpandsFromHeader() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        try {
            View root = controller.get().findViewById(android.R.id.content);
            assertFalse(shown(findLabel(root, "Размер названия")));
            ((View) findLabel(root, "Текст и отступы").getParent().getParent()).performClick();
            assertTrue(shown(findLabel(root, "Размер названия")));
            assertFalse(shown(findLabel(root, "Высота нижней панели")));
        } finally {
            controller.pause().stop().destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    @Test
    public void radioTabCollectsCatalogAndStaysUnavailableUntilBridgeSettings() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        try {
            View root = controller.get().findViewById(android.R.id.content);
            findLabel(root, "Радио").performClick();
            List<String> labels = new ArrayList<>();
            collectShownLabels(root, labels);
            int navigationIndex = labels.indexOf("Переключать радио без поиска по эфиру");
            int catalogIndex = labels.indexOf("Каталог станций");
            int exportIndex = labels.indexOf("Экспортировать каталог радио (ZIP)");
            int importIndex = labels.indexOf("Импортировать каталог радио (ZIP)");
            assertTrue(navigationIndex >= 0);
            assertTrue(catalogIndex > navigationIndex);
            assertTrue(exportIndex > catalogIndex);
            assertTrue(importIndex > exportIndex);
            assertFalse(labels.contains("Медиасервис OneOS"));
            assertFalse(findLabel(root, "Экспортировать каталог радио (ZIP)").isEnabled());
            assertFalse(findLabel(root, "Импортировать каталог радио (ZIP)").isEnabled());
            assertFalse(findLabel(root, "Названия и обложки из каталога в карточке").isEnabled());
        } finally {
            controller.pause().stop().destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    @Test
    public void radioPickerResultIsStagedAfterBridgeStops() throws Exception {
        File radioZip = temporaryFolder.newFile("radio.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(radioZip))) {
            zip.putNextEntry(new ZipEntry("stations.csv"));
            zip.write("frequency_khz,name,band,cover\n98800,Test,FM,\n".getBytes());
            zip.closeEntry();
        }

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        MainActivity activity = controller.get();
        controller.pause().stop();
        ShadowLooper.runUiThreadTasks();
        Intent result = new Intent().setData(Uri.fromFile(radioZip));
        activity.onActivityResult(4103, MainActivity.RESULT_OK, result);

        Field executorField = MainActivity.class.getDeclaredField("ioExecutor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(activity);
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        ShadowAlertDialog shadowDialog = Shadows.shadowOf(dialog);
        assertEquals("Заменить каталог радио?", shadowDialog.getTitle());
        assertTrue(shadowDialog.getMessage().toString().contains("полностью заменён"));
        Field stagedField = MainActivity.class.getDeclaredField("pendingRadioImportFile");
        stagedField.setAccessible(true);
        File stagedFile = (File) stagedField.get(activity);
        assertTrue(stagedFile.isFile());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.runUiThreadTasks();
        assertFalse(dialog.isShowing());
        assertFalse(stagedFile.exists());

        controller.destroy();
        ShadowLooper.runUiThreadTasks();
    }

    @Test
    public void importButtonPicksFileAndImportsAfterConfirmation() throws Exception {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        context.getSharedPreferences("atlas_media_widget", android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
        Prefs source = new Prefs(context);
        source.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 13);
        File json = temporaryFolder.newFile("settings.json");
        java.nio.file.Files.write(json.toPath(), SettingsBackup.encode(context, source)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        source.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 10);

        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        MainActivity activity = controller.get();
        try {
            View root = activity.findViewById(android.R.id.content);
            findLabel(root, "Система").performClick();
            findLabel(root, "Импортировать настройки (ZIP / JSON)").performClick();
            org.robolectric.shadows.ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent picker = shadow.getNextStartedActivityForResult().intent;
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.getAction());
            assertTrue(picker.hasCategory(Intent.CATEGORY_OPENABLE));
            List<String> types = java.util.Arrays.asList(
                    picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
            assertTrue(types.contains("application/zip"));
            assertTrue(types.contains("application/json"));
            assertTrue(types.contains("*/*"));
            // The picker covers the activity, so its result arrives while the bridge is stopped.
            controller.pause().stop();
            ShadowLooper.runUiThreadTasks();
            activity.onActivityResult(4102, MainActivity.RESULT_OK,
                    new Intent().setData(Uri.fromFile(json)));
            drainIo(activity);

            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            assertEquals("Импортировать настройки?", Shadows.shadowOf(dialog).getTitle());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.runUiThreadTasks();
            // Only wait for the worker: its success post recreates the activity.
            io(activity).submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertEquals(13, new Prefs(context).getInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 10));
        } finally {
            controller.destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void exportSharesDatedZipThroughOwnProvider() throws Exception {
        File zip = temporaryFolder.newFile("backup.zip");
        java.nio.file.Files.write(zip.toPath(), new byte[]{0x50, 0x4B, 3, 4});
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        MainActivity activity = controller.get();
        try {
            String name = MainActivity.datedExportName(FullSettingsBackup.ZIP_FILE_NAME);
            assertTrue(name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_"
                    + "AtlasMediaWidget-backup\\.zip"));
            Uri uri = BackupProvider.publish(activity, name, zip);
            activity.shareExport(uri, name, "Экспорт настроек");

            Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
            assertTrue((chooser.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals(Intent.ACTION_SEND, send.getAction());
            assertEquals("application/zip", send.getType());
            assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            assertEquals(uri, send.getParcelableExtra(Intent.EXTRA_STREAM));
            assertEquals(uri, send.getClipData().getItemAt(0).getUri());
            assertEquals("content", uri.getScheme());
            assertEquals(activity.getPackageName() + ".backup", uri.getAuthority());

            BackupProvider provider = org.robolectric.Robolectric
                    .setupContentProvider(BackupProvider.class, uri.getAuthority());
            assertEquals("application/zip", provider.getType(uri));
            try (android.database.Cursor cursor = provider.query(uri, null, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.OpenableColumns.DISPLAY_NAME)));
                assertEquals(4L, cursor.getLong(cursor.getColumnIndexOrThrow(
                        android.provider.OpenableColumns.SIZE)));
            }

            String radioName = MainActivity.datedExportName(MainActivity.RADIO_ZIP_FILE_NAME);
            BackupProvider.publish(activity, radioName, zip);
            assertTrue("older exports are dropped", provider.query(uri, null, null, null, null) == null);
        } finally {
            controller.pause().stop().destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    private static ExecutorService io(MainActivity activity) throws Exception {
        Field executorField = MainActivity.class.getDeclaredField("ioExecutor");
        executorField.setAccessible(true);
        return (ExecutorService) executorField.get(activity);
    }

    private static void drainIo(MainActivity activity) throws Exception {
        io(activity).submit(() -> {}).get(5, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @org.robolectric.annotation.Implements(android.os.Environment.class)
    public static class StorageEnvironment extends org.robolectric.shadows.ShadowEnvironment {
        @org.robolectric.annotation.Implementation(minSdk = 30)
        protected static boolean isExternalStorageManager() {
            return false;
        }
    }

    private static View findLabel(View view, String label) {
        if (view instanceof TextView text && label.contentEquals(text.getText())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findLabel(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectShownLabels(View view, List<String> labels) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView text) labels.add(text.getText().toString());
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collectShownLabels(group.getChildAt(i), labels);
            }
        }
    }

    static boolean shown(View view) {
        if (view == null) return false;
        for (View current = view; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) return false;
        }
        return true;
    }
}
