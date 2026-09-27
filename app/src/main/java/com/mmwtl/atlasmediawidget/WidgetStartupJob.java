package com.mmwtl.atlasmediawidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Bounded startup recovery after unlock; Android owns the initial job delivery. */
public final class WidgetStartupJob extends JobService {
    static final int JOB_ID = 2408;
    private static final long RETRY_MS = 1_000L;
    private static final long STARTUP_WINDOW_MS = 15_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private JobParameters parameters;
    private long deadline;
    private final Runnable retry = this::retryStartup;

    static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, WidgetStartupJob.class))
                .setMinimumLatency(RETRY_MS)
                .setOverrideDeadline(RETRY_MS)
                .build();
        try {
            if (context.getSystemService(JobScheduler.class).schedule(job)
                    != JobScheduler.RESULT_SUCCESS) {
                AppLog.warn("Cannot schedule widget startup retry", null);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot schedule widget startup retry", error);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        cancelRetry();
        parameters = params;
        deadline = SystemClock.elapsedRealtime() + STARTUP_WINDOW_MS;
        if (!continueStartup()) {
            parameters = null;
            return false;
        }
        main.postDelayed(retry, RETRY_MS);
        return true;
    }

    private void retryStartup() {
        if (continueStartup()) {
            main.postDelayed(retry, RETRY_MS);
        } else {
            JobParameters finished = parameters;
            cancelRetry();
            jobFinished(finished, false);
        }
    }

    private boolean continueStartup() {
        if (!new Prefs(this).isWidgetMode() || AtlasMediaWidgetProvider.ids(this).length == 0) {
            return false;
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            AppLog.info("Widget startup retry window expired");
            return false;
        }
        OverlayService service = OverlayService.current();
        if (service != null) return service.maintainMediaStartup();
        AppLog.info("Retrying widget service startup after user unlock");
        AtlasMediaWidgetProvider.refresh(this);
        return true;
    }

    private void cancelRetry() {
        main.removeCallbacks(retry);
        parameters = null;
    }

    @Override public boolean onStopJob(JobParameters params) {
        cancelRetry();
        return false;
    }

    @Override public void onDestroy() {
        cancelRetry();
        super.onDestroy();
    }
}
