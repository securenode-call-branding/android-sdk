{
  "title": "Mobile SDK Endpoint Contract",
  "path": "guides/mobile-sdk-endpoints"
}


# Mobile SDK Endpoint Contract

## Purpose & Non-Goals
## Base URL & Authentication
## Data Model (Branding Record)
## Local Storage & Atomicity Rules
## Sync Endpoint
## Lookup Endpoint (Fallback)
## Usage & Billing Events
## Device Registration & Updates
## Debug & Telemetry Gates
## SDK Runtime Flow (Boot / Ring-Time / Background)
## Error Handling & Retry Semantics
## What MUST Never Block Call UI
# Mobile SDK Endpoint Contract

This document defines the **authoritative runtime contract** for SecureNode Mobile SDKs (iOS & Android).

It describes **what endpoints exist**, **when they are called**, **what must be cached locally**, and **how billing / usage events are triggered safely** without ever blocking the operating system call UI.

> This document is SDK-agnostic. Platform-specific details live in:
>
> * `/docs/sdk/ios.mdx`
> * `/docs/sdk/android.mdx`

---

## 1. Purpose & Non‑Goals

### Purpose

* Guarantee **instant caller identity resolution** at ring-time (offline-first)
* Ensure **correct, auditable billing** based on real user-visible displays
* Provide a **single integration contract** for all mobile SDK implementations

### Non‑Goals

* This document does **not** describe UI rendering
* This document does **not** replace `openapi.yaml`
* This document does **not** contain platform permission walkthroughs

---

## 2. Base URL & Authentication

### Base URL

All Mobile SDK traffic targets the SecureNode Mobile API:

```
https://edge.securenode.io
```

> All documentation, SDKs, and OpenAPI definitions MUST reference the same base URL.

### Authentication

Every request must include:

```
X-API-Key: <customer-issued-api-key>
```

The API key uniquely identifies the customer account, billing context, and branding dataset.

---

## 3. Core Data Model

### Branding Record (Authoritative)

Each branding record represents **one active branded caller identity**.

Minimum required fields:

* `phone_number_e164` – lookup key (string)
* `brand_name` – display label (string)
* `brand_id` – internal identifier (string)
* `updated_at` – last update timestamp (ISO8601)

Optional fields:

* `logo_url`
* `call_reason`

### SDK Storage Rules

* Records MUST be stored locally
* Lookups MUST be local at ring-time
* Network calls MUST NOT be required to display identity

> iOS SDKs MUST use atomic snapshot replacement. Android SDKs MUST use an equivalent transactional mechanism.

---

## 4. Local Storage & Atomicity

### Requirements

* Partial writes are **never allowed**
* A sync either completes fully or not at all
* The SDK must always have a valid, readable dataset

### Recommended Pattern

* Write new dataset to a temporary table / file
* Validate record integrity
* Swap pointer / transaction commit
* Clean up obsolete assets (e.g. logos)

---

## 5. Sync Endpoint (Primary)

### `GET /api/mobile/branding/sync`

Fetches the **authoritative active branding dataset** for the API key.

#### Query Parameters

| Name        | Required | Description                       |
| ----------- | -------- | --------------------------------- |
| `since`     | No       | Incremental sync cursor (ISO8601) |
| `device_id` | No       | Device attribution                |

#### Response

* `branding[]` – active branding records
* `synced_at` – server timestamp
* `config` – runtime flags & limits

Important config fields:

* `branding_enabled`
* `account_status`
* `cap_this_month`
* `used_this_month`
* `included_imprints_monthly`
* `debug_ui`

#### SDK Rules

* Without `since`: treat response as **full authoritative set**
* With `since`: treat response as **incremental updates only**
* Removed / inactive records MUST be purged locally

---

## 6. Lookup Endpoint (Fallback Only)

### `GET /api/mobile/branding/lookup`

Used only when a local cache miss occurs.

#### Query Parameters

| Name        | Required |
| ----------- | -------- |
| `e164`      | Yes      |
| `device_id` | No       |

#### Critical Rule

> This endpoint MUST NOT be called synchronously during ring-time.

Results may be cached locally but SHOULD be confirmed by the next sync.

---

## 7. Usage & Billing Events (Authoritative)

### `POST /api/mobile/branding/event`

This endpoint is optional telemetry for:

* Usage analytics
* Reputation telemetry

#### Required Fields

* `phone_number_e164`
* `outcome`
* `event_key` (idempotency)

Common `outcome` values:

* `displayed` (billable if counted)
* `no_match`
* `branding_disabled`
* `error`

#### Response Fields

* `counted` (boolean)
* `reason` (if not counted)

#### Billing Rules

* SDKs MUST only send `displayed` when identity was actually shown
* Server response is authoritative for billing attribution
* Retries MUST reuse the same `event_key`

> Billing is derived from successful sync delivery (`GET /api/mobile/branding/sync`). The event endpoint is optional.

---

## 7b. Metrics & Counters (Server-Side Mapping)

Use this table to map reporting terms to the exact endpoint signal:

| Metric | Signal | Endpoint | Notes |
| ------ | ------ | -------- | ----- |
| Branding efforts (delivery) | Successful sync delivery | `GET /api/mobile/branding/sync` | Authoritative for billing. |
| Sync response (ack) | Client acknowledgment | `POST /api/mobile/branding/sync` | Optional legacy acknowledgment. |
| Imprints (counted) | `counted=true` | `POST /api/mobile/branding/event` | Counted only when eligible. |
| Branding failures | `counted=false` + `reason` | `POST /api/mobile/branding/event` | Reasons include `no_match`, `disabled`, `cap_reached`, `account_suspended`, `owner_suspended`, `owner_unresolved`, `analytics_only`, `pending_enrichment`, `event_dropped`. |
| Number matched | `matched=true` | `GET /api/mobile/branding/lookup?format=public` | Network fallback only; not for ring-time. |
| Calls not matched / ignored | `matched=false` or `counted=false` | `GET /api/mobile/branding/lookup?format=public` or `POST /api/mobile/branding/event` | Use events to track device-side outcomes. |

---

## 8. Device Registration & Updates

### `POST /api/mobile/device/register`

Called once per install.

Required:

* `device_id`
* `platform`

### `POST /api/mobile/device/update`

Used to report:

* OS / app / SDK versions
* Capability changes
* Last-seen timestamp

SDKs SHOULD call this:

* On app launch
* After permission changes
* After successful sync

---

## 9. Debug & Telemetry Gate

### `POST /api/mobile/debug/upload`

Debug uploads are **server-authorised only**.

SDKs must check:

```
config.debug_ui.enabled
config.debug_ui.request_upload
```

Uploads must be:

* One-time per nonce
* Explicitly gated

---

## 10. Runtime Flow Summary

### App Launch

1. Register device (if needed)
2. Update device state
3. Sync branding dataset
4. Cache logos

### Incoming Call (Ring-Time)

1. Normalise number
2. Local lookup ONLY
3. Display identity if found
4. Fire-and-forget usage event

### Background Maintenance

* Incremental sync
* Asset cleanup
* Throttled directory reloads (iOS)

---

## 11. Error Handling & Retry

| Scenario        | Behaviour                   |
| --------------- | --------------------------- |
| Network failure | Fail silently               |
| Event timeout   | Retry with same `event_key` |
| Rate limit      | Backoff                     |
| Auth failure    | Disable branding display    |

---

## 12. Absolute Rules (Non‑Negotiable)

* Call UI MUST NEVER block
* Caller identity MUST work offline
* Billing events MUST be idempotent
* Sync data is authoritative
* SDKs MUST obey server gating

---

## 13. Related Documents

* `openapi.yaml`
* `BRANDING_BILLING_SYSTEM.md`
* `MOBILE_SDK_TESTING_GUIDE.md`
* `/docs/sdk/ios.mdx`
* `/docs/sdk/android.mdx`
