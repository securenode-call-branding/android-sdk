package io.securenode.branding.data.repo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import io.securenode.branding.BrandingResult
import io.securenode.branding.SecureNodeConfig
import io.securenode.branding.SecureNodeError
import io.securenode.branding.Iso8601
import io.securenode.branding.contacts.ContactsBrandingSync
import io.securenode.branding.data.db.AppDatabase
import io.securenode.branding.data.db.entity.BrandingEntity
import io.securenode.branding.data.db.entity.EventEntity
import io.securenode.branding.net.ApiClient
import io.securenode.branding.net.BrandingEventRequest
import io.securenode.branding.net.BrandingSyncResponse
import io.securenode.branding.net.DeviceRegisterRequest
import io.securenode.branding.net.BrandingSyncItem
import io.securenode.branding.net.DeviceCapabilities
import io.securenode.branding.net.DeviceUpdateRequest
import io.securenode.branding.debug.DebugGate
import io.securenode.branding.debug.DebugPolicy
import io.securenode.branding.debug.DebugUploader
import io.securenode.branding.telemetry.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.security.MessageDigest

class BrandingRepository private constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val config: SecureNodeConfig
) {
    private val api = ApiClient.create(config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        fun create(context: Context, db: AppDatabase, config: SecureNodeConfig) =
            BrandingRepository(context, db, config)
    }

    fun registerDeviceBestEffort() {
        scope.launch {
            try {
                val deviceId = stableDeviceId()
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                val appVersion = config.appVersion ?: pkg.versionName
                api.deviceRegister(
                    DeviceRegisterRequest(
                        deviceId = deviceId,
                        platform = "android",
                        deviceType = Build.MODEL,
                        osVersion = Build.VERSION.RELEASE,
                        appVersion = appVersion,
                        sdkVersion = config.sdkVersion
                    )
                )

                // Idempotent device state update (best-effort, no logs/telemetry mixed in).
                val contactsPermitted = ContactsBrandingSync.hasRequiredPermissions(context)
                api.deviceUpdate(
                    DeviceUpdateRequest(
                        deviceId = deviceId,
                        platform = "android",
                        osVersion = Build.VERSION.RELEASE,
                        appVersion = appVersion,
                        sdkVersion = config.sdkVersion,
                        capabilities = DeviceCapabilities(
                            contactsEnabled = config.enableContactsBranding && contactsPermitted,
                            contactsPhotosEnabled = config.enableContactsBrandingPhotos && contactsPermitted
                        ),
                        lastSeen = Iso8601.nowUtcIso()
                    )
                )
            } catch (t: Throwable) {
                Logger.w("deviceRegister failed (best-effort)", t)
            }
        }
    }

    suspend fun resolveBranding(e164: String, surface: String): BrandingResult = withContext(Dispatchers.IO) {
        val cached = db.brandingDao().get(e164)
        if (cached != null) {
            enqueueEvent(e164, "displayed", surface)
            return@withContext BrandingResult(
                e164 = e164,
                brandName = cached.brandName,
                logoUrl = cached.logoUrl,
                callReason = cached.callReason,
                display = true,
                outcome = BrandingResult.Outcome.DISPLAYED
            )
        }

        try {
            val resp = api.brandingLookup(e164, stableDeviceId())
            val brandName = resp.brandName
            val logoUrl = resp.logoUrl
            val callReason = resp.callReason
            val display = resp.display

            val outcome = when {
                display == false -> BrandingResult.Outcome.DISABLED
                brandName.isNullOrBlank() && logoUrl.isNullOrBlank() && callReason.isNullOrBlank() -> BrandingResult.Outcome.NO_MATCH
                else -> BrandingResult.Outcome.DISPLAYED
            }

            if (outcome == BrandingResult.Outcome.DISPLAYED) {
                db.brandingDao().upsert(
                    BrandingEntity(
                        phoneE164 = e164,
                        brandName = brandName,
                        logoUrl = logoUrl,
                        callReason = callReason,
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }

            enqueueEvent(
                e164,
                when (outcome) {
                    BrandingResult.Outcome.DISPLAYED -> {
                        "displayed"
                    }
                    BrandingResult.Outcome.NO_MATCH -> {
                        "no_match"
                    }
                    BrandingResult.Outcome.DISABLED -> {
                        "disabled"
                    }
                    BrandingResult.Outcome.ERROR -> {
                        "error"
                    }
                },
                surface
            )

            BrandingResult(
                e164 = e164,
                brandName = brandName,
                logoUrl = logoUrl,
                callReason = callReason,
                display = display,
                outcome = outcome,
                config = resp.config?.toSimpleMap(),
                limits = resp.limits?.toSimpleMap()
            )
        } catch (t: Throwable) {
            val err = SecureNodeError.fromThrowable(t)
            enqueueEvent(e164, "error", surface)
            throw err
        }
    }

    suspend fun syncBrandingBestEffort(sinceIso: String?): BrandingSyncResponse = withContext(Dispatchers.IO) {
        val deviceId = stableDeviceId()
        val resp = api.brandingSync(sinceIso, deviceId)
        val entities = resp.branding.mapNotNull { item ->
            if (item.phoneE164.isBlank()) null else BrandingEntity(
                phoneE164 = item.phoneE164,
                brandName = item.brandName,
                logoUrl = item.logoUrl,
                callReason = item.callReason,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }
        if (entities.isNotEmpty()) db.brandingDao().upsertAll(entities)

        // basic retention: keep last 90 days
        val cutoff = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L)
        db.brandingDao().deleteOlderThan(cutoff)

        // Apply server debug policy if present (best-effort; never blocks sync).
        try {
            val p = parseDebugPolicy(resp.config)
            if (p != null) {
                DebugGate.applyServerPolicy(context, p)
                if (p.requestUpload && p.allowExport && p.isActive()) {
                    DebugUploader.upload(p)
                }
            }
        } catch (_: Throwable) {}

        // Best-effort device update on each sync (idempotent).
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersion = config.appVersion ?: pkg.versionName
            val contactsPermitted = ContactsBrandingSync.hasRequiredPermissions(context)
            api.deviceUpdate(
                DeviceUpdateRequest(
                    deviceId = deviceId,
                    platform = "android",
                    osVersion = Build.VERSION.RELEASE,
                    appVersion = appVersion,
                    sdkVersion = config.sdkVersion,
                    capabilities = DeviceCapabilities(
                        contactsEnabled = config.enableContactsBranding && contactsPermitted,
                        contactsPhotosEnabled = config.enableContactsBrandingPhotos && contactsPermitted,
                        backgroundRefresh = true
                    ),
                    lastSeen = Iso8601.nowUtcIso()
                )
            )
        } catch (_: Throwable) {}
        resp
    }

    private fun parseDebugPolicy(configMap: Map<String, JsonElement>?): DebugPolicy? {
        val m = configMap ?: return null
        val debugUi = m["debug_ui"] ?: return null
        val o = debugUi as? kotlinx.serialization.json.JsonObject ?: return null

        val enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false
        val requestUpload = (o["request_upload"] as? JsonPrimitive)?.booleanOrNull ?: false
        val allowExport = (o["allow_export"] as? JsonPrimitive)?.booleanOrNull ?: false
        val expiresAtIso = (o["expires_at"] as? JsonPrimitive)?.contentOrNull
        val expiresEpoch = try {
            if (expiresAtIso.isNullOrBlank()) null
            else Iso8601.parseUtcIsoToEpochMs(expiresAtIso)
        } catch (_: Throwable) {
            null
        }

        return DebugPolicy(
            serverEnabled = enabled,
            expiresAtEpochMs = expiresEpoch,
            allowExport = allowExport,
            requestUpload = requestUpload
        )
    }

    suspend fun syncContactsBrandingBestEffort(items: List<BrandingSyncItem>) = withContext(Dispatchers.IO) {
        if (!config.enableContactsBranding) return@withContext
        if (!ContactsBrandingSync.hasRequiredPermissions(context)) {
            Logger.w("Contacts branding sync skipped: missing contacts permissions")
            return@withContext
        }
        ContactsBrandingSync(
            context = context,
            maxNumbersPerContact = config.contactsBrandingMaxNumbersPerContact,
            enablePhotos = config.enableContactsBrandingPhotos
        )
            .syncFromPortal(items)
    }

    suspend fun syncContactsBrandingBestEffortFull() = withContext(Dispatchers.IO) {
        if (!config.enableContactsBranding) return@withContext
        if (!ContactsBrandingSync.hasRequiredPermissions(context)) {
            Logger.w("Contacts branding sync skipped: missing contacts permissions")
            return@withContext
        }
        try {
            val resp = api.brandingSync(null, stableDeviceId())
            ContactsBrandingSync(
                context = context,
                maxNumbersPerContact = config.contactsBrandingMaxNumbersPerContact,
                enablePhotos = config.enableContactsBrandingPhotos
            )
                .syncFromPortal(resp.branding)
        } catch (t: Throwable) {
            Logger.w("Contacts branding sync failed", t)
        }
    }

    suspend fun uploadPendingEvents(batchSize: Int = 50): Int = withContext(Dispatchers.IO) {
        val pending = db.eventDao().pending(batchSize)
        var uploadedCount = 0

        for (evt in pending) {
            try {
                val deviceId = stableDeviceId()
                api.brandingEvent(
                    BrandingEventRequest(
                        phoneNumberE164 = evt.phoneE164,
                        outcome = evt.outcome,
                        surface = evt.surface,
                        deviceId = deviceId,
                        displayedAt = Iso8601.formatUtcIso(evt.displayedAtEpochMs ?: evt.createdAtEpochMs),
                        idempotencyKey = evt.idempotencyKey,
                        eventKey = evt.idempotencyKey,
                        meta = parseMetaJson(evt.metaJson)
                    )
                )

                db.eventDao().markUploaded(evt.id)
                uploadedCount++
            } catch (t: Throwable) {
                val err = SecureNodeError.fromThrowable(t)
                db.eventDao().update(
                    evt.copy(
                        attempts = evt.attempts + 1,
                        lastError = "${err.code}: ${err.message}"
                    )
                )
                if (err is SecureNodeError.Unauthorized) throw err
            }
        }

        val cutoff = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
        db.eventDao().deleteUploadedOlderThan(cutoff)
        uploadedCount
    }

    private suspend fun enqueueEvent(
        e164: String,
        outcome: String,
        surface: String?,
        displayedAtEpochMs: Long? = null,
        meta: Map<String, Any?>? = null
    ) {
        val ts = displayedAtEpochMs ?: System.currentTimeMillis()
        val idk = idempotencyKeyFor(e164, outcome, ts)
        db.eventDao().insert(
            EventEntity(
                phoneE164 = e164,
                outcome = outcome,
                surface = surface,
                displayedAtEpochMs = displayedAtEpochMs,
                idempotencyKey = idk,
                metaJson = metaJson(meta),
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun deviceId(): String = stableDeviceId()

    @SuppressLint("HardwareIds")
    private fun stableDeviceId(): String {
        config.deviceId?.let { return it }
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val seed = "${context.packageName}|$androidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun idempotencyKeyFor(phoneE164: String, outcome: String, displayedAtEpochMs: Long): String {
        val deviceId = stableDeviceId()
        val seed = "$deviceId|$phoneE164|$outcome|$displayedAtEpochMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40)
    }

    private fun metaJson(meta: Map<String, Any?>?): String? {
        if (meta == null) return null
        return try {
            val obj = buildJsonObject {
                for ((k, v) in meta) {
                    put(
                        k,
                        when (v) {
                            null -> JsonNull
                            is Boolean -> JsonPrimitive(v)
                            is Number -> JsonPrimitive(v)
                            else -> JsonPrimitive(v.toString())
                        }
                    )
                }
            }
            obj.toString()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseMetaJson(metaJson: String?): Map<String, JsonElement>? {
        if (metaJson.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(metaJson)
            obj as? kotlinx.serialization.json.JsonObject
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Debug console snapshot. Avoids sensitive fields.
     */
    suspend fun debugStateSnapshot(): JSONObject = withContext(Dispatchers.IO) {
        val o = JSONObject()
        try {
            o.put("device_id_prefix", stableDeviceId().take(8) + "...")
        } catch (_: Throwable) {}
        try {
            o.put("cached_branding_count", db.brandingDao().count())
        } catch (_: Throwable) {}
        try {
            o.put("pending_events", db.eventDao().countPending())
        } catch (_: Throwable) {}
        o
    }
}

private fun Map<String, JsonElement>.toSimpleMap(): Map<String, Any?> =
    entries.associate { (k, v) ->
        k to when (v) {
            is JsonNull -> null
            is JsonPrimitive ->
                if (v.isString) v.content
                else v.booleanOrNull ?: v.longOrNull ?: v.doubleOrNull ?: v.content
            else -> v.toString()
        }
    }
