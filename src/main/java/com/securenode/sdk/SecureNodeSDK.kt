package com.securenode.sdk

import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.util.Log
import com.securenode.sdk.database.BrandingDatabase
import com.securenode.sdk.network.ApiClient
import com.securenode.sdk.network.BrandingInfo
import com.securenode.sdk.network.SyncResponse
import com.securenode.sdk.security.KeyStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val keyStoreManager: KeyStoreManager
    private val imageCache: ImageCache
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Initialize secure key storage
        keyStoreManager = KeyStoreManager(context)
        
        // Retrieve or store API key securely
        val storedApiKey = keyStoreManager.getApiKey()
        if (storedApiKey == null && config.apiKey.isNotBlank()) {
            keyStoreManager.saveApiKey(config.apiKey)
        }
        val apiKey = storedApiKey ?: config.apiKey
        
        // Initialize API client
        apiClient = ApiClient(config.apiUrl, apiKey)
        
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
                            com.securenode.sdk.database.BrandingEntity(
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
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                withContext(Dispatchers.Main) {
                    callback(Result.failure(e))
                }
            }
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
                if (branding != null && branding.brandName != null) {
                    // Cache for next time
                    database.brandingDao().insertBranding(
                        com.securenode.sdk.database.BrandingEntity(
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
    val apiKey: String = ""
)

