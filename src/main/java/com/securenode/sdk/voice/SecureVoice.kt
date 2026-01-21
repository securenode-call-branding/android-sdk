package com.securenode.sdk.sample.voice

import android.content.Context
import android.util.Log
import com.securenode.sdk.sample.SecureNodeSDK

/**
 * Secure Voice (VoIP / SIP) upgrade path.
 *
 * This is intentionally a thin façade today:
 * - Clients can ship it in their release, disabled by default.
 * - Later we can add a SIP engine implementation without changing the public API.
 */
object SecureVoice {
    private const val TAG = "SecureVoice"

    /**
     * Returns true only when:
     * - local flag is enabled in SecureNodeOptions
     * - server flag `voip_dialer_enabled` is enabled
     */
    @JvmStatic
    fun isEnabled(sdk: SecureNodeSDK): Boolean = sdk.isSecureVoiceEnabled()

    /**
     * Placeholder start hook (future).
     * Today this does nothing other than validate gating.
     */
    @JvmStatic
    fun start(context: Context) {
        val sdk = try { SecureNodeSDK.getInstance() } catch (_: Throwable) { null }
        if (sdk == null) {
            Log.w(TAG, "SDK not initialized; call SecureNode.install(...) first")
            return
        }
        if (!sdk.isSecureVoiceEnabled()) {
            Log.i(TAG, "Secure Voice is disabled (local flag or server gate).")
            return
        }
        // Future: initialize SIP engine + register dialer / connection service integration.
        Log.i(TAG, "Secure Voice start() (stub) — enabled and ready for future SIP engine.")
    }
}


