package com.securenode.sdk

import android.content.Context
import java.util.UUID

/**
 * Stable per-install device identifier.
 *
 * - Generated once and persisted in SharedPreferences
 * - Used for connected devices telemetry + server-initiated debug policy + activity sparklines
 */
internal object DeviceIdentity {
    private const val PREFS = "securenode_sdk_cfg"
    private const val KEY_DEVICE_ID = "device_id"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }
}

