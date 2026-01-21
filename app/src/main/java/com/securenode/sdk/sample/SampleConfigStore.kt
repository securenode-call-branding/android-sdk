package com.securenode.sdk

import android.content.Context

object SampleConfigStore {
    private const val PREFS = "securenode_sample_cfg"
    private const val KEY_BRANDING_ENABLED = "branding_enabled"
    private const val KEY_MODE = "mode"

    fun setBrandingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BRANDING_ENABLED, enabled)
            .apply()
    }

    fun isBrandingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BRANDING_ENABLED, true)
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .apply()
    }

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "live") ?: "live"
    }
}


