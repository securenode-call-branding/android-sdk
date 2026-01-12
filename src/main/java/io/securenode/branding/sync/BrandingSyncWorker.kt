package io.securenode.branding.sync

import android.content.Context
import androidx.work.*
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.telemetry.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class BrandingSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cfg = SecureNodeBranding.tryConfig()
            val repo = SecureNodeBranding.tryRepo()
            if (cfg == null || repo == null) {
                Logger.w("Branding sync skipped: SDK not initialized")
                return@withContext Result.failure()
            }

            val since = Instant.now().minus(cfg.syncSinceHoursDefault.toLong(), ChronoUnit.HOURS)
            val resp = repo.syncBrandingBestEffort(since.toString())
            // Optional: sync portal directory into Contacts (requires permissions + explicit config flag).
            if (cfg.enableContactsBranding) {
                repo.syncContactsBrandingBestEffort(resp.branding)
            }
            Result.success()
        } catch (t: Throwable) {
            Logger.w("Branding sync failed", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "securenode_branding_sync_once"
        const val UNIQUE_PERIODIC_NAME = "securenode_branding_sync_periodic"

        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<BrandingSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<BrandingSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
    }
}
