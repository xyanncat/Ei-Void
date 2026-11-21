package com.eivoid.feature.auth

import com.eivoid.core.security.SecretStore
import javax.inject.Inject

/**
 * The local session: the GitHub token that clone/push needs. Clerk owns
 * identity; this repository only reads and clears the token the auth gateway
 * produced.
 */
class AuthRepository @Inject constructor(
    private val secretStore: SecretStore,
) {

    fun isLoggedIn(): Boolean = !secretStore.getToken().isNullOrBlank()

    /**
     * Debug only: stores a placeholder token so the signed-in screens can be
     * exercised without Clerk keys. The login screen only offers this when the
     * debug flag is passed, so release builds never call it.
     */
    fun debugSignIn() {
        secretStore.saveToken(DEBUG_TOKEN)
    }

    fun logout() {
        // Spec: every secret is cleared on logout.
        secretStore.clearAll()
    }

    private companion object {
        const val DEBUG_TOKEN = "debug-token"
    }
}
