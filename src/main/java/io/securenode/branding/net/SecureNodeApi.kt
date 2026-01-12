package io.securenode.branding.net

import kotlinx.serialization.json.JsonElement
import retrofit2.http.*

interface SecureNodeApi {
    @GET("/api/mobile/branding/lookup")
    suspend fun brandingLookup(
        @Query("e164") e164: String,
        @Query("device_id") deviceId: String? = null
    ): BrandingLookupResponse

    @GET("/api/mobile/branding/sync")
    suspend fun brandingSync(
        @Query("since") sinceIso: String? = null,
        @Query("device_id") deviceId: String? = null
    ): BrandingSyncResponse

    @POST("/api/mobile/branding/event")
    suspend fun brandingEvent(@Body body: BrandingEventRequest): JsonElement

    @POST("/api/mobile/device/register")
    suspend fun deviceRegister(@Body body: DeviceRegisterRequest): JsonElement

    @POST("/api/mobile/device/update")
    suspend fun deviceUpdate(@Body body: DeviceUpdateRequest): SuccessResponse
}
