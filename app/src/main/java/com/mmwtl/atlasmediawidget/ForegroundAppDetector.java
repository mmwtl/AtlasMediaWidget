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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundAppDetector {
    private final Context context;
    private final UsageStatsManager usageStatsManager;
    private final PowerManager powerManager;
    private final KeyguardManager keyguardManager;
    private final Set<String> homePackages = new HashSet<>();
    private final ForegroundEventTracker tracker = new ForegroundEventTracker();
    private long lastHomeRefresh;
    private long lastQuery;

    ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
        usageStatsManager = context.getSystemService(UsageStatsManager.class);
        powerManager = context.getSystemService(PowerManager.class);
        keyguardManager = context.getSystemService(KeyguardManager.class);
        refreshHomePackages();
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        return appOps != null && appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        ) == AppOpsManager.MODE_ALLOWED;
    }

    boolean isHomeForeground() {
        if (powerManager == null || !powerManager.isInteractive()
                || keyguardManager != null && keyguardManager.isKeyguardLocked()) return false;
        long now = System.currentTimeMillis();
        if (homePackages.isEmpty() || now - lastHomeRefresh > 30_000L) refreshHomePackages();
        String foreground = currentForegroundPackage();
        return foreground != null && homePackages.contains(foreground);
    }

    private String currentForegroundPackage() {
        if (usageStatsManager == null || !hasUsageAccess(context)) return null;
        long now = System.currentTimeMillis();
        boolean initialQuery = lastQuery == 0;
        long queryStarted = SystemClock.elapsedRealtime();
        long begin = initialQuery ? now - 12L * 60L * 60L * 1000L
                : Math.max(now - 60_000L, lastQuery - 2_000L);
        try {
            UsageEvents events = usageStatsManager.queryEvents(begin, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    tracker.onResumed(event.getTimeStamp(), event.getPackageName());
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED
                        || event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                    tracker.onStopped(event.getTimeStamp(), event.getPackageName());
                }
            }
            lastQuery = now;
            if (!tracker.hasObservedEvent()) {
                List<UsageStats> stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now);
                if (stats != null) {
                    for (UsageStats item : stats) {
                        tracker.seed(item.getLastTimeUsed(), item.getPackageName());
                    }
                }
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot query foreground application", error);
            return null;
        }
        long queryElapsed = SystemClock.elapsedRealtime() - queryStarted;
        if (initialQuery || queryElapsed >= 100L) {
            AppLog.info("Foreground usage query completed in " + queryElapsed + " ms");
        }
        return tracker.foregroundPackage();
    }

    private void refreshHomePackages() {
        homePackages.clear();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            List<ResolveInfo> homes = context.getPackageManager().queryIntentActivities(
                    intent, PackageManager.MATCH_ALL);
            for (ResolveInfo home : homes) {
                if (home.activityInfo != null) homePackages.add(home.activityInfo.packageName);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot query HOME packages", error);
        }
        lastHomeRefresh = System.currentTimeMillis();
    }
}
