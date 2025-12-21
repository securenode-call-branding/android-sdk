package com.securenode.sdk.sample

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.security.KeyStoreManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SdkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val apiBaseUrl = "https://api.securenode.io"

    override suspend fun doWork(): Result {
        val apiKey = KeyStoreManager(applicationContext).getApiKey()
        if (apiKey.isNullOrBlank()) {
            return Result.retry()
        }

        val sdk = SecureNodeSDK.initialize(
            context = applicationContext,
            config = SecureNodeConfig(apiUrl = apiBaseUrl, apiKey = apiKey)
        )

        return suspendCancellableCoroutine { cont ->
            sdk.syncBranding { result ->
                val out = result.fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
                cont.resume(out)
            }
        }
    }
}


