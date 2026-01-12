package io.securenode.branding.call

import android.telecom.Call
import android.telecom.CallScreeningService
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.telemetry.Logger
import io.securenode.branding.util.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android 14-compatible CallScreeningService.
 * Branding-only: NEVER blocks or silences calls.
 *
 * Host app must declare this service and user must enable the host app as the call screening app.
 */
class SecureNodeCallScreeningService : CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Always allow call (branding only).
        respondToCall(callDetails, CallResponse.Builder().build())

        val raw = callDetails.handle?.schemeSpecificPart
            val phoneAccountId = callDetails.accountHandle?.id
        val e164 = PhoneNumberUtil.normalizeToE164(raw) ?: return

        scope.launch {
            try {
                SecureNodeBranding.onIncomingCallObserved(e164, surface = "call_screening", phoneAccountId = phoneAccountId)
            } catch (t: Throwable) {
                Logger.w("Branding resolve failed (call_screening)", t)
            }
        }
    }
}
