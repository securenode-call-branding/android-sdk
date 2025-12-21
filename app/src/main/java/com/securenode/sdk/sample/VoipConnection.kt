package com.securenode.sdk.sample

import android.net.Uri
import android.telecom.Connection
import android.telecom.DisconnectCause

class VoipConnection(
    private val remote: String
) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        address = Uri.fromParts("tel", remote, null)
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


