package io.securenode.branding.net

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BrandingLookupResponse(
    val e164: String? = null,
    @SerialName("brand_name") val brandName: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("call_reason") val callReason: String? = null,
    val display: Boolean? = null,
    val config: Map<String, JsonElement>? = null,
    val limits: Map<String, JsonElement>? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BrandingSyncItem(
    @SerialName("phone_number_e164") val phoneE164: String,
    @SerialName("brand_name") val brandName: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("call_reason") val callReason: String? = null,
    @SerialName("brand_id") val brandId: String,
    @SerialName("updated_at") val updatedAt: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BrandingSyncResponse(
    val branding: List<BrandingSyncItem> = emptyList(),
    @SerialName("synced_at") val syncedAt: String? = null,
    val config: Map<String, JsonElement>? = null
)

@Serializable
data class BrandingSyncAckRequest(
    @SerialName("e164_numbers") val e164Numbers: List<String>? = null,
    @SerialName("last_synced_at") val lastSyncedAt: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class BrandingEventRequest(
    @SerialName("phone_number_e164") val phoneNumberE164: String,
    val outcome: String,
    val surface: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("branding_request_id") val brandingRequestId: String? = null,
    @SerialName("displayed_at") val displayedAt: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    @SerialName("event_key") val eventKey: String? = null,
    val meta: Map<String, JsonElement>? = null
)


@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class DeviceRegisterRequest(
    @SerialName("device_id") val deviceId: String,
    val platform: String,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("sdk_version") val sdkVersion: String? = null,
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class DeviceCapabilities(
    @SerialName("contacts_enabled") val contactsEnabled: Boolean? = null,
    @SerialName("contacts_photos_enabled") val contactsPhotosEnabled: Boolean? = null,
    @SerialName("call_directory_enabled") val callDirectoryEnabled: Boolean? = null,
    @SerialName("background_refresh") val backgroundRefresh: Boolean? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class DeviceUpdateRequest(
    @SerialName("device_id") val deviceId: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("sdk_version") val sdkVersion: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    val capabilities: DeviceCapabilities? = null,
    @SerialName("last_seen") val lastSeen: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SuccessResponse(val success: Boolean = true)
