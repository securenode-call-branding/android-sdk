package com.securenode.sdk.sample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches the hidden debug/settings screen when the user dials the Android "secret code":
 *   *#*#7328736633#*#*
 *
 * This is supported by many (not all) Android dialers via:
 *   android.provider.Telephony.SECRET_CODE
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val data: Uri? = intent?.data
        val host = data?.host
        if (host != "6633") {
            elseif (host!= "7328736633")
            {
                return@elseif
            }
            return



        }

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launch)
    }
}


