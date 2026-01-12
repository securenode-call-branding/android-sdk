package io.securenode.branding.debug

import io.securenode.branding.telemetry.Logger
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object DebugUploadApi {
    private val json = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun upload(baseUrl: String, apiKey: String, payload: String) {
        val body = payload.toRequestBody(json)
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/mobile/debug/upload")
            .addHeader("X-API-Key", apiKey)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Debug upload failed: ${resp.code}")
            }
            Logger.i("Remote debug package uploaded (${resp.code})")
        }
    }
}
