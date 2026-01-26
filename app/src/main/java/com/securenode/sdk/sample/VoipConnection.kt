package com.securenode.sdk.app

import android.net.Uri
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager

class VoipConnection(
    private val remote: String
) : Connection() {

    init {
        setConnectionProperties(PROPERTY_SELF_MANAGED)
        setAddress(Uri.fromParts("tel", remote, null), TelecomManager.PRESENTATION_ALLOWED)
        setInitializing()
        setRinging()
    }

    override fun onAnswer() {
        setActive()
        VoipTransportProvider.transport.acceptIncoming(remote)
    }

    override fun onReject() {
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        VoipTransportProvider.transport.end(remote)
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    fun startOutgoing() {
        setDialing()
        VoipTransportProvider.transport.startOutgoing(remote)
        // In a real integration, move to ACTIVE when media/transport is connected.
        setActive()
    }
}


