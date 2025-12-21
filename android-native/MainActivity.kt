package io.securenode.nodekit

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.getcapacitor.BridgeActivity

/**
 * Main Activity - initializes SecureNode SDK and requests permissions
 */
class MainActivity : BridgeActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SecureNode SDK
        // API key should be retrieved from shared preferences or secure storage
        val apiKey = getApiKeyFromStorage()
        if (apiKey.isNotEmpty()) {
            SecureNodeConnectionService.initialize(
                context = this,
                apiKey = apiKey,
                apiUrl = "https://calls.securenode.io/api"
            )
        }
        
        // Request necessary permissions
        requestCallPermissions()
    }
    
    private fun getApiKeyFromStorage(): String {
        // Get API key from SharedPreferences or secure storage
        val prefs = getSharedPreferences("SecureNodePrefs", MODE_PRIVATE)
        return prefs.getString("api_key", "") ?: ""
    }
    
    fun saveApiKey(apiKey: String) {
        val prefs = getSharedPreferences("SecureNodePrefs", MODE_PRIVATE)
        prefs.edit().putString("api_key", apiKey).apply()
        
        // Re-initialize SDK with new key
        SecureNodeConnectionService.initialize(
            context = this,
            apiKey = apiKey,
            apiUrl = "https://calls.securenode.io/api"
        )
    }
    
    private fun requestCallPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            
            // Phone state permission
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_PHONE_STATE)
            }
            
            // Call phone permission (if needed)
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CALL_PHONE)
            }
            
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    100
                )
            }
            
            // Request default phone app (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val telecomManager = getSystemService(TelecomManager::class.java)
                if (!telecomManager.isDefaultDialerApp) {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    startActivity(intent)
                }
            }
        }
    }
}

