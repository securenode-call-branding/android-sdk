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

    private const val PREFS_NAME = "securenode_branding_sdk"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENABLE_HTTP_LOGGING = "enable_http_logging"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_HOST_APP_NAME = "host_app_name"
    private const val KEY_APP_VERSION = "app_version"
    private const val KEY_SDK_VERSION = "sdk_version"
    private const val KEY_SYNC_SINCE_HOURS_DEFAULT = "sync_since_hours_default"
    private const val KEY_ENABLE_CONTACTS_BRANDING = "enable_contacts_branding"
    private const val KEY_ENABLE_CONTACTS_BRANDING_PHOTOS = "enable_contacts_branding_photos"
    private const val KEY_CONTACTS_BRANDING_MAX_NUMBERS_PER_CONTACT = "contacts_branding_max_numbers_per_contact"

    fun isInitialized(): Boolean = initialized.get() && repoRef.get() != null && configRef.get() != null

    fun initialize(context: Context, config: SecureNodeConfig, callback: BrandingCallback? = null) {
        if (!initialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        appContextRef.set(appContext)
        configRef.set(config)
        callbackRef.set(callback)

        Logger.configure(config.enableHttpLogging)
        persistConfig(appContext, config)

        val db = AppDatabase.get(appContext)
        val repo = BrandingRepository.create(appContext, db, config)
        repoRef.set(repo)

        repo.registerDeviceBestEffort()

        // Best-effort non-call-screening fallback
        io.securenode.branding.call.IncomingCallFallback(appContext).start()

        val wm = WorkManager.getInstance(appContext)

        wm.enqueueUniqueWork(
            BrandingSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            BrandingSyncWorker.oneTimeRequest()
        )
        wm.enqueueUniqueWork(
            EventUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
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

    internal fun bootstrapForWork(context: Context): BrandingRepository? {
        repoRef.get()?.let { return it }

        val appContext = context.applicationContext
        val cfg = configRef.get() ?: loadPersistedConfig(appContext) ?: run {
            Logger.w("Background work skipped: persisted config not found")
            return null
        }

        appContextRef.set(appContext)
        configRef.set(cfg)
        Logger.configure(cfg.enableHttpLogging)

        val db = AppDatabase.get(appContext)
        val repo = BrandingRepository.create(appContext, db, cfg)
        repoRef.set(repo)
        initialized.set(true)
        return repo
    }

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
        // Resolve branding and forward to callback (same behavior as callers using resolveBranding()).
        resolveBranding(e164, surface)
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
    } finally {
        appContextRef.get()?.let { enqueueEventUpload(it) }
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

    private fun enqueueEventUpload(context: Context) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            EventUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            EventUploadWorker.oneTimeRequest()
        )
    }

    private fun persistConfig(context: Context, config: SecureNodeConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putBoolean(KEY_ENABLE_HTTP_LOGGING, config.enableHttpLogging)
            .putString(KEY_DEVICE_ID, config.deviceId)
            .putString(KEY_HOST_APP_NAME, config.hostAppName)
            .putString(KEY_APP_VERSION, config.appVersion)
            .putString(KEY_SDK_VERSION, config.sdkVersion)
            .putInt(KEY_SYNC_SINCE_HOURS_DEFAULT, config.syncSinceHoursDefault)
            .putBoolean(KEY_ENABLE_CONTACTS_BRANDING, config.enableContactsBranding)
            .putBoolean(KEY_ENABLE_CONTACTS_BRANDING_PHOTOS, config.enableContactsBrandingPhotos)
            .putInt(KEY_CONTACTS_BRANDING_MAX_NUMBERS_PER_CONTACT, config.contactsBrandingMaxNumbersPerContact)
            .apply()
    }

    private fun loadPersistedConfig(context: Context): SecureNodeConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val baseUrl = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        val apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (baseUrl.isBlank() || apiKey.isBlank()) return null

        return SecureNodeConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            enableHttpLogging = prefs.getBoolean(KEY_ENABLE_HTTP_LOGGING, false),
            deviceId = prefs.getString(KEY_DEVICE_ID, null),
            hostAppName = prefs.getString(KEY_HOST_APP_NAME, null),
            appVersion = prefs.getString(KEY_APP_VERSION, null),
            sdkVersion = prefs.getString(KEY_SDK_VERSION, null) ?: BuildConfig.VERSION_NAME,
            syncSinceHoursDefault = prefs.getInt(KEY_SYNC_SINCE_HOURS_DEFAULT, 24 * 30),
            enableContactsBranding = prefs.getBoolean(KEY_ENABLE_CONTACTS_BRANDING, true),
            enableContactsBrandingPhotos = prefs.getBoolean(KEY_ENABLE_CONTACTS_BRANDING_PHOTOS, true),
            contactsBrandingMaxNumbersPerContact = prefs.getInt(KEY_CONTACTS_BRANDING_MAX_NUMBERS_PER_CONTACT, 200)
        )
    }

}

internal object Iso8601 {
    private const val PATTERN_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private const val PATTERN_SECONDS = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    fun nowUtcIso(): String = formatUtcIso(System.currentTimeMillis())

    fun formatUtcIso(epochMs: Long): String {
        val df = java.text.SimpleDateFormat(PATTERN_MILLIS, java.util.Locale.US)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return df.format(java.util.Date(epochMs))
    }

    fun parseUtcIsoToEpochMs(iso: String): Long? {
        val s = iso.trim()
        if (s.isBlank()) return null

        fun tryParse(pattern: String): Long? {
            return try {
                val df = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                df.timeZone = java.util.TimeZone.getTimeZone("UTC")
                df.parse(s)?.time
            } catch (_: Throwable) {
                null
            }
        }

        return tryParse(PATTERN_MILLIS) ?: tryParse(PATTERN_SECONDS)
    }
}
