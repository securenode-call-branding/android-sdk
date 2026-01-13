package io.securenode.branding.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.securenode.branding.SecureNodeBranding
import io.securenode.branding.SecureNodeConfig

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOTE: Replace with your real values (or generate via portal-configurator/index.html).
        val cfg = SecureNodeConfig(
            baseUrl = "https://your-api.example.com",
            apiKey = "YOUR_API_KEY",
            hostAppName = "SecureNode SDK Sample"
        )
        SecureNodeBranding.initialize(this, cfg)

        setContentView(
            TextView(this).apply {
                text = "SecureNode SDK sample app.\n\nEdit MainActivity to configure baseUrl/apiKey."
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }
        )
    }
}

