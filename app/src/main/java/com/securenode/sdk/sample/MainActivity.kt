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
    private var voipEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val voipStatus = findViewById<TextView>(R.id.voipStatus)
        val rescan = findViewById<Button>(R.id.rescan)
        val voipDemo = findViewById<Button>(R.id.voipDemo)

        rescan.setOnClickListener {
            startActivity(android.content.Intent(this, QrScanActivity::class.java))
        }

        voipDemo.setOnClickListener {
            startActivity(android.content.Intent(this, VoipDemoActivity::class.java))
        }

        ensureApiKeyOrScan()

        status.text = "Status: syncing…"
        voipStatus.text = "VoIP dialer mode: checking…"
        voipDemo.isEnabled = false
        schedulePeriodicSync()
        runInitialSync(status, voipStatus, voipDemo)
    }

    override fun onResume() {
        super.onResume()
        ensureApiKeyOrScan()
        // If the user just scanned a key, kick a sync right away.
        val hasKeyNow = !KeyStoreManager(applicationContext).getApiKey().isNullOrBlank()
        if (!lastHadApiKey && hasKeyNow) {
            val status = findViewById<TextView>(R.id.status)
            val voipStatus = findViewById<TextView>(R.id.voipStatus)
            val voipDemo = findViewById<Button>(R.id.voipDemo)
            status.text = "Status: syncing…"
            voipStatus.text = "VoIP dialer mode: checking…"
            voipDemo.isEnabled = false
            schedulePeriodicSync()
            runInitialSync(status, voipStatus, voipDemo)
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

    private fun runInitialSync(status: TextView, voipStatus: TextView, voipDemo: Button) {
        val keyStore = KeyStoreManager(applicationContext)
        val apiKey = keyStore.getApiKey() ?: run {
            status.text = "Status: API key required (scan QR)"
            voipStatus.text = "VoIP dialer mode: disabled (no API key)"
            voipDemo.isEnabled = false
            return
        }

        val sdk = SecureNodeSDK.initialize(
            context = applicationContext,
            config = SecureNodeConfig(apiUrl = apiBaseUrl, apiKey = apiKey)
        )

        sdk.syncBranding { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = {
                        // Persist config for ring-time behavior (skip branding when capped, label Testing in demo)
                        SampleConfigStore.setBrandingEnabled(applicationContext, it.config.brandingEnabled)
                        SampleConfigStore.setMode(applicationContext, it.config.mode)

                        status.text = "Status: synced • waiting for calls"
                        voipEnabled = it.config.voipDialerEnabled
                        if (android.os.Build.VERSION.SDK_INT < 26) {
                            voipStatus.text = "VoIP dialer mode: unsupported (Android < 8)"
                            voipDemo.isEnabled = false
                        } else if (voipEnabled) {
                            voipStatus.text = "VoIP dialer mode: enabled for this company"
                            voipDemo.isEnabled = true
                        } else {
                            voipStatus.text = "VoIP dialer mode: disabled for this company"
                            voipDemo.isEnabled = false
                        }
                    },
                    onFailure = {
                        status.text = "Status: sync error (${it.message ?: "unknown"}) • waiting for calls"
                        voipStatus.text = "VoIP dialer mode: unknown (sync failed)"
                        voipDemo.isEnabled = false
                    }
                )
            }
        }
    }
}


