package com.securenode.sdk.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.security.KeyStoreManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SdkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val apiBaseUrl = "https://api.securenode.io"

    override suspend fun doWork(): Result {
        val apiKey = KeyStoreManager(applicationContext).getApiKey()
        if (apiKey.isNullOrBlank()) {
            // Periodic work will run again later; don't spin retries when the user hasn't onboarded yet.
            return Result.success()
        }

        val sdk = SecureNodeSDK.initialize(
            context = applicationContext,
            config = SecureNodeConfig(apiUrl = apiBaseUrl, apiKey = apiKey)
        )

        return suspendCancellableCoroutine { cont ->
            sdk.syncBranding { result ->
                val out = result.fold(
                    onSuccess = {
                        // Persist config for ring-time behavior (skip branding when capped, label Testing in demo)
                        SampleConfigStore.setBrandingEnabled(applicationContext, it.config.brandingEnabled)
                        SampleConfigStore.setMode(applicationContext, it.config.mode)
                        Result.success()
                    },
                    onFailure = { Result.retry() }
                )
                cont.resume(out)
            }
        }
    }

    companion object {
        // Keep these in one place so scheduling code can reference the same contract.
        const val UNIQUE_WORK_NAME = "securenode-sdk-sync"
        val BACKOFF_POLICY = BackoffPolicy.EXPONENTIAL
        val BACKOFF_DELAY: Long = 30
        val BACKOFF_UNIT: TimeUnit = TimeUnit.SECONDS
    }
}


