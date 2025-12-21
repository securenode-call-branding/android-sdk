package com.securenode.sdk.sample

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.securenode.sdk.ImageCache
import com.securenode.sdk.database.BrandingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class SecureNodeScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val incoming = handle?.schemeSpecificPart ?: ""

        // Always allow the call – we only display branding as a notification overlay.
        respondToCall(callDetails, CallResponse.Builder().build())

        if (incoming.isBlank()) return
        // If the account is capped/disabled, pass-through (do not display branding).
        if (!SampleConfigStore.isBrandingEnabled(applicationContext)) return
        // Normalize basic: ensure it starts with +
        val e164 = if (incoming.startsWith("+")) incoming else "+$incoming"

        val branding = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(150) {
                BrandingDatabase.getDatabase(applicationContext).brandingDao().getBranding(e164)
            }
        } ?: return

        val brandName = branding.brandName ?: return
        showBrandingNotification(
            number = e164,
            brandName = brandName,
            callReason = branding.callReason,
            logoUrl = branding.logoUrl
        )
    }

    private fun showBrandingNotification(
        number: String,
        brandName: String,
        callReason: String?,
        logoUrl: String?
    ) {
        ensureChannel()

        val mode = SampleConfigStore.getMode(applicationContext)
        val isTesting = mode.equals("testing", ignoreCase = true) || mode.equals("demo", ignoreCase = true)

        val primary = if (!callReason.isNullOrBlank()) callReason else "Verified business call"
        val text = if (isTesting) "TESTING • $primary • $number" else "$primary • $number"

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (isTesting) "TESTING • $brandName" else brandName)
            .setContentText(text)
            .setSubText(if (isTesting) "Testing • Verified by SecureNode" else "Verified by SecureNode")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Best-effort: attach cached logo as large icon (only if already cached).
        if (!logoUrl.isNullOrBlank()) {
            val cache = ImageCache(applicationContext)
            val cached = cache.getImage(logoUrl)
            if (cached?.path != null) {
                val file = File(cached.path!!)
                if (file.exists()) {
                    try {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) {
                            builder.setLargeIcon(bmp)
                        }
                    } catch (_e: Exception) {
                        // ignore
                    }
                }
            }
        }

        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "securenode_calls"
    }
}


