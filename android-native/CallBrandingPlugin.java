package io.securenode.nodekit;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor plugin for Call Branding
 */
@CapacitorPlugin(name = "CallBranding")
public class CallBrandingPlugin extends Plugin {
    
    private static final String TAG = "CallBrandingPlugin";
    
    @PluginMethod
    public void initialize(PluginCall call) {
        String apiKey = call.getString("apiKey");
        if (apiKey == null || apiKey.isEmpty()) {
            call.reject("API key is required");
            return;
        }
        
        String apiUrl = call.getString("apiUrl", "https://calls.securenode.io/api");
        
        // Initialize ConnectionService
        SecureNodeConnectionService.initialize(
            getContext(),
            apiKey,
            apiUrl
        );
        
        // Store API key for persistence
        getContext().getSharedPreferences("SecureNodePrefs", 0)
            .edit()
            .putString("api_key", apiKey)
            .putString("api_url", apiUrl)
            .apply();
        
        call.resolve();
    }
    
    @PluginMethod
    public void syncBranding(PluginCall call) {
        // Sync is handled by the SDK automatically
        // This method can trigger a manual sync if needed
        JSObject result = new JSObject();
        result.put("success", true);
        call.resolve(result);
    }
    
    @PluginMethod
    public void testIncomingCall(PluginCall call) {
        String phoneNumber = call.getString("phoneNumber");
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            call.reject("Phone number is required");
            return;
        }
        
        // For testing, we can simulate an incoming call
        // In production, this would come from the system
        Log.d(TAG, "Test incoming call: " + phoneNumber);
        
        // Note: Actual call handling is done by ConnectionService
        // This is just for testing/development
        call.resolve();
    }
}

