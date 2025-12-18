# SecureNode Android SDK

Android SDK for SecureNode **Active Caller Identity** and Call Branding.

This SDK is designed for **client apps** that want users to recognise legitimate business or partner calls by:

- syncing branding data incrementally
- caching identity locally for instant ring-time lookup
- surfacing a **high‑priority “Verified by SecureNode”** call notification using Android’s native call surfaces

The SDK is **offline-safe**, **non-dialer by default**, and built to scale to **multi‑million device deployments**.

---

## Features

- ✅ **Sample ring-time branding surface (non‑dialer)**  
  CallScreeningService sample shows a CALL‑category notification (no default dialer requirement)

- ✅ **Local database caching**  
  Room database for sub‑10ms branding lookups at ring‑time

- ✅ **Local image caching**  
  Logos served from `https://assets.securenode.io` and cached for offline use

- ✅ **Incremental sync**  
  Delta-based sync using `since` cursor (no polling storms)

- ✅ **Secure API key storage**  
  Android Keystore backed encryption

- ✅ **Offline-first / call-safe**  
  Incoming calls never trigger network requests

- ✅ **Thread-safe**  
  Safe for multi-threaded call handling

- 🧩 **Optional VoIP / self-managed calling (future / gated)**  
  For in‑app VoIP use cases only (carrier PSTN not replaced)

---

## Installation

This SDK currently ships as **source + sample app** (not yet published to Maven Central).

Recommended integration options:
- Add the SDK source as a module, or
- Copy the SDK package into your app and keep it updated

(Artifact coordinates below are reserved for future publishing.)

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.securenode:android-sdk:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation "com.securenode:android-sdk:1.0.0"
}
```

---

## Quick Start

### 1. Create an API key

- Portal: https://portal.securenode.io  
- Settings → API Keys  
- Generate a key (QR or copy)

---

### 2. Initialise the SDK

```kotlin
import com.securenode.sdk.SecureNodeSDK
import com.securenode.sdk.SecureNodeConfig

val secureNode = SecureNodeSDK.initialize(
    context = applicationContext,
    config = SecureNodeConfig(
        apiUrl = "https://verify.securenode.io",
        apiKey = "your-api-key-here",
        campaignId = "campaign_123" // optional
    )
)
```

> Branding logos are always served from:
> ```
> https://assets.securenode.io
> ```

---

### 3. Sync branding data (recommended pattern)

```kotlin
// Initial sync (after onboarding)
secureNode.syncBranding { result ->
    // Branding cached locally
}

// Incremental sync (throttled, background-safe)
secureNode.syncBranding(since = lastSyncTimestamp) { result ->
    // Only updates since last sync
}
```

**Important behaviour**
- Sync is host‑driven (call it from your app lifecycle or scheduler)
- Use `synced_at` as your `since` cursor for incremental updates
- Never sync during an incoming call

---

## Recommended Integration: CallScreeningService (non‑dialer)

To surface branding at ring‑time **without becoming a dialer app**, integrate with `CallScreeningService`.

This allows:
- normal app installation (no default dialer requirement)
- high‑priority CALL notifications with brand name + call reason
- fail‑open behaviour if branding is unavailable

Reference implementation:
- `android-sdk/app/src/main/java/com/securenode/sdk/sample/SecureNodeScreeningService.kt`

### User setup note

On many devices, users must enable your app as the caller ID / spam app:

```
Settings → Apps → Default apps → Caller ID & spam app → select your app
```

---

## API Contract (mobile)

Base URL: `https://verify.securenode.io`  
(SDK accepts either root or `/api` and tries both.)

### Sync

```
GET /mobile/branding/sync?since=<timestamp>
```

Minimal fields used by the SDK:

```json
{
  "branding": [
    {
      "phone_number_e164": "+61412345678",
      "brand_name": "SecureNode",
      "logo_url": "https://assets.securenode.io/logo/secure-node.png",
      "call_reason": "Account enquiry",
      "updated_at": "2026-01-27T03:12:44Z"
    }
  ],
  "synced_at": "2026-01-27T03:15:00Z",
  "config": {
    "branding_enabled": true,
    "voip_dialer_enabled": false,
    "mode": "live"
  }
}
```

---

### BrandingInfo

```kotlin
data class BrandingInfo(
    val phoneNumberE164: String,
    val brandName: String?,
    val logoUrl: String?,
    val callReason: String?,
    val updatedAt: String
)
```

---

## Reporting & outcomes (optional)

The SDK does **not** derive call outcomes. If your app knows them, report via `/mobile/branding/event` using:

- `recordCallSeen(...)` → baseline “seen” events (returns `call_id`)
- `recordMissedCall(...)` → missed outcome (returns `call_id`)
- `recordCallReturned(...)` → follow‑up attribution (pass `call_id`)

Convenience fields are mapped into event `meta`:
`call_event_id`, `caller_number_e164`, `destination_number_e164`, `observed_at_utc`, `branding_applied`,
`branding_profile_id`, `identity_type`, `ring_duration_seconds`, `call_duration_seconds`, `call_outcome`,
`return_call_detected`, `return_call_latency_seconds`.

Example:

```kotlin
val callId = secureNode.recordCallSeen(
    phoneNumberE164 = "+61412345678",
    brandingDisplayed = true,
    callOutcome = "ANSWERED",
    ringDurationSeconds = 12,
    callDurationSeconds = 180,
    callerNumberE164 = "+61412345678",
    destinationNumberE164 = "+61234567890"
)
```

Notes:
- `branding_applied` defaults to `brandingDisplayed` if not provided.
- Use `call_outcome` values your exports expect (e.g. `ANSWERED`, `MISSED`, `REJECTED`).
- Imprints (`POST /mobile/branding/imprint`) are sent when branding is applied in the self‑managed Connection path.
  The CallScreeningService sample only shows a notification.

---

## Security

- API keys stored using Android Keystore
- HTTPS enforced for all requests
- No sensitive data logged
- Local cache persists across restarts

---

## Limitations (intentional)

- This SDK does **not** replace the system call UI.
- Becoming the default dialer is out of scope for standard integrations.
- `CallScreeningService` behaviour varies by Android version and OEM.
  Notifications are the most consistent surface.

---

## Requirements

- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9+
- **Room**: 2.6.0+
- **OkHttp**: 4.12.0+

---

## License

GPL-3.0

---

## Support

- Documentation: https://verify.securenode.io/sdk
- Issues: https://github.com/securenode-call-branding/android-sdk/issues
