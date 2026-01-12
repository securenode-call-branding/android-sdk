package io.securenode.branding.util

import android.telephony.PhoneNumberUtils

object PhoneNumberUtil {
    fun normalizeToE164(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.startsWith("+")) return s
        return PhoneNumberUtils.stripSeparators(s)
    }
}
