package com.securenode.sdk.app

/**
 * Integration point for client VoIP stacks.
 *
 * This SDK/sample does NOT ship a transport (WebRTC/SIP/etc).
 * Clients wire their own VoIP stack here and use Telecom only as the call UI / lifecycle surface.
 */
interface VoipTransport {
    fun startOutgoing(remote: String)
    fun acceptIncoming(remote: String)
    fun end(remote: String)
}

object VoipTransportProvider {
    /**
     * Replace this with a real transport implementation in client apps.
     */
    @Volatile
    var transport: VoipTransport = object : VoipTransport {
        override fun startOutgoing(remote: String) { /* no-op */ }
        override fun acceptIncoming(remote: String) { /* no-op */ }
        override fun end(remote: String) { /* no-op */ }
    }
}


