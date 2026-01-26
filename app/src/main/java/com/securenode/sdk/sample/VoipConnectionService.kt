package com.securenode.sdk.app

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle

/**
 * Self-managed ConnectionService demo (VoIP style).
 *
 * This does not handle PSTN calls. It's for in-app VoIP sessions where the app owns the call.
 */
class VoipConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        val remote = request.address?.schemeSpecificPart ?: "Unknown"
        return VoipConnection(remote)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        val remote = request.address?.schemeSpecificPart ?: "Unknown"
        return VoipConnection(remote).apply { startOutgoing() }
    }
}


