package io.securenode.nodekit.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.securenode.sdk.SecureNodeSDK;

import io.securenode.nodekit.SdkBootstrap;

/**
 * Daily background sync for branding cache.
 *
 * Requirements:
 * - Must never crash
 * - Must be fail-open
 * - If SDK isn't initialized yet (no API key), job just finishes quietly
 */
public class BrandingSyncJobService extends JobService {
  private static final String TAG = "BrandingSyncJob";

  @Override
  public boolean onStartJob(JobParameters params) {
    try {
      SharedPreferences prefs = getSharedPreferences("SecureNodePrefs", Context.MODE_PRIVATE);
      String apiUrl = prefs.getString("api_url", "https://calls.securenode.io/api");

      SdkBootstrap.initializeIfPossible(this, apiUrl);
      SecureNodeSDK sdk = SdkBootstrap.getInstanceOrNull();
      if (sdk == null) {
        jobFinished(params, false);
        return false;
      }

      sdk.syncBranding(null, result -> {
        try {
          if (result.isFailure()) {
            Log.e(TAG, "Sync failed (will retry later): " + String.valueOf(result.exceptionOrNull()));
          }
        } catch (Throwable ignored) {}

        jobFinished(params, false);
        return null;
      });

      return true; // async
    } catch (Throwable t) {
      Log.e(TAG, "Job crashed (fail-open)", t);
      jobFinished(params, false);
      return false;
    }
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    // Let the system reschedule if needed.
    return true;
  }
}





