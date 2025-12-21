package com.securenode.sdk.sample

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

object VoipPhoneAccount {
    private const val ACCOUNT_ID = "securenode_voip"
    private const val LABEL = "SecureNode VoIP (Demo)"

    fun getHandle(context: Context): PhoneAccountHandle {
        return PhoneAccountHandle(
            ComponentName(context, VoipConnectionService::class.java),
            ACCOUNT_ID
        )
    }

    fun ensureRegistered(context: Context): PhoneAccountHandle {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = getHandle(context)

        val account = PhoneAccount.builder(handle, LABEL)
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()

        tm.registerPhoneAccount(account)
        return handle
    }
}


