# SecureNode Mobile SDK — Endpoint Contract (Authoritative)

This document defines the **authoritative runtime contract** for SecureNode Mobile SDKs  
(**iOS and Android**).

It specifies:

- which endpoints exist
- when each endpoint is called
- what data must be cached locally
- how identity is applied at ring-time
- how usage is reported safely for billing

> **Hard rule**  
> Caller identity must work **offline at ring-time**.  
> Network calls must **never** be required to render the OS call UI.

Platform-specific implementation details live in:
- `/docs/sdk/ios.md`
- `/docs/sdk/android.md`

---

## 1. Purpose & Non-Goals

### Purpose
- Guarantee **instant caller identity resolution** (<10ms lookup)
- Preserve **native OS dialler ownership**
- Enable **accurate, auditable billing**
- Provide a **single integration contract** across platforms

### Non-Goals
This document does **not**:
- Define UI rendering
- Replace OpenAPI specifications
- Describe permission request UX
- Describe VoIP or dialler replacement flows

---

## 2. Base URL & Authentication

### Base URL
```
https://api.securenode.io
```

### Authentication
Every request **must** include:
```
X-API-Key: <customer-issued-api-key>
```

---

## 3. Core Data Model

### Branding Record (Authoritative)

Required fields:
- `phone_number_e164`
- `brand_name`
- `brand_id`
- `updated_at`
- `is_active`

Optional fields:
- `logo_url`
- `call_reason`

**Storage rules**
- Records must be stored locally
- Ring-time lookups must be local
- Network calls must never be required to display identity

---

## 4. Local Storage & Atomicity

- Partial writes are never allowed
- Syncs must be atomic
- SDK must always have a readable dataset

---

## 5. Branding Sync

### `GET /api/mobile/branding/sync`

Primary endpoint for branding data.

Supports:
- Full sync
- Incremental sync via `since` cursor

Inactive records must be purged locally.

---

## 6. Lookup Endpoint (Fallback Only)

### `GET /api/mobile/branding/lookup`

Used **only** on local cache miss.

> Must never be called synchronously during ring-time.

---

## 7. Usage & Billing Events

### `POST /api/mobile/branding/imprint`

Reports successful identity display.

Rules:
- Best-effort
- Asynchronous
- Idempotent via `event_key`
- Must never block call UI

---

## 8. Device Registration

### `POST /api/mobile/device/register`
- Called once per install
- Registers platform and device_id

### `POST /api/mobile/device/update`
- Reports capability and version changes

---

## 9. Debug & Telemetry Gate

### `POST /api/mobile/debug/upload`

Only allowed when explicitly enabled by server config.

---

## 10. Runtime Flow Summary

**App Launch**
1. Device register/update
2. Branding sync
3. Cache assets

**Incoming Call**
1. Local lookup only
2. Display native call UI
3. Async imprint event

**Background**
- Incremental sync
- Asset cleanup

---

## 11. Error Handling

| Scenario | Behaviour |
|--------|-----------|
| Network failure | Fail silently |
| Event timeout | Retry with same key |
| Auth failure | Disable branding |

---

## 12. Absolute Rules

- Native dialler remains in control
- No audio interception
- No ring-time network dependency
- Billing events must be idempotent

---

## 13. Related Docs

- `openapi.yaml`
- `BRANDING_BILLING_SYSTEM.md`
- `MOBILE_SDK_TESTING_GUIDE.md`
- `/docs/sdk/ios.md`
- `/docs/sdk/android.md`

---

**End of document**
