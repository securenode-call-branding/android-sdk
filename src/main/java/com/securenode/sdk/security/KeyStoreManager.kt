package com.securenode.sdk.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure key storage manager using Android Keystore
 * 
 * Stores API keys securely using EncryptedSharedPreferences.
 */
class KeyStoreManager(private val context: Context) {
    private val masterKey: MasterKey
    private val encryptedPrefs: android.content.SharedPreferences

    init {
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            "securenode_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save API key securely
     */
    fun saveApiKey(apiKey: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key", e)
        }
    }

    /**
     * Get stored API key
     */
    fun getApiKey(): String? {
        return try {
            encryptedPrefs.getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key", e)
            null
        }
    }

    /**
     * Clear stored API key
     */
    fun clearApiKey() {
        try {
            encryptedPrefs.edit()
                .remove(KEY_API_KEY)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API key", e)
        }
    }

    companion object {
        private const val TAG = "KeyStoreManager"
        private const val KEY_API_KEY = "api_key"
    }
}

