package com.eivoid.feature.auth

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.session.fetchToken
import com.eivoid.core.common.auth.AuthConfiguration
import com.eivoid.core.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clerk sign-in plus the exchange that turns a Clerk session into the GitHub
 * token JGit needs. The GitHub token is stored exactly where the device flow
 * stored it, so the rest of the app is unchanged.
 */
@Singleton
class ClerkAuthRepository @Inject constructor(
    private val tokenClient: ClerkTokenClient,
    private val secretStore: SecretStore,
    private val configuration: AuthConfiguration,
) {

    val enabled: Boolean get() = configuration.clerkEnabled

    /** Reads the Clerk session JWT and exchanges it for a GitHub token. */
    suspend fun connectGithub(): Result<Unit> {
        val session = Clerk.session
            ?: return Result.failure(ClerkAuthException("Your session is not ready. Try again."))

        var jwt: String? = null
        var readError: Throwable? = null
        session.fetchToken()
            .onSuccess { jwt = it.jwt }
            .onFailure {
                readError = it.throwable ?: ClerkAuthException("Could not read the session.")
            }

        val sessionToken = jwt
            ?: return Result.failure(
                readError ?: ClerkAuthException("Could not read the session."),
            )

        return tokenClient.fetchGithubToken(sessionToken)
            .onSuccess { token -> secretStore.saveToken(token.token) }
            .map { }
    }

    /** Ends the Clerk session and removes the GitHub token it produced. */
    suspend fun signOut() {
        runCatching { Clerk.auth.signOut() }
        secretStore.clearToken()
    }
}
