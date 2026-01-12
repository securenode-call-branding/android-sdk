package io.securenode.branding

interface BrandingCallback {
    fun onBrandingResolved(result: io.securenode.branding.BrandingResult)
    fun onError(error: SecureNodeError) { /* optional */ }
}
