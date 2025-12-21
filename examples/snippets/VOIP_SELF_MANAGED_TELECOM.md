# Android Option B (Optional): VoIP “Dialer Style” (Self-managed ConnectionService)

Use this when your client app **owns the call** (VoIP sessions like WhatsApp) and you want to surface it via Android Telecom call UI/lifecycle.

This is **not for carrier PSTN calls**.

## What you copy/paste

- A self-managed `ConnectionService`
- A self-managed `PhoneAccount`
- Hook points to plug in your own VoIP transport (WebRTC/SIP/etc)

## 1) AndroidManifest.xml (service + permission)

```xml
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

<service
    android:name=".VoipConnectionService"
    android:exported="true"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

## 2) Telecom wiring (copy these files)

Copy from the sample:

- `app/src/main/java/com/securenode/sdk/sample/VoipPhoneAccount.kt`
- `app/src/main/java/com/securenode/sdk/sample/VoipConnectionService.kt`
- `app/src/main/java/com/securenode/sdk/sample/VoipConnection.kt`

## 3) Plug in your VoIP transport (client responsibility)

Replace the no-op transport with your stack:

Copy from:
- `app/src/main/java/com/securenode/sdk/sample/VoipTransport.kt`

## Notes

- Self-managed calling behavior varies by OEM; test on your target devices.
- Gate this option behind your server config if needed (the portal supports a per-company toggle).


