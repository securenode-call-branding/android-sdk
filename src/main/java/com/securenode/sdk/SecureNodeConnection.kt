package com.securenode.sdk

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.StatusHints
import android.telecom.TelecomManager
import android.util.Log
import com.securenode.sdk.database.BrandingDatabase
import com.securenode.sdk.network.ApiClient
import com.securenode.sdk.network.BrandingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * SecureNode branded Connection implementation
 * 
 * Applies branding information to incoming calls via ConnectionService.
 */
internal class SecureNodeConnection(
    private val context: Context,
    private val phoneNumber: String?,
    private val connectionRequest: ConnectionRequest?,
    private val database: BrandingDatabase,
    private val apiClient: ApiClient,
    private val imageCache: ImageCache,
    private val campaignId: String?
) : Connection() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var brandingApplied = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
        }
        setAudioModeIsVoip(true)
        
        // Lookup and apply branding
        phoneNumber?.let { number ->
            applyBrandingAsync(number)
        } ?: run {
            setActive()
        }
    }

    /**
     * Asynchronously lookup and apply branding
     */
    private fun applyBrandingAsync(phoneNumber: String) {
        scope.launch {
            try {
                // First try local database (fastest)
                val cached = database.brandingDao().getBranding(phoneNumber)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        applyBranding(
                            phoneNumberE164 = phoneNumber,
                            brandName = cached.brandName,
                            logoUrl = cached.logoUrl,
                            callReason = cached.callReason
                        )
                    }
                    return@launch
                }
                
                // Fallback to API lookup
                val branding = apiClient.lookupBranding(phoneNumber)
                if (branding?.brandName != null) {
                    // Cache for next time
                    database.run {
                        brandingDao().insertBranding(
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
                        applyBranding(
                            phoneNumberE164 = phoneNumber,
                            brandName = branding.brandName,
                            logoUrl = branding.logoUrl,
                            callReason = branding.callReason
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        setActive()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply branding", e)
                withContext(Dispatchers.Main) {
                    setActive()
                }
            }
        }
    }

    /**
     * Apply branding to the connection
     */
    private fun applyBranding(
        phoneNumberE164: String,
        brandName: String?,
        logoUrl: String?,
        callReason: String?
    ) {
        if (brandingApplied) return
        
        try {
            // Set display name
            brandName?.let { name ->
                setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED)
            }
            // NOTE:
            // We intentionally do not attempt to set caller photos/status hints here.
            // Carrier/OS surfaces vary and some APIs are not available on all versions.
            // Keep this path lean + reliable; use notifications/UX surfaces for richer branding when needed.
            
            brandingApplied = true
            setActive()

            // Best-effort: record imprint for portal activity sparklines (never blocks call UX)
            if (!brandName.isNullOrBlank()) {
                scope.launch {
                    try {
                        apiClient.recordImprint(
                            phoneNumberE164 = phoneNumberE164,
                            platform = "android",
                            osVersion = Build.VERSION.RELEASE,
                            deviceModel = deviceModelHash(),
                            campaignId = campaignId
                        )
                    } catch {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply branding", e)
            setActive()
        }
    }

    override fun onAnswer() {
        super.onAnswer()
        setActive()
    }

    override fun onReject() {
        super.onReject()
        destroy()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        destroy()
    }

    override fun onAbort() {
        super.onAbort()
        destroy()
    }

    companion object {
        private const val TAG = "SecureNodeConnection"
    }

    private fun deviceModelHash(): String? = sha256Hex(Build.MODEL)

    private fun sha256Hex(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

