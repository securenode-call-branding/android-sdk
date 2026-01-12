package io.securenode.branding.debug

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.securenode.branding.BuildConfig
import io.securenode.branding.telemetry.Logger

internal object DebugGate {
    private const val REQUIRED_TAPS = 7
    private const val RESET_MS = 1500L
    private const val PREFS = "securenode_debug_gate"
    private const val KEY_LOCAL_OVERRIDE = "local_override_enabled"

    private var tapCount = 0
    private var lastTap = 0L
    private var locallyUnlocked = false

    fun setLocalOverride(context: Context, enabled: Boolean) {
        // Safety: never allow local override in release builds.
        if (!BuildConfig.DEBUG) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCAL_OVERRIDE, enabled)
            .apply()

        locallyUnlocked = enabled
        enableDebugActivity(context, enabled)
        Logger.w("Debug local override set=$enabled (debug builds only)")
    }

    private fun isLocalOverrideEnabled(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCAL_OVERRIDE, false)
    }

    fun onAboutTapped(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTap > RESET_MS) tapCount = 0
        lastTap = now
        tapCount++

        val policy = DebugPolicy.load(context)
        if (!policy.isActive() && !isLocalOverrideEnabled(context)) {
            if (tapCount >= REQUIRED_TAPS) {
                Logger.w("Debug tap sequence detected but server gate disabled/expired")
                tapCount = 0
            }
            return false
        }

        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0
            locallyUnlocked = true
            enableDebugActivity(context, true)
            Logger.i("Debug console unlocked (server-gated)")
            return true
        }
        return false
    }

    fun isDebugAvailable(context: Context): Boolean {
        val policy = DebugPolicy.load(context)
        return locallyUnlocked && (policy.isActive() || isLocalOverrideEnabled(context))
    }

    fun applyServerPolicy(context: Context, policy: DebugPolicy) {
        DebugPolicy.save(context, policy)
        // If server disables, also disable component to hide entry points.
        if (!policy.isActive() && !isLocalOverrideEnabled(context)) {
            locallyUnlocked = false
            enableDebugActivity(context, false)
        }
    }

    private fun enableDebugActivity(context: Context, enabled: Boolean) {
        val cn = ComponentName(context, DebugActivity::class.java)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            cn,
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}
