package io.securenode.nodekit;

import android.content.SharedPreferences;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import io.securenode.nodekit.plugins.CallBrandingPlugin;
import io.securenode.nodekit.sync.SyncScheduler;
import io.securenode.nodekit.telecom.DialerRoleManager;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CallBrandingPlugin.class);
    super.onCreate(savedInstanceState);

    // Ensure daily sync is scheduled (self-heal)
    SyncScheduler.scheduleDailySync(this);

    // Prompt to become default dialer (required for ConnectionService call interception)
    DialerRoleManager.ensureDefaultDialer(this);

    // Best-effort: initialize SDK using stored apiUrl + keystore-backed key (if previously set)
    SharedPreferences prefs = getSharedPreferences("SecureNodePrefs", MODE_PRIVATE);
    String apiUrl = prefs.getString("api_url", "https://calls.securenode.io/api");
    SdkBootstrap.initializeIfPossible(this, apiUrl);
  }
}
