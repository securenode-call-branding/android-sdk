package io.securenode.branding

data class SecureNodeConfig(
    val baseUrl: String,
    val apiKey: String,
    val enableHttpLogging: Boolean = false,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val sdkVersion: String = BuildConfig.VERSION_NAME,
    val syncSinceHoursDefault: Int = 24 * 30,

    /**
     * Optional: sync portal branding directory into the device Contacts so the stock dialer can show
     * branded names even when Call Screening isn't enabled.
     *
     * Requires host app to request/grant READ_CONTACTS + WRITE_CONTACTS at runtime.
     */
    val enableContactsBranding: Boolean = true,

    /**
     * Optional: also write brand logo photos into the SecureNode-managed Contacts.
     *
     * Notes:
     * - Best-effort (network/OEM/contacts provider may fail).
     * - Only applies to SecureNode-managed contacts created by the SDK.
     */
    val enableContactsBrandingPhotos: Boolean = true,

    /**
     * To avoid OEM dialer quirks and contact bloat, we split a brand into multiple contacts when it
     * has more than this many numbers.
     */
    val contactsBrandingMaxNumbersPerContact: Int = 200
)
