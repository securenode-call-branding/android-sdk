package io.securenode.nodekit;

import android.content.Context;
import android.util.Log;

import com.securenode.sdk.SecureNodeConfig;
import com.securenode.sdk.SecureNodeSDK;

/**
 * Bootstraps SecureNodeSDK safely.
 *
 * Goals:
 * - Never crash the app/telecom flow if SDK init fails
 * - Prefer keystore-stored API key (SDK handles storage)
 */
public final class SdkBootstrap {
  private static final String TAG = "SdkBootstrap";

  private SdkBootstrap() {}

  public static void initializeIfPossible(Context context, String apiUrl) {
    try {
      // Empty apiKey is OK if it was previously stored by the SDK in the Keystore.
      SecureNodeConfig config = new SecureNodeConfig(apiUrl, "");
      SecureNodeSDK.Companion.initialize(context.getApplicationContext(), config);
    } catch (Throwable t) {
      Log.e(TAG, "SecureNodeSDK init skipped (will fail-open)", t);
    }
  }

  public static SecureNodeSDK getInstanceOrNull() {
    try {
      return SecureNodeSDK.Companion.getInstance();
    } catch (Throwable t) {
      return null;
    }
  }
}





