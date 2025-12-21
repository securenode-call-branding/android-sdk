# Android Option A (Recommended): PSTN Branding Assist (CallScreeningService)

Use this when you want to improve answer rates for **normal carrier calls (PSTN)** without becoming the default dialer.

## What you copy/paste

- A `CallScreeningService` that **always allows** calls and shows a high-priority **Verified** notification using cached branding.
- A `WorkManager` sync to keep the local cache fresh.

## 1) AndroidManifest.xml (service + permissions)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<service
    android:name=".SecureNodeScreeningService"
    android:exported="true"
    android:permission="android.permission.BIND_SCREENING_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

## 2) CallScreeningService (ring-time display)

Copy from:

- `app/src/main/java/com/securenode/sdk/sample/SecureNodeScreeningService.kt`

Key behavior:
- **fail-open**: `respondToCall(... allow ...)` always
- show notification only if local cache has branding

## 3) Background sync (cache refresh)

Copy from:

- `app/src/main/java/com/securenode/sdk/sample/SdkSyncWorker.kt`
- `app/src/main/java/com/securenode/sdk/sample/MainActivity.kt` (WorkManager scheduling)

## Notes

- Users may need to enable your app as the **Caller ID & spam app** in Android settings (varies by OEM).
- This does **not** replace the system in-call UI; it overlays branding via notifications.


