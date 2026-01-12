package io.securenode.branding.sync

import android.content.Context
import androidx.work.*
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.SecureNodeError
import io.securenode.branding.telemetry.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EventUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = SecureNodeBranding.tryRepo()
            if (repo == null) {
                Logger.w("Event upload skipped: SDK not initialized")
                return@withContext Result.failure()
            }

            val uploaded = repo.uploadPendingEvents(50)
            Logger.d("Uploaded events: $uploaded")
            Result.success()
        } catch (t: Throwable) {
            Logger.w("Event upload failed", t)
            if (t is SecureNodeError.Unauthorized) return@withContext Result.retry()
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "securenode_branding_events_once"
        const val UNIQUE_PERIODIC_NAME = "securenode_branding_events_periodic"

        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EventUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<EventUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
    }
}
