package io.securenode.branding.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.securenode.branding.BuildConfig
import io.securenode.branding.R
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.sync.BrandingSyncWorker
import io.securenode.branding.sync.EventUploadWorker
import io.securenode.branding.telemetry.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class DebugActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DebugGate.isDebugAvailable(this)) {
            finish()
            return
        }

        setContentView(R.layout.securenode_debug_activity)

        val summary = findViewById<TextView>(R.id.summary)
        val logs = findViewById<TextView>(R.id.logs)

        findViewById<Button>(R.id.btnForceSync).setOnClickListener {
            val wm = WorkManager.getInstance(this)
            wm.enqueueUniqueWork(
                BrandingSyncWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                BrandingSyncWorker.oneTimeRequest()
            )
            refresh(summary, logs)
        }

        findViewById<Button>(R.id.btnFlushEvents).setOnClickListener {
            val wm = WorkManager.getInstance(this)
            wm.enqueueUniqueWork(
                EventUploadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                EventUploadWorker.oneTimeRequest()
            )
            refresh(summary, logs)
        }

        findViewById<Button>(R.id.btnExportLogs).setOnClickListener {
            val policy = DebugPolicy.load(this)
            if (!policy.allowExport) return@setOnClickListener
            shareText("securenode_logs.txt", Logger.snapshotText())
        }

        findViewById<Button>(R.id.btnExportState).setOnClickListener {
            val policy = DebugPolicy.load(this)
            if (!policy.allowExport) return@setOnClickListener
            scope.launch {
                val json = buildDebugJson()
                runOnUiThread { shareText("securenode_debug_state.json", json.toString(2)) }
            }
        }

        refresh(summary, logs)
    }

    private fun refresh(summary: TextView, logs: TextView) {
        scope.launch {
            val json = buildDebugJson()
            runOnUiThread {
                summary.text = json.optString("summary")
                logs.text = Logger.snapshotText()
            }
        }
    }

    private suspend fun buildDebugJson(): JSONObject {
        val o = JSONObject()
        val policy = DebugPolicy.load(this)

        o.put("sdk_version", BuildConfig.VERSION_NAME)
        o.put("sdk_debuggable", BuildConfig.DEBUG)
        o.put("server_debug_enabled", policy.serverEnabled)
        o.put("server_debug_expires_at_ms", policy.expiresAtEpochMs)
        o.put("server_allow_export", policy.allowExport)

        // Best-effort DB counts / timestamps
        try {
            val repo = SecureNodeBranding.tryRepo()
            if (repo == null) {
                o.put("state_error", "not_initialized")
            } else {
                val state = repo.debugStateSnapshot()
                o.put("state", state)
            }
        } catch (t: Throwable) {
            o.put("state_error", t.message ?: "unknown")
        }

        o.put("summary", "SDK ${BuildConfig.VERSION_NAME} | server_debug=${policy.serverEnabled} | export=${policy.allowExport}")
        return o
    }

    private fun shareText(filename: String, content: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, filename)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(send, "Share"))
    }
}
