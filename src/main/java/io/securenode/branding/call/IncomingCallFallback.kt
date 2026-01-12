package io.securenode.branding.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.telemetry.Logger
import io.securenode.branding.util.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Non-call-screening fallback (best-effort).
 *
 * NOTE: On many Android 10+ devices, the OS will not provide incoming numbers to non-default dialers
 * unless Call Screening is enabled. Use SecureNodeBranding.onIncomingCallObserved(...) when possible.
 */
internal class IncomingCallFallback(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Logger.w("READ_PHONE_STATE not granted; fallback disabled")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tm.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_RINGING) {
                            Logger.d("CALL_STATE_RINGING observed (fallback; number often unavailable)")
                        }
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            tm.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        val e164 = PhoneNumberUtil.normalizeToE164(incomingNumber)
                        if (e164 != null) {
                            scope.launch {
                                try { SecureNodeBranding.resolveBranding(e164, surface = "telephony_fallback") }
                                catch (t: Throwable) { Logger.w("Fallback branding failed", t) }
                            }
                        } else {
                            Logger.d("RINGING observed but incoming number not available")
                        }
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }
}
