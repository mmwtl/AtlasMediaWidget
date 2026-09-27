package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.*;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.os.Looper;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class BootReceiverTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void widgetStartsAfterBootUnlockQuickBootAndUpgradeWithoutOverlayOptIn() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        prefs.putBoolean(Prefs.KEY_AUTO_START, false);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        bindWidget();
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);

        for (String action : new String[]{Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_USER_UNLOCKED, BootStartPolicy.ACTION_QUICKBOOT_POWERON,
                Intent.ACTION_MY_PACKAGE_REPLACED}) {
            new BootReceiver().onReceive(context, new Intent(action));
            assertWidgetStartRequested();
            assertFalse(prefs.getBoolean(Prefs.KEY_AUTO_START, true));
            assertFalse(prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, true));
        }
        assertEquals(1, context.getSystemService(JobScheduler.class).getAllPendingJobs().size());
    }

    @Test public void lockedBootSchedulesStartupWithoutNeedingAnotherBootBroadcast() throws Exception {
        new Prefs(context).setWidgetMode(true);
        bindWidget();
        var users = Shadows.shadowOf(context.getSystemService(UserManager.class));
        users.setUserUnlocked(false);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        var job = context.getSystemService(JobScheduler.class).getPendingJob(WidgetStartupJob.JOB_ID);
        assertNotNull(job);
        assertEquals(new ComponentName(context, WidgetStartupJob.class), job.getService());
        assertEquals(1_000L, job.getMinLatencyMillis());
        var serviceInfo = context.getPackageManager().getServiceInfo(job.getService(), 0);
        assertFalse(serviceInfo.directBootAware);
        assertEquals("android.permission.BIND_JOB_SERVICE", serviceInfo.permission);
        users.setUserUnlocked(true);
        runStartupJob();
        assertWidgetStartRequested();
    }

    @Test public void delayedStartupRechecksModeAndWidgetAllocation() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(true);
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        runStartupJob();
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        bindWidget();
        prefs.setWidgetMode(false);
        runStartupJob();
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        prefs.setWidgetMode(true);
        runStartupJob();
        assertWidgetStartRequested();
    }

    @Test public void unrelatedBroadcastDoesNotScheduleWidgetStartup() {
        new Prefs(context).setWidgetMode(true);
        bindWidget();
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_SCREEN_ON));
        assertTrue(context.getSystemService(JobScheduler.class).getAllPendingJobs().isEmpty());
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void startupRetriesEverySecondAndStopsAtFifteenSeconds() {
        new Prefs(context).setWidgetMode(true);
        bindWidget();
        var controller = Robolectric.buildService(WidgetStartupJob.class).create();
        try {
            assertTrue(controller.get().onStartJob(null));
            assertWidgetStartRequested();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(999));
            assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
            assertWidgetStartRequested();
            for (int second = 2; second < 15; second++) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
                assertWidgetStartRequested();
            }
            assertFalse(Shadows.shadowOf(controller.get()).getIsJobFinished());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
            assertTrue(Shadows.shadowOf(controller.get()).getIsJobFinished());
            assertFalse(Shadows.shadowOf(controller.get()).getIsRescheduleNeeded());
            assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        } finally {
            controller.destroy();
        }
    }

    @Test public void modeChangeAndSystemStopCancelStartupCallbacks() {
        Prefs prefs = new Prefs(context);
        bindWidget();
        for (boolean systemStop : new boolean[]{false, true}) {
            prefs.setWidgetMode(true);
            var controller = Robolectric.buildService(WidgetStartupJob.class).create();
            try {
                assertTrue(controller.get().onStartJob(null));
                assertWidgetStartRequested();
                if (systemStop) assertFalse(controller.get().onStopJob(null));
                else prefs.setWidgetMode(false);
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
                assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
                assertEquals(!systemStop, Shadows.shadowOf(controller.get()).getIsJobFinished());
            } finally {
                controller.destroy();
            }
        }
    }

    @Test public void destroyedJobDoesNotKeepStartingService() {
        new Prefs(context).setWidgetMode(true);
        bindWidget();
        var controller = Robolectric.buildService(WidgetStartupJob.class).create();
        assertTrue(controller.get().onStartJob(null));
        assertWidgetStartRequested();
        controller.destroy();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    private void runStartupJob() {
        var controller = Robolectric.buildService(WidgetStartupJob.class).create();
        try {
            controller.get().onStartJob(null);
        } finally {
            controller.destroy();
        }
    }

    @Test public void widgetModeWithoutPlacedWidgetsDoesNotStartService() {
        new Prefs(context).setWidgetMode(true);
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void overlayStillRequiresItsOwnBootOptInEvenWithPlacedWidgets() {
        Prefs prefs = new Prefs(context);
        prefs.setWidgetMode(false);
        prefs.putBoolean(Prefs.KEY_AUTO_START, false);
        prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
        bindWidget();
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        assertFalse(prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, true));
    }

    private void bindWidget() {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, AtlasMediaWidgetProvider.class);
        info.initialLayout = R.layout.media_widget_empty;
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(41, info);
    }

    private void assertWidgetStartRequested() {
        Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();
        assertNotNull(started);
        assertEquals(new ComponentName(context, OverlayService.class), started.getComponent());
        assertEquals(OverlayService.ACTION_WIDGET_REFRESH, started.getAction());
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
    }
}
