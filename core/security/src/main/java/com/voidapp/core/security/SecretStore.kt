package com.voidapp.core.security

/**
 * The app's credential contract. Implemented by [EncryptedSecretStore] in
 * production and by in-memory fakes in tests.
 */
interface SecretStore {
    fun saveToken(token: String)
    fun getToken(): String?

    fun clearToken()

    fun saveProviderKey(providerId: String, key: String)
    fun getProviderKey(providerId: String): String?

    fun clearProviderKey(providerId: String)

    /** Clears every credential. Called on logout and on credential reset. */
    fun clearAll()
}
