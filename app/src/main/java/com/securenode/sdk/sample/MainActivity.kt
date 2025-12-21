package com.securenode.sdk.sample

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.securenode.sdk.ImageCache
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.SecureNodeSDK
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val apiBaseUrl = "https://api.securenode.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiKeyInput = findViewById<EditText>(R.id.apiKey)
        val phoneInput = findViewById<EditText>(R.id.phoneNumber)
        val lookupBtn = findViewById<Button>(R.id.lookup)

        val status = findViewById<TextView>(R.id.status)
        val brandName = findViewById<TextView>(R.id.brandName)
        val callReason = findViewById<TextView>(R.id.callReason)
        val logo = findViewById<ImageView>(R.id.logo)
        val logoMeta = findViewById<TextView>(R.id.logoMeta)

        lookupBtn.setOnClickListener {
            val apiKey = apiKeyInput.text?.toString()?.trim().orEmpty()
            val phone = phoneInput.text?.toString()?.trim().orEmpty()

            status.text = ""
            brandName.text = ""
            callReason.text = ""
            logo.setImageDrawable(null)
            logoMeta.text = ""

            if (apiKey.isBlank()) {
                status.text = "Missing API key"
                return@setOnClickListener
            }
            if (phone.isBlank() || !phone.startsWith("+")) {
                status.text = "Enter a valid E.164 number (starts with +)"
                return@setOnClickListener
            }

            status.text = "Looking up..."

            val sdk = SecureNodeSDK.initialize(
                context = applicationContext,
                config = SecureNodeConfig(apiUrl = apiBaseUrl, apiKey = apiKey)
            )

            sdk.getBranding(phone) { result ->
                runOnUiThread {
                    result.fold(
                        onSuccess = { branding ->
                            if (branding.brandName.isNullOrBlank()) {
                                status.text = "No branding found for $phone"
                                return@fold
                            }
                            status.text = "OK"
                            brandName.text = branding.brandName ?: ""
                            callReason.text = branding.callReason ?: ""

                            val logoUrl = branding.logoUrl
                            if (!logoUrl.isNullOrBlank()) {
                                val cache = ImageCache(applicationContext)
                                cache.loadImageAsync(logoUrl) { uri ->
                                    runOnUiThread {
                                        if (uri != null) {
                                            logo.setImageURI(uri)
                                            logoMeta.text = describeImage(uri)
                                        } else {
                                            logoMeta.text = "Logo: failed to load/cache"
                                        }
                                    }
                                }
                            } else {
                                logoMeta.text = "Logo: none"
                            }
                        },
                        onFailure = { e ->
                            status.text = "Error: ${e.message ?: "unknown"}"
                        }
                    )
                }
            }
        }
    }

    private fun describeImage(uri: Uri): String {
        val file = File(uri.path ?: return "Logo: cached")
        val bytes = if (file.exists()) file.length() else 0L
        val kb = (bytes / 1024.0).roundToInt()
        val (w, h) = getImageDimensions(file)
        val dims = if (w != null && h != null) "${w}×${h}px" else "—"
        return "Logo cached • ${kb}KB • ${dims}"
    }

    private fun getImageDimensions(file: File): Pair<Int?, Int?> {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val w = if (opts.outWidth > 0) opts.outWidth else null
            val h = if (opts.outHeight > 0) opts.outHeight else null
            Pair(w, h)
        } catch (_e: Exception) {
            Pair(null, null)
        }
    }
}


