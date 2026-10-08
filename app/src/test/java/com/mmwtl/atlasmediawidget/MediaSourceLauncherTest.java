package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.PlaybackState;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class MediaSourceLauncherTest {
    @Test
    public void onlineWithoutOwnerOpensConfiguredPlayerInsteadOfChooser() {
        Application app = RuntimeEnvironment.getApplication();
        installLauncherActivity(app, "com.example.player");

        assertTrue(new MediaSourceLauncher(app).open(snapshot(""), "com.example.player"));

        Intent started = shadowOf(app).getNextStartedActivity();
        assertEquals("com.example.player", started.getComponent().getPackageName());
    }

    @Test
    public void onlineWithoutOwnerOrConfiguredPlayerOpensChooser() {
        Application app = RuntimeEnvironment.getApplication();

        assertTrue(new MediaSourceLauncher(app).open(snapshot(""), ""));

        Intent started = shadowOf(app).getNextStartedActivity();
        assertTrue(started.getSelector().hasCategory(Intent.CATEGORY_APP_MUSIC));
    }

    @Test
    public void onlyOnlineWithoutOwnerNeedsConfiguredPlayer() {
        assertTrue(MediaSourceLauncher.needsConfiguredPlayer(snapshot("")));
        assertFalse(MediaSourceLauncher.needsConfiguredPlayer(snapshot("com.example.owner")));
    }

    private static void installLauncherActivity(Application app, String packageName) {
        ComponentName component = new ComponentName(packageName, packageName + ".Main");
        var packageManager = shadowOf(app.getPackageManager());
        packageManager.addActivityIfNotPresent(component);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        packageManager.addIntentFilterForActivity(component, filter);
    }

    private static MediaSnapshot snapshot(String ownerPackage) {
        return new MediaSnapshot(MediaBridgeContract.VERSION, 1, 1, true, 0, "",
                MediaSource.Id.ONLINE, "", List.of(), ownerPackage, "", "", "", "", "",
                0L, -1L, 0L, 0, PlaybackState.STATE_NONE, 0, "", 0,
                MediaBridgeContract.CAP_PLAY, "", 0);
    }
}
