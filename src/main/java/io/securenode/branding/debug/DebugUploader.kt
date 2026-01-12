package io.securenode.branding.debug

import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.telemetry.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal object DebugUploader {
    suspend fun upload(policy: DebugPolicy) {
        val repo = SecureNodeBranding.tryRepo()
        val cfg = SecureNodeBranding.tryConfig()
        if (repo == null || cfg == null) {
            Logger.w("Remote debug upload skipped: SDK not initialized")
            return
        }

        val state = repo.debugStateSnapshot()
        val logs = io.securenode.branding.telemetry.Logger.snapshotText()

        val payload = JSONObject()
        payload.put("device_id", repo.deviceId())
        payload.put("created_at", Instant.now().toString())
        policy.expiresAtEpochMs?.let {
            payload.put("expires_at", Instant.ofEpochMilli(it).toString())
        }
        payload.put("nonce", UUID.randomUUID().toString())
        payload.put("state", state)
        payload.put("logs", logs)

        withContext(Dispatchers.IO) {
            DebugUploadApi.upload(cfg.baseUrl, cfg.apiKey, payload.toString())
        }
    }
}
