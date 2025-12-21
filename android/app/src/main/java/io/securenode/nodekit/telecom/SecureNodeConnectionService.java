package io.securenode.nodekit.telecom;

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import com.securenode.sdk.SecureNodeSDK;

import io.securenode.nodekit.SdkBootstrap;

/**
 * Android Telecom integration.
 *
 * IMPORTANT:
 * - To intercept PSTN calls, the app must be the default dialer on Android 10+
 * - This must be fail-open: if anything fails, return a normal Connection
 */
public class SecureNodeConnectionService extends ConnectionService {
  private static final String TAG = "SNConnectionService";

  @Override
  public Connection onCreateIncomingConnection(
      PhoneAccountHandle connectionManagerPhoneAccount,
      ConnectionRequest request
  ) {
    try {
      String phoneNumber = request != null && request.getAddress() != null
          ? request.getAddress().getSchemeSpecificPart()
          : null;

      SecureNodeSDK sdk = SdkBootstrap.getInstanceOrNull();
      if (sdk != null) {
        return sdk.createBrandedConnection(phoneNumber, request);
      }
    } catch (Throwable t) {
      Log.e(TAG, "Failed to create branded connection (fail-open)", t);
    }

    return new FailOpenConnection(request);
  }

  @Override
  public Connection onCreateOutgoingConnection(
      PhoneAccountHandle connectionManagerPhoneAccount,
      ConnectionRequest request
  ) {
    // Outgoing calls: we do not brand these in the sample app. Fail-open.
    return new FailOpenConnection(request);
  }
}





