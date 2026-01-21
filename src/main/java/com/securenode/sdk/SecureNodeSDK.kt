package com.securenode.sdk.sample

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.util.Log
import com.securenode.sdk.sample.database.BrandingDatabase
import com.securenode.sdk.sample.database.PendingEventEntity
import com.securenode.sdk.sample.network.ApiClient
import com.securenode.sdk.sample.network.BrandingInfo
import com.securenode.sdk.sample.network.SyncResponse
import com.securenode.sdk.sample.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * SecureNode Android SDK
 * 
 * Provides branding data sync + lookup for incoming call surfaces (PSTN assist) and in-app calling (VoIP) integrations.
 * Handles local caching, API synchronization, and secure credential storage.
 */
class SecureNodeSDK private constructor(
    private val context: Context,
    private val config: SecureNodeConfig
) {
    private val apiClient: ApiClient
    private val database: BrandingDatabase
    // Initialize secure key storage
    private val keyStoreManager: KeyStoreManager = KeyStoreManager(context)
    private val imageCache: ImageCache
    private val scope = CoroutineScope(Dispatchers.IO)

    init {

        // Retrieve or store API key securely
        val storedApiKey = keyStoreManager.getApiKey()
        if (storedApiKey == null && config.apiKey.isNotBlank()) {
            keyStoreManager.saveApiKey(config.apiKey)
        }
        val apiKey = storedApiKey ?: config.apiKey
        
        // Initialize API client (includes SecureNode client CA trust on Android)
        apiClient = ApiClient(context, config.apiUrl, apiKey)

        // Register device (best-effort; fail-open)
        scope.launch {
            try {
                val pm = context.packageManager
                val pkg = context.packageName
                val appVersion = runCatching {
                    val pi = pm.getPackageInfo(pkg, 0)
                    pi.versionName ?: pi.longVersionCode.toString()
                }.getOrNull()
                apiClient.registerDevice(
                    platform = "android",
                    deviceType = Build.MODEL,
                    osVersion = "Android ${Build.VERSION.RELEASE}",
                    appVersion = appVersion,
                    sdkVersion = null,
                    customerName = null,
                    customerAccountNumber = null
                )
            } catch {
                // ignore
            }
        }
        
        // Initialize database
        database = BrandingDatabase.getDatabase(context)
        
        // Initialize image cache
        imageCache = ImageCache(context)
        
        // Clean up old branding data periodically
        scope.launch {
            cleanupOldBranding()
        }
    }

    companion object {
        private const val TAG = "SecureNodeSDK"
        private const val CACHE_RETENTION_DAYS = 30L

        private fun nowIsoUtc(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }
        
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: SecureNodeSDK? = null

        /**
         * Initialize the SDK singleton instance
         */
        fun initialize(context: Context, config: SecureNodeConfig): SecureNodeSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureNodeSDK(context.applicationContext, config).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Get the SDK instance (must be initialized first)
         */
        fun getInstance(): SecureNodeSDK {
            return INSTANCE ?: throw IllegalStateException(
                "SecureNodeSDK not initialized. Call initialize() first."
            )
        }
    }

    /**
     * Sync branding data from the API
     * 
     * @param since Optional ISO timestamp for incremental sync
     * @param callback Result callback with SyncResponse or error
     */
    fun syncBranding(
        since: String? = null,
        callback: (Result<SyncResponse>) -> Unit
    ) {
        scope.launch {
            try {
                val response = apiClient.syncBranding(since)
                
                withContext(Dispatchers.IO) {
                    // Store in local database
                    response.branding.forEach { branding ->
                        val bn = branding.brandName
                        if (bn.isNullOrBlank()) return@forEach
                        database.brandingDao().insertBranding(
                            com.securenode.sdk.sample.database.BrandingEntity(
                                phoneNumberE164 = branding.phoneNumberE164,
                                brandName = bn,
                                logoUrl = branding.logoUrl,
                                callReason = branding.callReason,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        
                        // Pre-cache images in background
                        branding.logoUrl?.let { url ->
                            imageCache.loadImageAsync(url)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    callback(Result.success(response))
                }

                // Best-effort: flush queued analytics events in the background.
                // Must never block branding sync success.
                scope.launch {
                    try {
                        flushQueuedEvents()
                    } catch {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Record a missed call event (analytics-only; never blocks call UX).
     *
     * Returns a stable call_id which you can later pass to [recordCallReturned] to track conversions.
     *
     * `brandingDisplayed` should be true when your integration displayed any caller identity (full/partial).
     */
    @JvmOverloads
    fun recordMissedCall(
        phoneNumberE164: String,
        brandingDisplayed: Boolean,
        surface: String? = null,
        occurredAtIso: String? = null
    ): String {
        val callId = UUID.randomUUID().toString()
        queueEventIfTracked(
            phoneNumberE164 = phoneNumberE164,
            outcome = "missed",
            surface = surface,
            displayedAtIso = occurredAtIso,
            eventKey = "missed:$callId",
            metaJson = JSONObject()
                .put("call_id", callId)
                .put("branding_displayed", brandingDisplayed)
                .toString()
        )
        return callId
    }

    /**
     * Record that a previously missed call was returned later.
     *
     * - callId should come from [recordMissedCall]
     * - brandingDisplayedAtMissed should match what you passed to recordMissedCall (full/partial = true)
     */
    @JvmOverloads
    fun recordCallReturned(
        phoneNumberE164: String,
        callId: String,
        brandingDisplayedAtMissed: Boolean,
        surface: String? = null,
        occurredAtIso: String? = null
    ) {
        queueEventIfTracked(
            phoneNumberE164 = phoneNumberE164,
            outcome = "call_returned",
            surface = surface,
            displayedAtIso = occurredAtIso,
            eventKey = "call_returned:$callId",
            metaJson = JSONObject()
                .put("call_id", callId)
                .put("branding_displayed_at_missed", brandingDisplayedAtMissed)
                .toString()
        )
    }

    /**
     * Record that an incoming call was observed by the OS/integration (analytics-only).
     * This is the "total calls seen" baseline for impact graphs.
     *
     * Returns a stable call_id you can optionally reuse for other call outcome events.
     */
    @JvmOverloads
    fun recordCallSeen(
        phoneNumberE164: String,
        brandingDisplayed: Boolean,
        surface: String? = null,
        occurredAtIso: String? = null
    ): String {
        val callId = UUID.randomUUID().toString()
        queueEventIfTracked(
            phoneNumberE164 = phoneNumberE164,
            outcome = "call_seen",
            surface = surface,
            displayedAtIso = occurredAtIso,
            eventKey = "call_seen:$callId",
            metaJson = JSONObject()
                .put("call_id", callId)
                .put("branding_displayed", brandingDisplayed)
                .toString()
        )
        return callId
    }

    private fun queueEventIfTracked(
        phoneNumberE164: String,
        outcome: String,
        surface: String?,
        displayedAtIso: String?,
        eventKey: String,
        metaJson: String?
    ) {
        val now = System.currentTimeMillis()
        val displayedAt = displayedAtIso ?: nowIsoUtc()
        scope.launch {
            try {
                // Only track numbers that are approved + currently in use (synced local cache).
                // This means: only when the number exists in the local branding table.
                val tracked = database.brandingDao().getBranding(phoneNumberE164) != null
                if (!tracked) return@launch

                database.pendingEventDao().insert(
                    PendingEventEntity(
                        eventKey = eventKey,
                        phoneNumberE164 = phoneNumberE164,
                        outcome = outcome,
                        surface = surface,
                        displayedAt = displayedAt,
                        metaJson = metaJson,
                        createdAt = now,
                        status = "queued",
                        attempts = 0,
                        lastError = null,
                        dropReason = null,
                        updatedAt = now
                    )
                )
            } catch (e: Exception) {
                // Never break caller UX on analytics failures.
                Log.w(TAG, "Failed to queue event ($outcome)", e)
            }
        }
    }

    /**
     * Flush queued events. Best-effort, low retry pressure:
     * - after 3 failed attempts, drop the event and record the reason.
     */
    suspend fun flushQueuedEvents(limit: Int = 25) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val queued = try {
            database.pendingEventDao().listQueued(limit)
        } catch (_e: Exception) {
            emptyList()
        }
        if (queued.isEmpty()) return@withContext

        val sentIds = mutableListOf<Long>()
        for (evt in queued) {
            val ok = try {
                apiClient.recordBrandingEvent(
                    phoneNumberE164 = evt.phoneNumberE164,
                    outcome = evt.outcome,
                    surface = evt.surface,
                    displayedAtIso = evt.displayedAt,
                    eventKey = evt.eventKey,
                    metaJson = evt.metaJson
                )
            } catch (e: Exception) {
                database.pendingEventDao().recordFailure(evt.id, e.message ?: "upload_failed", now)
                false
            }

            if (ok) {
                sentIds.add(evt.id)
            } else {
                val attemptsNext = evt.attempts + 1
                if (attemptsNext >= 3) {
                    database.pendingEventDao().markDropped(evt.id, "retries_exceeded", now)
                } else {
                    database.pendingEventDao().recordFailure(evt.id, "upload_failed", now)
                }
            }
        }

        if (sentIds.isNotEmpty()) {
            database.pendingEventDao().markSent(sentIds, now)
        }

        // Cleanup old completed rows to keep DB small.
        try {
            val cutoff = now - (7L * 24L * 60L * 60L * 1000L)
            database.pendingEventDao().deleteCompletedOlderThan(cutoff)
        } catch {
            // ignore
        }
    }

    /**
     * Java-friendly sync callback shape.
     *
     * Kotlin's `Result<T>` is awkward from Java (extension helpers like `isSuccess()` are not callable),
     * so this provides an explicit `(response, error)` contract for Java callers.
     */
    fun interface SyncBrandingCallback {
        fun onResult(response: SyncResponse?, error: Throwable?)
    }

    @JvmOverloads
    fun syncBrandingJava(
        since: String? = null,
        callback: SyncBrandingCallback
    ) {
        syncBranding(since) { result ->
            result.fold(
                onSuccess = { callback.onResult(it, null) },
                onFailure = { callback.onResult(null, it) }
            )
        }
    }

    /**
     * Get branding for a specific phone number
     * 
     * @param phoneNumber E.164 formatted phone number
     * @param callback Result callback with BrandingInfo or error
     */
    fun getBranding(
        phoneNumber: String,
        callback: (Result<BrandingInfo>) -> Unit
    ) {
        scope.launch {
            try {
                // First try local database
                val cached = database.brandingDao().getBranding(phoneNumber)
                if (cached != null) {
                    val branding = BrandingInfo(
                        phoneNumberE164 = cached.phoneNumberE164,
                        brandName = cached.brandName,
                        logoUrl = cached.logoUrl,
                        callReason = cached.callReason,
                        updatedAt = cached.updatedAt.toString()
                    )
                    withContext(Dispatchers.Main) {
                        callback(Result.success(branding))
                    }
                    return@launch
                }
                
                // Fallback to API lookup
                val branding = apiClient.lookupBranding(phoneNumber)
                if (branding?.brandName != null) {
                    // Cache for next time
                    database.brandingDao().insertBranding(
                        com.securenode.sdk.sample.database.BrandingEntity(
                            phoneNumberE164 = branding.phoneNumberE164,
                            brandName = branding.brandName,
                            logoUrl = branding.logoUrl,
                            callReason = branding.callReason,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                
                withContext(Dispatchers.Main) {
                    callback(Result.success(branding ?: BrandingInfo(
                        phoneNumberE164 = phoneNumber,
                        brandName = null,
                        logoUrl = null,
                        callReason = null,
                        updatedAt = ""
                    )))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Get branding failed", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
        }
    }

    /**
     * Create a branded Connection for ConnectionService
     * 
     * @param phoneNumber E.164 formatted phone number
     * @param connectionRequest Original connection request
     * @return Branded Connection instance
     */
    fun createBrandedConnection(
        phoneNumber: String?,
        connectionRequest: ConnectionRequest?
    ): Connection {
        return SecureNodeConnection(
            context = context,
            phoneNumber = phoneNumber,
            connectionRequest = connectionRequest,
            database = database,
            apiClient = apiClient,
            imageCache = imageCache
        )
    }

    /**
     * Clean up old branding data (older than retention period)
     */
    private suspend fun cleanupOldBranding() {
        try {
            val cutoffTime = System.currentTimeMillis() - (CACHE_RETENTION_DAYS * 24 * 60 * 60 * 1000)
            database.brandingDao().deleteOldBranding(cutoffTime)
            imageCache.cleanupOldImages()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * Future: Secure Voice / VoIP channel gate.
     * This SDK build ships the surface as a stub (off by default).
     */
    fun isSecureVoiceEnabled(): Boolean = false
}

/**
 * SDK Configuration
 */
data class SecureNodeConfig(
    val apiUrl: String,
    val apiKey: String = "sn_live_de23756e5c16bcd94f763f5a8320ccb2"
)

