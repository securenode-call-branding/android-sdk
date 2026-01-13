# SecureNode Branding SDK (Android)

Lightweight, production-oriented library for PSTN call branding.

## What it does

- Incoming call → E.164 → local cache lookup → network fallback → cache update
- Queues a billing/telemetry event for every observed call
- Background sync (WorkManager)
- Optional Android call-screening integration (Android 14 supported)

## Public API

- SecureNodeBranding.initialize(...)
- SecureNodeBranding.resolveBranding(e164, surface)

## Portal Configurator (drop-in form)

This repo includes a dependency-free configurator page you can embed in your developer portal to
generate copy/paste integration snippets:

- `portal-configurator/index.html`

## Call Screening

Add to host APP manifest:

```xml
<service
    android:name="io.securenode.branding.call.SecureNodeCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```

## Doze-mode resilience

- One-time event uploads use **expedited WorkManager** (falls back if quota is exceeded).
- Periodic uploads run at Android's minimum interval (15 minutes).
- Network + battery-not-low constraints are applied.

## Dual‑SIM support

- CallScreeningService attaches `phone_account_id` where available.
- If your host app knows `subscriptionId` / `simSlotIndex`, pass them via `SecureNodeBranding.onIncomingCallObserved(...)`.

## Debug Console (server gated)

The SDK includes a hidden debug console Activity with **no launcher icon**.

### Enable from server (recommended)

Return `config.debug_ui` in the response from:
`GET /api/mobile/branding/sync`

Example:

```json
{
  "config": {
    "debug_ui": {
      "enabled": true,
      "expires_at": "2026-01-10T23:59:59Z",
      "allow_export": true
    }
  }
}
```

The user/engineer can then unlock locally by tapping your About row **7 times**:

```kotlin
if (SecureNodeBranding.onAboutTapped(context)) {
  SecureNodeBranding.openDebugConsole(context)
}
```

## Remote Debug Package Upload (Manager initiated)

Your backend can request a **remote debug package upload** for a specific device.
When received via sync config, the SDK will automatically upload a signed payload
containing SDK state + logs.

```json
{
  "config": {
    "debug_ui": {
      "enabled": true,
      "request_upload": true,
      "expires_at": "2026-01-10T23:59:59Z",
      "allow_export": true
    }
  }
}
```
