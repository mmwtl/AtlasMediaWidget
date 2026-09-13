package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.ScrollView;
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
    public void settingsResumeWithPermissionsFirstAndScaleLast() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        try {
            MainActivity activity = controller.get();
            ScrollView scroll = findScroll(activity.findViewById(android.R.id.content));
            ViewGroup settings = (ViewGroup) scroll.getChildAt(0);
            List<String> labels = new ArrayList<>();
            collectLabels(settings, labels);
            assertTrue(labels.indexOf(activity.getString(R.string.permissions_title))
                    < labels.indexOf("Медиасервис OneOS"));
            assertTrue(labels.indexOf("Медиасервис OneOS")
                    < labels.indexOf(activity.getString(R.string.appearance_title)));
            List<String> lastCard = new ArrayList<>();
            collectLabels(settings.getChildAt(settings.getChildCount() - 1), lastCard);
            assertEquals(activity.getString(R.string.scale_title), lastCard.get(0));
            assertFalse(findLabel(settings, "Radio").isEnabled());
        } finally {
            controller.pause().stop().destroy();
            ShadowLooper.runUiThreadTasks();
        }
    }

    @Test
    public void radioCatalogTransferCardIsInMediaSectionAndUnavailableUntilBridgeSettings() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume();
        try {
            MainActivity activity = controller.get();
            ScrollView scroll = findScroll(activity.findViewById(android.R.id.content));
            ViewGroup settings = (ViewGroup) scroll.getChildAt(0);
            List<String> labels = new ArrayList<>();
            collectLabels(settings, labels);
            int mediaIndex = labels.indexOf("Медиасервис OneOS");
            int exportIndex = labels.indexOf("Экспортировать каталог радио (ZIP)");
            int importIndex = labels.indexOf("Импортировать каталог радио (ZIP)");
            assertTrue(mediaIndex >= 0);
            assertTrue(exportIndex > mediaIndex);
            assertTrue(importIndex > exportIndex);
            assertFalse(findLabel(settings, "Экспортировать каталог радио (ZIP)").isEnabled());
            assertFalse(findLabel(settings, "Импортировать каталог радио (ZIP)").isEnabled());
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

    @org.robolectric.annotation.Implements(android.os.Environment.class)
    public static class StorageEnvironment extends org.robolectric.shadows.ShadowEnvironment {
        @org.robolectric.annotation.Implementation(minSdk = 30)
        protected static boolean isExternalStorageManager() {
            return false;
        }
    }

    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView scroll) return scroll;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                ScrollView found = findScroll(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
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

    private static void collectLabels(View view, List<String> labels) {
        if (view instanceof TextView text) labels.add(text.getText().toString());
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectLabels(group.getChildAt(i), labels);
        }
    }
}
