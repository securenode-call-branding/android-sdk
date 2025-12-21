package com.securenode.sdk.sample

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VoipDemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voip_demo)

        val status = findViewById<TextView>(R.id.status)
        val number = findViewById<EditText>(R.id.number)
        val outgoing = findViewById<Button>(R.id.btnOutgoing)
        val incoming = findViewById<Button>(R.id.btnIncoming)

        if (Build.VERSION.SDK_INT < 26) {
            status.text = "Status: unsupported (Android < 8)"
            outgoing.isEnabled = false
            incoming.isEnabled = false
            return
        }

        val handle = VoipPhoneAccount.ensureRegistered(applicationContext)
        status.text = "Status: phone account registered"

        outgoing.setOnClickListener {
            val remote = number.text?.toString()?.trim().orEmpty()
            if (remote.isBlank()) {
                status.text = "Status: enter a number"
                return@setOnClickListener
            }
            try {
                val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                tm.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, remote, null), extras)
                status.text = "Status: outgoing call requested"
            } catch (e: Exception) {
                status.text = "Status: outgoing failed (${e.message ?: "unknown"})"
            }
        }

        incoming.setOnClickListener {
            val remote = number.text?.toString()?.trim().orEmpty()
            if (remote.isBlank()) {
                status.text = "Status: enter a number"
                return@setOnClickListener
            }
            try {
                val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts(PhoneAccount.SCHEME_TEL, remote, null))
                }
                tm.addNewIncomingCall(handle, extras)
                status.text = "Status: incoming call requested"
            } catch (e: Exception) {
                status.text = "Status: incoming failed (${e.message ?: "unknown"})"
            }
        }
    }
}


