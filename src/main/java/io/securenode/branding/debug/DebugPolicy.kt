package io.securenode.branding.debug

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class DebugPolicy(
    val serverEnabled: Boolean,
    val expiresAtEpochMs: Long? = null,
    val allowExport: Boolean = true,
    val requestUpload: Boolean = false
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean {
        if (!serverEnabled) return false
        val exp = expiresAtEpochMs ?: return true
        return now <= exp
    }

    companion object {
        private const val PREFS = "securenode_debug"
        private const val KEY_JSON = "policy_json"

        fun load(context: Context): DebugPolicy {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_JSON, null) ?: return DebugPolicy(false, null, true)
            return try {
                val o = JSONObject(raw)
                DebugPolicy(
                    serverEnabled = o.optBoolean("serverEnabled", false),
                    expiresAtEpochMs = if (o.has("expiresAtEpochMs")) o.optLong("expiresAtEpochMs") else null,
                    allowExport = o.optBoolean("allowExport", true),
                    requestUpload = o.optBoolean("requestUpload", false)
                )
            } catch (_: Throwable) {
                DebugPolicy(false, null, true)
            }
        }

        fun save(context: Context, policy: DebugPolicy) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val o = JSONObject()
            o.put("serverEnabled", policy.serverEnabled)
            policy.expiresAtEpochMs?.let { o.put("expiresAtEpochMs", it) }
            o.put("allowExport", policy.allowExport)
            o.put("requestUpload", policy.requestUpload)
            prefs.edit().putString(KEY_JSON, o.toString()).apply()
        }
    }
}
