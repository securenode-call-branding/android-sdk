package io.securenode.nodekit.telecom;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;

/**
 * Requests default dialer role so ConnectionService can intercept PSTN calls.
 *
 * Fail-open: if user declines, app still works for API testing; call interception won't occur.
 */
public final class DialerRoleManager {
  private DialerRoleManager() {}

  public static void ensureDefaultDialer(Activity activity) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        TelecomManager telecomManager = activity.getSystemService(TelecomManager.class);
        if (telecomManager != null && !activity.getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
          Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
          intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.getPackageName());
          activity.startActivity(intent);
        }
      }
    } catch (Throwable ignored) {
      // fail-open
    }
  }
}





