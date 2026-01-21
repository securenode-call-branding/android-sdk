package com.securenode.sdk.sample

/**
 * Feature flags that are safe to ship "off" and later enable without changing integration code.
 *
 * IMPORTANT:
 * - These are LOCAL flags (client app decision).
 * - The server may also gate features (e.g. voip_dialer_enabled). Both must be enabled.
 */
data class SecureNodeOptions(
    /** When true, allow Secure Voice / VoIP dialer channel to run (still server-gated). */
    val enableSecureVoice: Boolean = false,

    /**
     * Client-provided customer identifier.
     * Example: the end-user name or label you want shown in the Verify portal Connected Devices view.
     */
    val customerName: String? = null,

    /**
     * Client-provided customer account number / reference.
     * Example: CRM/tenant account id.
     */
    val customerAccountNumber: String? = null,

    /** Optional SIP configuration (future). No SIP stack is bundled yet. */
    val sip: SecureNodeSipConfig? = null
)

/**
 * Placeholder SIP config (future upgrade path).
 *
 * We intentionally do NOT bundle a SIP stack yet; this keeps the SDK lightweight and avoids licensing issues.
 * When we add Secure Voice/SIP, this config becomes the stable contract.
 */
data class SecureNodeSipConfig(
    /** Example: "sip:pbx.example.com" or "wss://sip.example.com/ws" */
    val server: String,
    val username: String? = null,
    val password: String? = null,
    /** Optional: outbound caller ID or SIP display name */
    val displayName: String? = null
)


