package com.securenode.sdk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.database.BrandingDatabase
import com.securenode.sdk.security.KeyStoreManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlin.concurrent.thread
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
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val saveApiKey = findViewById<Button>(R.id.saveApiKey)
        val syncNow = findViewById<Button>(R.id.syncNow)
        val openDialer = findViewById<Button>(R.id.openDialer)
        val dbDump = findViewById<TextView>(R.id.dbDump)
        val apiUrlLinks = findViewById<TextView>(R.id.apiUrlLinks)

        rescan.setOnClickListener {
            startActivity(android.content.Intent(this, QrScanActivity::class.java))
        }

        voipDemo.setOnClickListener {
            startActivity(android.content.Intent(this, VoipDemoActivity::class.java))
        }

        val keyStore = KeyStoreManager(applicationContext)
        val existingKey = keyStore.getApiKey().orEmpty()
        lastHadApiKey = existingKey.isNotBlank()
        apiKeyInput.setText(existingKey)

        apiUrlLinks.text = buildString {
            append(apiBaseUrl)
            append("\n")
            append(apiBaseUrl)
            append("/api")
            append("\n")
            append(apiBaseUrl)
            append("/mobile/branding/sync")
            append("\n")
            append(apiBaseUrl)
            append("/mobile/branding/lookup")
            append("\n")
            append(apiBaseUrl)
            append("/mobile/branding/event")
        }
        apiUrlLinks.movementMethod = LinkMovementMethod.getInstance()

        saveApiKey.setOnClickListener {
            val raw = apiKeyInput.text?.toString().orEmpty().trim()
            if (raw.isBlank()) {
                status.text = "Status: API key required"
                return@setOnClickListener
            }
            keyStore.saveApiKey(raw)
            lastHadApiKey = true
            status.text = "Status: saved API key • syncing…"
            voipStatus.text = "VoIP dialer mode: checking…"
            voipDemo.isEnabled = false
            schedulePeriodicSync()
            runInitialSync(status, voipStatus, voipDemo, dbDump)
        }

        syncNow.setOnClickListener {
            status.text = "Status: syncing…"
            voipStatus.text = "VoIP dialer mode: checking…"
            voipDemo.isEnabled = false
            runInitialSync(status, voipStatus, voipDemo, dbDump)
        }

        openDialer.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        }

        status.text = "Status: syncing…"
        voipStatus.text = "VoIP dialer mode: checking…"
        voipDemo.isEnabled = false
        schedulePeriodicSync()
        runInitialSync(status, voipStatus, voipDemo, dbDump)
    }

    override fun onResume() {
        super.onResume()
        // If the user just scanned a key, kick a sync right away.
        val hasKeyNow = !KeyStoreManager(applicationContext).getApiKey().isNullOrBlank()
        if (!lastHadApiKey && hasKeyNow) {
            val status = findViewById<TextView>(R.id.status)
            val voipStatus = findViewById<TextView>(R.id.voipStatus)
            val voipDemo = findViewById<Button>(R.id.voipDemo)
            val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
            val dbDump = findViewById<TextView>(R.id.dbDump)
            apiKeyInput.setText(KeyStoreManager(applicationContext).getApiKey().orEmpty())
            status.text = "Status: syncing…"
            voipStatus.text = "VoIP dialer mode: checking…"
            voipDemo.isEnabled = false
            schedulePeriodicSync()
            runInitialSync(status, voipStatus, voipDemo, dbDump)
        }
        lastHadApiKey = hasKeyNow
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

    private fun runInitialSync(status: TextView, voipStatus: TextView, voipDemo: Button, dbDump: TextView) {
        val keyStore = KeyStoreManager(applicationContext)
        val apiKey = keyStore.getApiKey() ?: run {
            status.text = "Status: API key required"
            voipStatus.text = "VoIP dialer mode: disabled (no API key)"
            voipDemo.isEnabled = false
            renderDbDump(dbDump)
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
                        renderDbDump(dbDump)
                    },
                    onFailure = {
                        status.text = "Status: sync error (${it.message ?: "unknown"}) • waiting for calls"
                        voipStatus.text = "VoIP dialer mode: unknown (sync failed)"
                        voipDemo.isEnabled = false
                        renderDbDump(dbDump)
                    }
                )
            }
        }
    }

    private fun renderDbDump(dbDump: TextView) {
        thread {
            val rows = try {
                BrandingDatabase.getDatabase(applicationContext)
                    .brandingDao()
                    .getRecentBranding(50)
            } catch (_e: Exception) {
                emptyList()
            }

            val text = if (rows.isEmpty()) {
                "(empty)"
            } else {
                rows.joinToString(separator = "\n\n") { row ->
                    buildString {
                        append(row.phoneNumberE164)
                        append("\n")
                        append(row.brandName)
                        row.callReason?.takeIf { it.isNotBlank() }?.let {
                            append("\nreason: ")
                            append(it)
                        }
                        row.logoUrl?.takeIf { it.isNotBlank() }?.let {
                            append("\nlogo: ")
                            append(it)
                        }
                        append("\nupdatedAt: ")
                        append(row.updatedAt)
                    }
                }
            }

            runOnUiThread {
                dbDump.text = text
            }
        }
    }
}


