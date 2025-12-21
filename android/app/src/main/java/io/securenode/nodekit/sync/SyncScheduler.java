package io.securenode.nodekit.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/**
 * Schedules daily branding sync using native JobScheduler (no extra deps).
 *
 * Self-heal behavior:
 * - Schedule is re-applied on app start and boot
 * - Job will retry automatically (system managed) and we also reschedule if missing
 */
public final class SyncScheduler {
  private static final int JOB_ID = 42001;

  private SyncScheduler() {}

  public static void scheduleDailySync(Context context) {
    try {
      ComponentName serviceName = new ComponentName(context, BrandingSyncJobService.class);

      JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceName)
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setPersisted(true);

      // Periodic jobs have minimums; use 24h target with flex when available
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        builder.setPeriodic(24L * 60L * 60L * 1000L, 60L * 60L * 1000L);
      } else {
        builder.setPeriodic(24L * 60L * 60L * 1000L);
      }

      JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
      if (scheduler != null) {
        scheduler.schedule(builder.build());
      }
    } catch (Throwable ignored) {
      // fail-open
    }
  }
}





