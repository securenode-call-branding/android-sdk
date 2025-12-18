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
    }

    /**
     * Sync branding data from API
     */
    suspend fun syncBranding(since: String? = null): SyncResponse = withContext(Dispatchers.IO) {
        try {
            val url = if (since != null) {
                "$baseUrl/mobile/branding/sync?since=$since"
            } else {
                "$baseUrl/mobile/branding/sync"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                throw IOException("Sync failed: ${response.code}")
            }

            val json = JSONObject(body)
            val brandingArray = json.getJSONArray("branding")
            val brandingList = mutableListOf<BrandingInfo>()

            for (i in 0 until brandingArray.length()) {
                val item = brandingArray.getJSONObject(i)
                brandingList.add(
                    BrandingInfo(
                        phoneNumberE164 = item.getString("phone_number_e164"),
                        brandName = item.getString("brand_name"),
                        logoUrl = item.optString("logo_url").takeIf { it.isNotBlank() },
                        callReason = item.optString("call_reason").takeIf { it.isNotBlank() },
                        updatedAt = item.getString("updated_at")
                    )
                )
            }

            SyncResponse(
                branding = brandingList,
                syncedAt = json.getString("synced_at")
            )
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
            val url = "$baseUrl/mobile/branding/lookup?e164=$encodedNumber"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext null
            }

            val json = JSONObject(body)
            val brandName = json.optString("brand_name").takeIf { it.isNotBlank() }

            if (brandName == null) {
                return@withContext null
            }

            BrandingInfo(
                phoneNumberE164 = json.getString("e164"),
                brandName = brandName,
                logoUrl = json.optString("logo_url").takeIf { it.isNotBlank() },
                callReason = json.optString("call_reason").takeIf { it.isNotBlank() },
                updatedAt = ""
            )
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
    val syncedAt: String
)

