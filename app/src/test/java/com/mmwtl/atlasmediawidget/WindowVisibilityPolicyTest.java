package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;

import android.view.accessibility.AccessibilityWindowInfo;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class WindowVisibilityPolicyTest {
    private static final int WIDTH = 1440;
    private static final int HEIGHT = 1920;
    private static final Set<String> HOME_PACKAGES = Set.of("launcher", "settings");
    private static final Set<String> HOME_COMPONENTS = Set.of(
            "launcher/HomeActivity",
            "settings/FallbackHome"
    );

    @Test
    public void exactHomeComponentIsVisible() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_VISIBLE,
                List.of(window("launcher", "HomeActivity", true, true,
                        0, 0, WIDTH, HEIGHT, 0)),
                activity("launcher", "HomeActivity"),
                "launcher",
                "HomeActivity"
        );
    }

    @Test
    public void focusedFreeformWindowAboveHomeRemainsVisible() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_VISIBLE,
                List.of(
                        window("launcher", "HomeActivity", true, false,
                                0, 0, WIDTH, HEIGHT, 0),
                        window("maps", "MapActivity", true, true,
                                180, 300, 1180, 1450, 4)
                ),
                activity("maps", "MapActivity"),
                "maps",
                "MapActivity"
        );
    }

    @Test
    public void fullscreenActivityAboveHomeHidesPanel() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                List.of(
                        window("launcher", "HomeActivity", false, false,
                                0, 0, WIDTH, HEIGHT, 0),
                        window("video", "PlayerActivity", true, true,
                                0, 0, WIDTH, HEIGHT, 5)
                ),
                activity("video", "PlayerActivity"),
                "video",
                "PlayerActivity"
        );
    }

    @Test
    public void fallbackHomeComponentIsVisible() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_VISIBLE,
                List.of(window("settings", "FallbackHome", true, true,
                        0, 0, WIDTH, HEIGHT, 3)),
                activity("settings", "FallbackHome"),
                "settings",
                "FallbackHome"
        );
    }

    @Test
    public void settingsActivityIsNotMistakenForFallbackHome() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                List.of(window("settings", "Settings", true, true,
                        0, 0, WIDTH, HEIGHT, 3)),
                activity("settings", "Settings"),
                "settings",
                "Settings"
        );
    }

    @Test
    public void freeformSettingsAboveRealHomeRemainsVisible() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_VISIBLE,
                List.of(
                        window("launcher", "HomeActivity", false, false,
                                0, 0, WIDTH, HEIGHT, 0),
                        window("settings", "Settings", true, true,
                                250, 300, 1100, 1500, 4)
                ),
                activity("settings", "Settings"),
                "settings",
                "Settings"
        );
    }

    @Test
    public void arbitraryFullscreenThirdPartyOverlayHidesPanel() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                List.of(
                        window("launcher", "HomeActivity", false, false,
                                0, 0, WIDTH, HEIGHT, 0),
                        new WindowObservation(
                                "third.party.overlay", "Overlay", 3,
                                true, true, 8, 0, 0, WIDTH, HEIGHT)
                ),
                null,
                "third.party.overlay",
                "Overlay"
        );
    }

    @Test
    public void arbitraryFullscreenSystemWindowWithoutPackageHidesPanel() {
        assertDecision(
                WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                List.of(
                        window("launcher", "HomeActivity", false, false,
                                0, 0, WIDTH, HEIGHT, 0),
                        new WindowObservation(
                                "", "SystemDialog", AccessibilityWindowInfo.TYPE_SYSTEM,
                                true, true, 8, 0, 0, WIDTH, HEIGHT)
                ),
                null,
                "",
                ""
        );
    }

    @Test
    public void headUnitSpecialPackagesAndLauncherEntryHidePanel() {
        String[] packages = {
                "com.salat.gsplit",
                "com.geely.hvac",
                "com.geely.oneosphone"
        };
        for (String packageName : packages) {
            assertEquals(packageName, WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                    decisionWithForeground(packageName, "Activity"));
        }
        assertEquals(
                WindowVisibilityPolicy.Decision.HOME_HIDDEN,
                decisionWithForeground(
                        "com.salat.gbinder",
                        "com.salat.gbinder.features.launcher.LauncherEntryActivity"
                )
        );
    }

    private static WindowVisibilityPolicy.Decision decisionWithForeground(
            String packageName,
            String className
    ) {
        return WindowVisibilityPolicy.evaluate(
                List.of(window("launcher", "HomeActivity", false, false,
                        0, 0, WIDTH, HEIGHT, 0)),
                WIDTH,
                HEIGHT,
                HOME_PACKAGES,
                HOME_COMPONENTS,
                activity(packageName, className),
                packageName,
                className,
                "com.mmwtl.atlasmediawidget"
        );
    }

    private static WindowObservation window(
            String packageName,
            String className,
            boolean active,
            boolean focused,
            int left,
            int top,
            int right,
            int bottom,
            int layer
    ) {
        return new WindowObservation(
                packageName,
                className,
                AccessibilityWindowInfo.TYPE_APPLICATION,
                active,
                focused,
                layer,
                left,
                top,
                right,
                bottom
        );
    }

    private static ForegroundEventTracker.VisibleActivity activity(
            String packageName,
            String className
    ) {
        return new ForegroundEventTracker.VisibleActivity(packageName, className, 1L);
    }

    private static void assertDecision(
            WindowVisibilityPolicy.Decision expected,
            List<WindowObservation> windows,
            ForegroundEventTracker.VisibleActivity foreground,
            String eventPackage,
            String eventClass
    ) {
        assertEquals(expected, WindowVisibilityPolicy.evaluate(
                windows,
                WIDTH,
                HEIGHT,
                HOME_PACKAGES,
                HOME_COMPONENTS,
                foreground,
                eventPackage,
                eventClass,
                "com.mmwtl.atlasmediawidget"
        ));
    }
}
