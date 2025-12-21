package io.securenode.nodekit.telecom;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.TelecomManager;

/**
 * Safe fallback connection that never throws and always allows the call to proceed.
 */
public class FailOpenConnection extends Connection {
  public FailOpenConnection(ConnectionRequest request) {
    try {
      setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
      setAudioModeIsVoip(true);

      Uri address = request != null ? request.getAddress() : null;
      if (address != null) {
        setCallerDisplayName(address.getSchemeSpecificPart(), TelecomManager.PRESENTATION_ALLOWED);
      }

      setActive();
    } catch (Throwable ignored) {
      // Never throw from telecom code path
      try {
        setActive();
      } catch (Throwable ignored2) {}
    }
  }

  @Override
  public void onAnswer() {
    try {
      setActive();
    } catch (Throwable ignored) {}
  }

  @Override
  public void onReject() {
    try {
      destroy();
    } catch (Throwable ignored) {}
  }

  @Override
  public void onDisconnect() {
    try {
      destroy();
    } catch (Throwable ignored) {}
  }
}





