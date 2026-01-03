package com.securenode.sdk.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API client for SecureNode branding endpoints
 */
class ApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val client: OkHttpClient
    private val baseRoot: String
    private val baseApi: String

    init {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", "application/json")
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        // Accept either:
        // - https://api.securenode.io
        // - https://api.securenode.io/api
        // Normalize to root and always try both mounts:
        // - {root}/mobile/...
        // - {root}/api/mobile/...
        val trimmed = baseUrl.trim().trimEnd('/')
        baseRoot = if (trimmed.endsWith("/api")) trimmed.dropLast(4) else trimmed
        baseApi = "$baseRoot/api"
    }

    private fun endpointCandidates(path: String): List<String> {
        // path example: "mobile/branding/sync"
        return listOf("$baseRoot/$path", "$baseApi/$path")
    }

    /**
     * Sync branding data from API
     */
    suspend fun syncBranding(since: String? = null): SyncResponse = withContext(Dispatchers.IO) {
        try {
            val urls = endpointCandidates("mobile/branding/sync").map { base ->
                if (since != null) "$base?since=$since" else base
            }

            var lastCode: Int? = null
            for (url in urls) {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (response.code == 404) continue
                if (!response.isSuccessful || body == null) {
                    lastCode = response.code
                    continue
                }

                val json = JSONObject(body)
                val brandingArray = json.getJSONArray("branding")
                val brandingList = mutableListOf<BrandingInfo>()

                for (i in 0 until brandingArray.length()) {
                    val item = brandingArray.getJSONObject(i)
                    val bn = item.optString("brand_name").takeIf { it.isNotBlank() } ?: continue
                    brandingList.add(
                        BrandingInfo(
                            phoneNumberE164 = item.getString("phone_number_e164"),
                            brandName = bn,
                            logoUrl = item.optString("logo_url").takeIf { it.isNotBlank() },
                            callReason = item.optString("call_reason").takeIf { it.isNotBlank() },
                            updatedAt = item.optString("updated_at")
                        )
                    )
                }

                return@withContext SyncResponse(
                    branding = brandingList,
                    syncedAt = json.optString("synced_at"),
                    config = run {
                        val cfg = json.optJSONObject("config")
                        SyncConfig(
                            voipDialerEnabled = cfg?.optBoolean("voip_dialer_enabled", false) ?: false,
                            mode = cfg?.optString("mode")?.takeIf { it.isNotBlank() } ?: "live",
                            brandingEnabled = cfg?.optBoolean("branding_enabled", true) ?: true
                        )
                    }
                )
            }

            throw IOException("Sync failed: ${lastCode ?: "unknown"}")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            throw e
        }
    }

    /**
     * Lookup branding for a single phone number
     */
    suspend fun lookupBranding(phoneNumber: String): BrandingInfo? = withContext(Dispatchers.IO) {
        try {
            val encodedNumber = java.net.URLEncoder.encode(phoneNumber, "UTF-8")
            val urls = endpointCandidates("mobile/branding/lookup").map { base ->
                "$base?e164=$encodedNumber"
            }

            for (url in urls) {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                if (response.code == 404) continue
                if (!response.isSuccessful || body == null) continue

                val json = JSONObject(body)
                val brandName = json.optString("brand_name").takeIf { it.isNotBlank() } ?: return@withContext null

                return@withContext BrandingInfo(
                    phoneNumberE164 = json.optString("phone_number_e164").takeIf { it.isNotBlank() } ?: json.optString("e164"),
                    brandName = brandName,
                    logoUrl = json.optString("logo_url").takeIf { it.isNotBlank() },
                    callReason = json.optString("call_reason").takeIf { it.isNotBlank() },
                    updatedAt = json.optString("updated_at")
                )
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Lookup failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "ApiClient"
    }
}

/**
 * Branding information data class
 */
data class BrandingInfo(
    val phoneNumberE164: String,
    val brandName: String?,
    val logoUrl: String?,
    val callReason: String?,
    val updatedAt: String
)

/**
 * Sync response data class
 */
data class SyncResponse(
    val branding: List<BrandingInfo>,
    val syncedAt: String,
    val config: SyncConfig = SyncConfig()
)

data class SyncConfig(
    val voipDialerEnabled: Boolean = false,
    val mode: String = "live",
    val brandingEnabled: Boolean = true
)

