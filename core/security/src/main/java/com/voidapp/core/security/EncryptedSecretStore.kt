package com.voidapp.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The only place OAuth tokens and AI provider keys are stored.
 *
 * Backed by [EncryptedSharedPreferences]: keys are encrypted with AES-256-SIV
 * and values with AES-256-GCM, both under an Android Keystore master key.
 * Nothing here is ever logged.
 */
class EncryptedSecretStore private constructor(
    private val prefs: SharedPreferences,
) : SecretStore {

    constructor(context: Context) : this(create(context))

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).commit()
    }

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).commit()
    }

    override fun saveProviderKey(providerId: String, key: String) {
        prefs.edit().putString(providerKey(providerId), key).commit()
    }

    override fun getProviderKey(providerId: String): String? =
        prefs.getString(providerKey(providerId), null)

    override fun clearProviderKey(providerId: String) {
        prefs.edit().remove(providerKey(providerId)).commit()
    }

    override fun clearAll() {
        prefs.edit().clear().commit()
    }

    private fun providerKey(providerId: String) = "$PROVIDER_KEY_PREFIX$providerId"

    companion object {
        const val PREFS_NAME = "void_secrets"
        private const val KEY_TOKEN = "gh_token"
        private const val PROVIDER_KEY_PREFIX = "provider_key_"

        /** Test seam: hidden from Java callers so the encrypted path cannot be bypassed. */
        @JvmSynthetic
        internal fun forTest(prefs: SharedPreferences): SecretStore = EncryptedSecretStore(prefs)

        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
