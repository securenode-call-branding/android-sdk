package io.securenode.branding

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.securenode.branding.data.db.AppDatabase
import io.securenode.branding.data.repo.BrandingRepository
import io.securenode.branding.sync.BrandingSyncWorker
import io.securenode.branding.sync.EventUploadWorker
import io.securenode.branding.telemetry.Logger
import io.securenode.branding.contacts.ContactsBrandingSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object SecureNodeBranding {
    private val initialized = AtomicBoolean(false)
    private val repoRef = AtomicReference<BrandingRepository?>(null)
    private val callbackRef = AtomicReference<BrandingCallback?>(null)
    private val configRef = AtomicReference<SecureNodeConfig?>(null)
    private val appContextRef = AtomicReference<Context?>(null)

    fun isInitialized(): Boolean = initialized.get() && repoRef.get() != null && configRef.get() != null

    fun initialize(context: Context, config: SecureNodeConfig, callback: BrandingCallback? = null) {
        if (!initialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        appContextRef.set(appContext)
        configRef.set(config)
        callbackRef.set(callback)

        Logger.configure(config.enableHttpLogging)

        val db = AppDatabase.get(appContext)
        val repo = BrandingRepository.create(appContext, db, config)
        repoRef.set(repo)

        repo.registerDeviceBestEffort()

        // Best-effort non-call-screening fallback
        io.securenode.branding.call.IncomingCallFallback(appContext).start()

        val wm = WorkManager.getInstance(appContext)

        wm.enqueueUniqueWork(
            BrandingSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            BrandingSyncWorker.oneTimeRequest()
        )
        wm.enqueueUniqueWork(
            EventUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            EventUploadWorker.oneTimeRequest()
        )

        wm.enqueueUniquePeriodicWork(
            BrandingSyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            BrandingSyncWorker.periodicRequest()
        )
        wm.enqueueUniquePeriodicWork(
            EventUploadWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            EventUploadWorker.periodicRequest()
        )
    }

    internal fun repo(): BrandingRepository = requireNotNull(repoRef.get()) { "SecureNodeBranding not initialized" }
    internal fun config(): SecureNodeConfig = requireNotNull(configRef.get()) { "SecureNodeBranding not initialized" }
    internal fun callback(): BrandingCallback? = callbackRef.get()
    internal fun tryRepo(): BrandingRepository? = repoRef.get()
    internal fun tryConfig(): SecureNodeConfig? = configRef.get()

    /**
     * Optional: set/replace the callback after initialization (e.g., when a UI screen is active).
     */
    fun setCallback(callback: BrandingCallback?) {
        callbackRef.set(callback)
    }

    /**
 * Host-app hook for OEM-restricted devices where the OS does not expose the incoming number unless
 * Call Screening is enabled (or the app is the default dialer).
 */
@Suppress("UNUSED_PARAMETER")
fun onIncomingCallObserved(
    e164: String,
    surface: String = "host_observed",
    subscriptionId: Int? = null,
    simSlotIndex: Int? = null,
    phoneAccountId: String? = null,
    isSpam: Boolean? = null,
    isUnknown: Boolean? = null,
    displayedAtEpochMs: Long? = null
) {
    if (!isInitialized()) {
        Logger.w("onIncomingCallObserved ignored; SDK not initialized")
        return
    }

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            // Resolve branding and forward to callback (same behavior as callers using resolveBranding()).
            resolveBranding(e164, surface)
        } finally {
            appContextRef.get()?.let { ctx ->
                androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork(
                    io.securenode.branding.sync.EventUploadWorker.UNIQUE_WORK_NAME,
                    androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                    io.securenode.branding.sync.EventUploadWorker.oneTimeRequest()
                )
            }
        }
    }
}

suspend fun resolveBranding(e164: String, surface: String = "incoming_call"): BrandingResult {
    if (!isInitialized()) {
        val err = SecureNodeError.NotInitialized()
        callback()?.onError(err)
        return BrandingResult.error(e164, err)
    }

    return try {
        val result = repo().resolveBranding(e164, surface)
        callback()?.onBrandingResolved(result)
        result
    } catch (t: Throwable) {
        val err = SecureNodeError.fromThrowable(t)
        callback()?.onError(err)
        BrandingResult.error(e164, err)
    }
    }

/**
 * Call this from your host app's About/App Info UI (e.g., tapping a version row 7 times).
 * Debug console is SERVER-GATED and will only unlock if your backend enables it via sync config.
 */
fun onAboutTapped(context: Context): Boolean {
    return io.securenode.branding.debug.DebugGate.onAboutTapped(context.applicationContext)
}

/**
 * Optional helper to open the debug console after unlock.
 */
fun openDebugConsole(context: Context) {
    val ctx = context.applicationContext
    val intent = android.content.Intent().apply {
        setClassName(ctx, "io.securenode.branding.debug.DebugActivity")
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}

/**
 * Debug-only local override for the debug console when server policy can't be fetched (e.g. no connectivity).
 *
 * Safety: has **no effect** in release builds of the SDK.
 */
fun setLocalDebugOverride(context: Context, enabled: Boolean) {
    io.securenode.branding.debug.DebugGate.setLocalOverride(context.applicationContext, enabled)
}

/**
 * Optional: manually trigger a Contacts branding sync (Option B).
 * Requires READ_CONTACTS + WRITE_CONTACTS runtime permissions granted.
 */
fun syncContactsBrandingNow(context: Context) {
    val repo = tryRepo()
    if (repo == null) {
        Logger.w("Contacts branding sync skipped; SDK not initialized")
        return
    }
    if (!ContactsBrandingSync.hasRequiredPermissions(context.applicationContext)) {
        Logger.w("Contacts branding sync skipped: missing contacts permissions")
        return
    }
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            repo.syncContactsBrandingBestEffortFull()
        } catch (t: Throwable) {
            Logger.w("Contacts branding sync failed", t)
        }
    }
}


internal suspend fun handleRemoteDebugRequest(policy: io.securenode.branding.debug.DebugPolicy) {
    if (!policy.requestUpload) return
    if (!policy.allowExport) return
    io.securenode.branding.debug.DebugUploader.upload(policy)
}

}
