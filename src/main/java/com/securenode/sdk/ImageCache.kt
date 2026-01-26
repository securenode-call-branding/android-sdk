package com.securenode.sdk

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Image cache manager for branding logos
 * 
 * Stores images in app's cache directory for fast retrieval.
 */
class ImageCache(private val context: Context) {
    private val cacheDir: File = File(context.cacheDir, "SecureNodeBranding").apply {
        if (!exists()) {
            mkdirs()
        }
    }
    private val client: OkHttpClient

    init {

        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get cached image URI if available
     */
    fun getImage(urlString: String): Uri? {
        val filename = urlToFilename(urlString)
        val file = File(cacheDir, filename)
        return if (file.exists() && file.length() > 0) {
            Uri.fromFile(file)
        } else {
            null
        }
    }

    /**
     * Load image asynchronously and cache it
     */
    fun loadImageAsync(urlString: String, callback: ((Uri?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val filename = urlToFilename(urlString)
                val file = File(cacheDir, filename)

                // If already cached, return immediately
                if (file.exists() && file.length() > 0) {
                    callback?.invoke(Uri.fromFile(file))
                    return@launch
                }

                // Download and cache
                val request = Request.Builder()
                    .url(urlString)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    callback?.invoke(null)
                    return@launch
                }

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                if (file.exists() && file.length() > 0) {
                    callback?.invoke(Uri.fromFile(file))
                } else {
                    callback?.invoke(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image", e)
                callback?.invoke(null)
            }
        }
    }

    /**
     * Clean up old images (older than 30 days)
     */
    fun cleanupOldImages() {
        try {
            val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * Convert URL to safe filename
     */
    private fun urlToFilename(urlString: String): String {
        val encoded = Base64.encodeToString(
            urlString.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return encoded
            .replace("/", "_")
            .replace("+", "-")
            .replace("=", "")
            .plus(".png")
    }

    companion object {
        private const val TAG = "ImageCache"
    }
}

