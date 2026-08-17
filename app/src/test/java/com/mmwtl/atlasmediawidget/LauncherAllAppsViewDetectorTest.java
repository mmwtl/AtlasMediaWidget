package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LauncherAllAppsViewDetectorTest {
    @Test
    public void recognizesOneOsAllAppsContainer() {
        assertTrue(LauncherAllAppsViewDetector.isAllAppsMarker(
                "com.android.launcher3:id/ll_all_apps_container",
                "android.widget.LinearLayout",
                ""
        ));
    }

    @Test
    public void recognizesStandardLauncherAllAppsContainer() {
        assertTrue(LauncherAllAppsViewDetector.isAllAppsMarker(
                "",
                "com.android.launcher3.allapps.AllAppsContainerView",
                ""
        ));
    }

    @Test
    public void recognizesOneOsAllAppsPageAndTab() {
        assertTrue(LauncherAllAppsViewDetector.isAllAppsMarker(
                "com.android.launcher3:id/page_view_app",
                "android.view.View",
                ""
        ));
        assertTrue(LauncherAllAppsViewDetector.isAllAppsMarker(
                "",
                "android.widget.TextView",
                "My APPs"
        ));
    }

    @Test
    public void ignoresHomeWorkspaceNodes() {
        assertFalse(LauncherAllAppsViewDetector.isAllAppsMarker(
                "com.android.launcher3:id/custom_workspace",
                "android.widget.ScrollView",
                ""
        ));
    }
}
