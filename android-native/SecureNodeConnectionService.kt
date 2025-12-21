package io.securenode.nodekit

import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.SecureNodeConfig
import com.securenode.sdk.network.BrandingInfo

/**
 * ConnectionService for handling incoming calls with SecureNode branding
 */
class SecureNodeConnectionService : ConnectionService() {
    
    companion object {
        private const val TAG = "SecureNodeConnection"
        private var sdk: SecureNodeSDK? = null
        
        fun initialize(context: Context, apiKey: String, apiUrl: String = "https://calls.securenode.io/api") {
            val config = SecureNodeConfig(
                apiUrl = apiUrl,
                apiKey = apiKey
            )
            sdk = SecureNodeSDK.initialize(context.applicationContext, config)
            
            // Initial sync on startup
            syncBrandingData()
        }
        
        private fun syncBrandingData() {
            sdk?.syncBranding { result ->
                result.onSuccess { response ->
                    Log.d(TAG, "Synced ${response.branding.size} branding records")
                }.onFailure { error ->
                    Log.e(TAG, "Sync failed", error)
                }
            }
        }
    }
    
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val phoneNumber = request?.address?.schemeSpecificPart
        
        return if (sdk != null && phoneNumber != null) {
            // Use SDK to create branded connection
            sdk!!.createBrandedConnection(phoneNumber, request)
        } else {
            // Fallback to basic connection
            createBasicConnection(phoneNumber, request)
        }
    }
    
    private fun createBasicConnection(
        phoneNumber: String?,
        request: ConnectionRequest?
    ): Connection {
        return object : Connection() {
            init {
                setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
                setAudioModeIsVoip(true)
                
                phoneNumber?.let { number ->
                    setCallerDisplayName(number, TelecomManager.PRESENTATION_ALLOWED)
                }
                
                setActive()
            }
            
            override fun onAnswer() {
                super.onAnswer()
                setActive()
            }
            
            override fun onReject() {
                super.onReject()
                destroy()
            }
            
            override fun onDisconnect() {
                super.onDisconnect()
                destroy()
            }
        }
    }
    
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        // Outgoing calls - not needed for branding test
        return createBasicConnection(request?.address?.schemeSpecificPart, request)
    }
}

