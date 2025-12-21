package io.securenode.nodekit.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.securenode.sdk.SecureNodeConfig;
import com.securenode.sdk.SecureNodeSDK;

import io.securenode.nodekit.SdkBootstrap;
import io.securenode.nodekit.sync.SyncScheduler;

/**
 * Capacitor plugin: CallBranding
 *
 * - initialize(apiKey, apiUrl?)
 * - syncBranding()
 * - testIncomingCall(phoneNumber) [dev helper; real calls come from Telecom]
 */
@CapacitorPlugin(name = "CallBranding")
public class CallBrandingPlugin extends Plugin {

  @PluginMethod
  public void initialize(PluginCall call) {
    String apiKey = call.getString("apiKey");
    if (apiKey == null || apiKey.isEmpty()) {
      call.reject("API key is required");
      return;
    }

    String apiUrl = call.getString("apiUrl", "https://calls.securenode.io/api");

    try {
      SecureNodeConfig config = new SecureNodeConfig(apiUrl, apiKey);
      SecureNodeSDK.Companion.initialize(getContext().getApplicationContext(), config);

      // Persist apiUrl (not secret). API key is stored securely by the SDK in Keystore.
      SharedPreferences prefs = getContext().getSharedPreferences("SecureNodePrefs", Context.MODE_PRIVATE);
      prefs.edit().putString("api_url", apiUrl).apply();

      // Ensure daily sync is scheduled (self-heal)
      SyncScheduler.scheduleDailySync(getContext());

      JSObject result = new JSObject();
      result.put("success", true);
      call.resolve(result);
    } catch (Throwable t) {
      call.reject("Failed to initialize SecureNodeSDK", t);
    }
  }

  @PluginMethod
  public void syncBranding(PluginCall call) {
    SecureNodeSDK sdk = SdkBootstrap.getInstanceOrNull();
    if (sdk == null) {
      JSObject result = new JSObject();
      result.put("success", false);
      result.put("error", "SDK not initialized (set API key first)");
      call.resolve(result);
      return;
    }

    sdk.syncBranding(null, result -> {
      JSObject out = new JSObject();
      if (result.isSuccess()) {
        out.put("success", true);
        out.put("count", result.getOrNull().getBranding().size());
      } else {
        out.put("success", false);
        out.put("error", String.valueOf(result.exceptionOrNull()));
      }
      call.resolve(out);
      return null;
    });
  }

  @PluginMethod
  public void testIncomingCall(PluginCall call) {
    // Real PSTN interception happens via ConnectionService after default-dialer is granted.
    // This method is intentionally a no-op for production safety.
    call.resolve();
  }
}





