package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BootStartPolicyTest {
    @Test public void acceptsColdDirectAndQuickBootSignals() {
        assertTrue(BootStartPolicy.isStartupAction("android.intent.action.BOOT_COMPLETED"));
        assertTrue(BootStartPolicy.isStartupAction(
                "android.intent.action.LOCKED_BOOT_COMPLETED"));
        assertTrue(BootStartPolicy.isStartupAction(
                "android.intent.action.QUICKBOOT_POWERON"));
        assertTrue(BootStartPolicy.isStartupAction("android.intent.action.USER_UNLOCKED"));
    }

    @Test public void rejectsUnrelatedSignals() {
        assertFalse(BootStartPolicy.isStartupAction(null));
        assertFalse(BootStartPolicy.isStartupAction("android.intent.action.SCREEN_ON"));
        assertFalse(BootStartPolicy.isStartupAction("android.intent.action.MY_PACKAGE_REPLACED"));
    }
}
