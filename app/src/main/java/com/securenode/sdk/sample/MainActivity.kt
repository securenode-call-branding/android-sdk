package com.securenode.sdk.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.security.KeyStoreManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val apiBaseUrl = "https://api.securenode.io"

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
    }

    private fun ensureApiKeyOrScan() {
        val keyStore = KeyStoreManager(applicationContext)
        val apiKey = keyStore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            startActivity(android.content.Intent(this, QrScanActivity::class.java))
        }
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SdkSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "securenode-sdk-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
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


