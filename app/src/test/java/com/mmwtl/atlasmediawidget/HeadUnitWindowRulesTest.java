package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HeadUnitWindowRulesTest {
    @Test
    public void forceHideMatchesKnownFirmwarePackages() {
        assertTrue(HeadUnitWindowRules.forceHide("com.salat.gsplit", "MainActivity"));
        assertTrue(HeadUnitWindowRules.forceHide("com.geely.hvac", "ClimateActivity"));
        assertTrue(HeadUnitWindowRules.forceHide("com.geely.oneosphone", "PhoneActivity"));
    }

    @Test
    public void forceHideMatchesKnownLauncherTransitionClass() {
        assertTrue(HeadUnitWindowRules.forceHide(
                "com.salat.gbinder",
                "com.salat.gbinder.features.launcher.LauncherEntryActivity"
        ));
    }

    @Test
    public void forceHideDoesNotMatchUnrelatedWindowsOrEmptyValues() {
        assertFalse(HeadUnitWindowRules.forceHide("com.example.app", "MainActivity"));
        assertFalse(HeadUnitWindowRules.forceHide("", ""));
        assertFalse(HeadUnitWindowRules.forceHide(null, null));
    }
}
