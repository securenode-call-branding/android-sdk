package io.securenode.branding

data class BrandingResult(
    val e164: String,
    val brandName: String? = null,
    val logoUrl: String? = null,
    val callReason: String? = null,
    val display: Boolean? = null,
    val outcome: Outcome,
    val config: Map<String, Any?>? = null,
    val limits: Map<String, Any?>? = null,
    val error: SecureNodeError? = null
) {
    enum class Outcome { DISPLAYED, NO_MATCH, DISABLED, ERROR }

    companion object {
        fun error(e164: String, error: SecureNodeError) = BrandingResult(
            e164 = e164,
            outcome = Outcome.ERROR,
            error = error
        )
    }
}
