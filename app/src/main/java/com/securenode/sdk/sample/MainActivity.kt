package com.securenode.sdk.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.security.KeyStoreManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val apiBaseUrl = "https://api.securenode.io"
    private var lastHadApiKey: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val rescan = findViewById<Button>(R.id.rescan)

        rescan.setOnClickListener {
            startActivity(android.content.Intent(this, QrScanActivity::class.java))
        }

        ensureApiKeyOrScan()

        status.text = "Status: syncing…"
        schedulePeriodicSync()
        runInitialSync(status)
    }

    override fun onResume() {
        super.onResume()
        ensureApiKeyOrScan()
        // If the user just scanned a key, kick a sync right away.
        val hasKeyNow = !KeyStoreManager(applicationContext).getApiKey().isNullOrBlank()
        if (!lastHadApiKey && hasKeyNow) {
            val status = findViewById<TextView>(R.id.status)
            status.text = "Status: syncing…"
            schedulePeriodicSync()
            runInitialSync(status)
        }
        lastHadApiKey = hasKeyNow
    }

    private fun ensureApiKeyOrScan() {
        val keyStore = KeyStoreManager(applicationContext)
        val apiKey = keyStore.getApiKey()
        lastHadApiKey = !apiKey.isNullOrBlank()
        if (!lastHadApiKey) {
            startActivity(android.content.Intent(this, QrScanActivity::class.java))
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<SdkSyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                SdkSyncWorker.BACKOFF_POLICY,
                SdkSyncWorker.BACKOFF_DELAY,
                SdkSyncWorker.BACKOFF_UNIT
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            SdkSyncWorker.UNIQUE_WORK_NAME,
            // KEEP avoids resetting the next-run timer every time the app opens.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun runInitialSync(status: TextView) {
        val keyStore = KeyStoreManager(applicationContext)
        val apiKey = keyStore.getApiKey() ?: run {
            status.text = "Status: API key required (scan QR)"
            return
        }

        val sdk = SecureNodeSDK.initialize(
            context = applicationContext,
            config = SecureNodeConfig(apiUrl = apiBaseUrl, apiKey = apiKey)
        )

        sdk.syncBranding { result ->
            runOnUiThread {
                status.text = result.fold(
                    onSuccess = { "Status: synced • waiting for calls" },
                    onFailure = { "Status: sync error (${it.message ?: "unknown"}) • waiting for calls" }
                )
            }
        }
    }
}


