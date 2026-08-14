package com.mmwtl.atlasmediawidget;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundAppDetector {
    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final PowerManager powerManager;
    private final KeyguardManager keyguardManager;
    private final UserManager userManager;
    private final Set<String> homePackages = new HashSet<>();
    private final Set<String> homeComponents = new HashSet<>();
    private final ForegroundEventTracker tracker = new ForegroundEventTracker();
    private long lastHomeRefresh;
    private long lastQuery;

    ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
        usageStatsManager = context.getSystemService(UsageStatsManager.class);
        powerManager = context.getSystemService(PowerManager.class);
        keyguardManager = context.getSystemService(KeyguardManager.class);
        userManager = context.getSystemService(UserManager.class);
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        return appOps != null && appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        ) == AppOpsManager.MODE_ALLOWED;
    }

    boolean isHomeVisible() {
        if (!isDeviceReady()) return false;
        long now = System.currentTimeMillis();
        if (homePackages.isEmpty() || now - lastHomeRefresh > 30_000L) refreshHomePackages();
        // Accessibility snapshots are already collected from the window service and are the
        // authoritative source when they contain a decisive result. Do not put the potentially
        // slow UsageStats Binder query in front of this hot path.
        WindowVisibilityPolicy.Decision decision = evaluateAccessibilitySnapshot();
        if (decision != WindowVisibilityPolicy.Decision.UNKNOWN) {
            return decision == WindowVisibilityPolicy.Decision.HOME_VISIBLE;
        }

        // UsageStats remains a bounded fallback for OEM frames where the accessibility snapshot
        // is unavailable or cannot disambiguate the active window.
        boolean usageStateAvailable = refreshActivityVisibility();
        decision = evaluateAccessibilitySnapshot();
        if (decision != WindowVisibilityPolicy.Decision.UNKNOWN) {
            return decision == WindowVisibilityPolicy.Decision.HOME_VISIBLE;
        }
        return usageStateAvailable && tracker.isAnyPackageVisible(homePackages);
    }

    private WindowVisibilityPolicy.Decision evaluateAccessibilitySnapshot() {
        AccessibilityWindowState.Snapshot windowState = AccessibilityWindowState.current();
        if (!windowState.available) return WindowVisibilityPolicy.Decision.UNKNOWN;
        return WindowVisibilityPolicy.evaluate(
                windowState.windows,
                windowState.displayWidth,
                windowState.displayHeight,
                homePackages,
                homeComponents,
                tracker.mostRecentVisibleActivity(),
                windowState.eventPackage,
                windowState.eventClass,
                context.getPackageName()
        );
    }

    boolean isDeviceReady() {
        return powerManager != null && powerManager.isInteractive()
                && (keyguardManager == null || !keyguardManager.isKeyguardLocked())
                && (userManager == null || userManager.isUserUnlocked());
    }

    private boolean refreshActivityVisibility() {
        if (usageStatsManager == null || !hasUsageAccess(context)) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean initialQuery = lastQuery == 0;
        long queryStarted = SystemClock.elapsedRealtime();
        long begin = ForegroundPollPolicy.queryBegin(now, lastQuery);
        UsageEvents events;
        Trace.beginSection("AtlasForeground.queryEvents");
        try {
            events = usageStatsManager.queryEvents(begin, now);
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("usage-events", "Usage-events query failed", error);
            return false;
        } finally {
            Trace.endSection();
        }
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                tracker.onResumed(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                tracker.onPaused(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                tracker.onStopped(
                        event.getTimeStamp(), event.getPackageName(), event.getClassName());
            } else if (event.getEventType() == UsageEvents.Event.DEVICE_SHUTDOWN
                    || event.getEventType() == UsageEvents.Event.DEVICE_STARTUP) {
                tracker.onReset(event.getTimeStamp());
            }
        }
        lastQuery = now;

        if (!tracker.hasObservedEvent()) {
            List<UsageStats> stats;
            try {
                stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        now - 24L * 60L * 60L * 1000L,
                        now
                );
            } catch (RuntimeException error) {
                AppLog.warnRateLimited(
                        "usage-stats", "Usage-stats fallback query failed", error);
                return false;
            }
            if (stats != null) {
                for (UsageStats item : stats) {
                    tracker.seed(item.getLastTimeUsed(), item.getPackageName());
                }
            }
        }
        long queryElapsed = SystemClock.elapsedRealtime() - queryStarted;
        if (initialQuery || queryElapsed >= 100L) {
            AppLog.info("Foreground usage query completed in " + queryElapsed + " ms");
        }
        return true;
    }

    private void refreshHomePackages() {
        homePackages.clear();
        homeComponents.clear();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes;
        try {
            homes = context.getPackageManager().queryIntentActivities(
                    intent, PackageManager.MATCH_ALL);
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("home-query", "HOME package query failed", error);
            lastHomeRefresh = System.currentTimeMillis();
            return;
        }
        for (ResolveInfo home : homes) {
            if (home.activityInfo != null) {
                homePackages.add(home.activityInfo.packageName);
                homeComponents.add(WindowVisibilityPolicy.componentKey(
                        home.activityInfo.packageName,
                        home.activityInfo.name
                ));
            }
        }
        lastHomeRefresh = System.currentTimeMillis();
    }
}
