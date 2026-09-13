package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, shadows = MainActivityTest.StorageEnvironment.class)
public final class MainActivityTest {
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
