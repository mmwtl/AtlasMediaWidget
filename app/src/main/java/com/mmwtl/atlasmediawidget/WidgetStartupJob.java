package com.mmwtl.atlasmediawidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** One startup retry, owned by Android so it survives the boot receiver's process. */
public final class WidgetStartupJob extends JobService {
    static final int JOB_ID = 2408;

    static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, WidgetStartupJob.class))
                .setMinimumLatency(10_000L)
                .setOverrideDeadline(60_000L)
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
        // Mode and widget allocation may have changed while the job was waiting.
        if (new Prefs(this).isWidgetMode() && !OverlayService.isRunning()) {
            AppLog.info("Retrying widget startup after user unlock");
            AtlasMediaWidgetProvider.refresh(this);
        }
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
