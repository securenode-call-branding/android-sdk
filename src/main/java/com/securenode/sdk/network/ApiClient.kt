package com.securenode.sdk.sample.network

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.securenode.sdk.sample.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * API client for SecureNode branding endpoints
 */
class ApiClient(
    private val context: Context?,
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

        val builder = OkHttpClient.Builder()
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

        // Trust the platform roots PLUS the SecureNode client CA (prod-ca-2021) when present.
        buildCompositeTrustManager()?.let { composite ->
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(composite), null)
            }
            builder.sslSocketFactory(sslContext.socketFactory, composite)
        }

        client = builder.build()

        // Accept either:
        // - https://verify.securenode.io
        // - https://verify.securenode.io/api
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

    private fun deviceIdOrNull(): String? {
        val ctx = context ?: return null
        return try {
            DeviceIdentity.getOrCreateDeviceId(ctx)
        } catch {
            null
        }
    }

    private fun withQuery(base: String, params: Map<String, String?>): String {
        val parts = params.entries
            .filter { !it.value.isNullOrBlank() }
            .map { (k, v) ->
                val encV = java.net.URLEncoder.encode(v, "UTF-8")
                "${k}=${encV}"
            }
        if (parts.isEmpty()) return base
        val joiner = if (base.contains("?")) "&" else "?"
        return base + joiner + parts.joinToString("&")
    }

    /**
     * Sync branding data from API
     */
    suspend fun syncBranding(since: String? = null): SyncResponse = withContext(Dispatchers.IO) {
        try {
            val urls = endpointCandidates("mobile/branding/sync").map { base ->
                withQuery(
                    base,
                    mapOf(
                        "since" to since,
                        "device_id" to deviceIdOrNull()
                    )
                )
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

    /**
     * Best-effort: register a device install for Connected Devices view.
     */
    suspend fun registerDevice(
        platform: String,
        deviceType: String? = null,
        osVersion: String? = null,
        appVersion: String? = null,
        sdkVersion: String? = null,
        customerName: String? = null,
        customerAccountNumber: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceId = deviceIdOrNull() ?: return@withContext false
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("platform", platform)
            .put("device_type", deviceType)
            .put("os_version", osVersion)
            .put("app_version", appVersion)
            .put("sdk_version", sdkVersion)
            .put("customer_name", customerName)
            .put("customer_account_number", customerAccountNumber)
            .toString()

        val body = payload.toRequestBody("application/json".toMediaType())
        val urls = endpointCandidates("mobile/device/register")

        for (url in urls) {
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            if (response.code == 404) continue
            if (response.isSuccessful) return@withContext true
        }
        return@withContext false
    }

    /**
     * Best-effort: record a branding imprint (call branding activity).
     * This is used to drive per-device activity sparklines in the portal.
     */
    suspend fun recordImprint(
        phoneNumberE164: String,
        displayedAtIso: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val deviceId = deviceIdOrNull() ?: return@withContext false
        val payload = JSONObject()
            .put("phone_number_e164", phoneNumberE164)
            .put("displayed_at", displayedAtIso)
            .put("device_id", deviceId)
            .toString()
        val body = payload.toRequestBody("application/json".toMediaType())
        val urls = endpointCandidates("mobile/branding/imprint")

        for (url in urls) {
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            if (response.code == 404) continue
            if (response.isSuccessful) return@withContext true
        }
        return@withContext false
    }

    private fun buildCompositeTrustManager(): X509TrustManager? {
        val ctx = context ?: return null
        val defaultTm = defaultTrustManager() ?: return null
        val extraTm = ProdCa2021.trustManager() ?: return defaultTm

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                // Not used for HTTPS clients.
                defaultTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                } catch (_e: Exception) {
                    extraTm.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                val a = defaultTm.acceptedIssuers
                val b = extraTm.acceptedIssuers
                return (a + b)
            }
        }
    }

    private fun defaultTrustManager(): X509TrustManager? {
        return try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
            tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } catch (_e: Exception) {
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

