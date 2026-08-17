package com.mmwtl.atlasmediawidget;

import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;
import java.util.Set;

/** Decides whether HOME is actually visible from a snapshot of interactive windows. */
final class WindowVisibilityPolicy {
    enum Decision {
        HOME_VISIBLE,
        HOME_HIDDEN,
        UNKNOWN
    }

    private static final int FULLSCREEN_PERCENT = 85;

    private WindowVisibilityPolicy() {
    }

    static Decision evaluate(
            List<WindowObservation> windows,
            int displayWidth,
            int displayHeight,
            Set<String> homePackages,
            Set<String> homeComponents,
            ForegroundEventTracker.VisibleActivity foreground,
            String eventPackage,
            String eventClass,
            String ownPackage
    ) {
        String foregroundPackage = value(eventPackage);
        String foregroundClass = value(eventClass);
        if (foregroundPackage.isEmpty() && foreground != null) {
            foregroundPackage = foreground.packageName;
            foregroundClass = foreground.className;
        } else if (foregroundClass.isEmpty() && foreground != null
                && foregroundPackage.equals(foreground.packageName)) {
            foregroundClass = foreground.className;
        }

        // These packages/classes are known to represent a visible shell transition on the
        // tested head unit even when AccessibilityWindowInfo reports bad bounds or state.
        if (HeadUnitWindowRules.forceHide(foregroundPackage, foregroundClass)) {
            return Decision.HOME_HIDDEN;
        }
        if (windows == null || windows.isEmpty()
                || displayWidth <= 0 || displayHeight <= 0
                || homePackages == null || homePackages.isEmpty()) {
            return Decision.UNKNOWN;
        }

        for (WindowObservation window : windows) {
            if (window == null || !window.launcherAppListVisible
                    || !homePackages.contains(window.packageName)) {
                continue;
            }
            if (window.active || window.focused || window.packageName.equals(foregroundPackage)) {
                return Decision.HOME_HIDDEN;
            }
        }

        boolean foregroundIsKnownHome = isHomeComponent(
                foregroundPackage, foregroundClass, homeComponents);

        boolean launcherPresent = false;
        int highestLauncherLayer = Integer.MIN_VALUE;
        boolean nonHomeApplicationPresent = false;
        boolean activeOrFocusedWindowPresent = false;
        for (WindowObservation window : windows) {
            if (window == null) {
                continue;
            }
            activeOrFocusedWindowPresent |= window.active || window.focused;
            boolean homeWindow = isHomeWindow(
                    window,
                    homePackages,
                    homeComponents,
                    foregroundPackage,
                    foregroundClass
            );
            if (homeWindow) {
                launcherPresent = true;
                highestLauncherLayer = Math.max(highestLauncherLayer, window.layer);
            }
        }

        for (WindowObservation window : windows) {
            if (window == null) {
                continue;
            }
            boolean homeWindow = isHomeWindow(
                    window,
                    homePackages,
                    homeComponents,
                    foregroundPackage,
                    foregroundClass
            );
            if (homeWindow) {
                continue;
            }

            boolean applicationWindow = window.type == AccessibilityWindowInfo.TYPE_APPLICATION;
            boolean ownPassiveOverlay = ownPackage.equals(window.packageName)
                    && !applicationWindow && !window.active && !window.focused;
            if (ownPassiveOverlay) {
                continue;
            }
            if (applicationWindow && !window.packageName.isEmpty()) {
                nonHomeApplicationPresent = true;
            }

            boolean fullScreen = coversPercent(
                    window.width(), displayWidth, FULLSCREEN_PERCENT)
                    && coversPercent(window.height(), displayHeight, FULLSCREEN_PERCENT);
            boolean aboveLauncher = highestLauncherLayer == Integer.MIN_VALUE
                    || window.layer >= highestLauncherLayer;
            boolean foregroundWindow = window.active || window.focused
                    || (!activeOrFocusedWindowPresent && !foregroundPackage.isEmpty()
                    && foregroundPackage.equals(window.packageName));

            // Do not require a package name here. Some system windows on the head unit expose
            // real bounds and active/focused state but no root package at all.
            if (fullScreen && aboveLauncher && foregroundWindow) {
                return Decision.HOME_HIDDEN;
            }
            // The known firmware windows get an additional state-independent guard. Their
            // activity lifecycle and bounds are not reliable during shell transitions.
            if (foregroundWindow
                    && HeadUnitWindowRules.forceHide(window.packageName, window.className)) {
                return Decision.HOME_HIDDEN;
            }
        }

        // A focused non-HOME activity from a package that also exposes FallbackHome must not be
        // mistaken for the launcher merely because the package appears in CATEGORY_HOME.
        if (!foregroundPackage.isEmpty()
                && homePackages.contains(foregroundPackage)
                && !foregroundIsKnownHome
                && !foregroundClass.isEmpty()
                && !launcherPresent) {
            return Decision.HOME_HIDDEN;
        }
        if (launcherPresent || foregroundIsKnownHome) {
            return Decision.HOME_VISIBLE;
        }
        if (!foregroundPackage.isEmpty() || nonHomeApplicationPresent) {
            return Decision.HOME_HIDDEN;
        }
        return Decision.UNKNOWN;
    }

    private static boolean isHomeWindow(
            WindowObservation window,
            Set<String> homePackages,
            Set<String> homeComponents,
            String foregroundPackage,
            String foregroundClass
    ) {
        if (isHomeComponent(window.packageName, window.className, homeComponents)) {
            return true;
        }
        if (!homePackages.contains(window.packageName)) {
            return false;
        }
        // A launcher window left below a freeform activity is still HOME. A window from the
        // foreground HOME package is only HOME when its exact component is known.
        return !window.packageName.equals(foregroundPackage)
                || foregroundClass.isEmpty()
                || isHomeComponent(foregroundPackage, foregroundClass, homeComponents);
    }

    private static boolean isHomeComponent(
            String packageName,
            String className,
            Set<String> homeComponents
    ) {
        return homeComponents != null
                && homeComponents.contains(componentKey(packageName, className));
    }

    static String componentKey(String packageName, String className) {
        String packageValue = value(packageName);
        String classValue = value(className);
        if (classValue.startsWith(".")) {
            classValue = packageValue + classValue;
        }
        return packageValue + "/" + classValue;
    }

    private static boolean coversPercent(int size, int displaySize, int percent) {
        return (long) size * 100L >= (long) displaySize * percent;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
