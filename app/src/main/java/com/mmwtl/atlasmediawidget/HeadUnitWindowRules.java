package com.mmwtl.atlasmediawidget;

import java.util.Set;

/** Firmware-specific hide rules for windows that report incomplete activity state. */
final class HeadUnitWindowRules {
    private static final Set<String> FORCE_HIDE_PACKAGES = Set.of(
            "com.salat.gsplit",
            "com.geely.hvac",
            "com.geely.oneosphone"
    );
    private static final Set<String> FORCE_HIDE_CLASSES = Set.of(
            "com.salat.gbinder.features.launcher.LauncherEntryActivity"
    );

    private HeadUnitWindowRules() {
    }

    static boolean forceHide(String packageName, String className) {
        return FORCE_HIDE_PACKAGES.contains(value(packageName))
                || FORCE_HIDE_CLASSES.contains(value(className));
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
