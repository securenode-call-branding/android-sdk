package io.securenode.branding.net

import io.securenode.branding.SecureNodeConfig
import io.securenode.branding.telemetry.Logger
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.util.concurrent.TimeUnit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

object ApiClient {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(config: SecureNodeConfig): SecureNodeApi {
        val logging = HttpLoggingInterceptor { msg -> Logger.d(msg) }.apply {
            level = if (config.enableHttpLogging) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(config.apiKey))
            .addInterceptor(logging)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl.trimEnd('/') + "/")
            .client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create()
    }

    private class ApiKeyInterceptor(private val apiKey: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val req = chain.request().newBuilder()
                .header("X-API-Key", apiKey)
                .build()

            // IMPORTANT: Only throw IOException (or subclasses) from OkHttp interceptors.
            // Retrofit will translate non-2xx into HttpException automatically.
            return chain.proceed(req)
        }
    }
}
