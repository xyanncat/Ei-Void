package com.eivoid.feature.auth

import com.eivoid.core.common.auth.AuthConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The GitHub token the auth gateway returns for a signed-in Clerk user. */
data class GithubToken(
    val token: String,
    val scopes: List<String>,
    val expiresAt: Long?,
)

/** A problem connecting Clerk to GitHub; the message is safe to show. */
open class ClerkAuthException(message: String) : Exception(message)

/** The Clerk account exists but has no GitHub connection yet. */
class GithubNotConnectedException :
    ClerkAuthException("Connect GitHub to your Clerk account, then try again.")

/**
 * Calls the auth gateway in `server/` with the Clerk session token. The
 * gateway is the only place that holds the Clerk secret; this client only
 * ever sees the GitHub token.
 */
@Singleton
class ClerkTokenClient @Inject constructor(
    private val client: HttpClient,
    private val configuration: AuthConfiguration,
) {

    suspend fun fetchGithubToken(sessionToken: String): Result<GithubToken> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.get(
                    "${configuration.backendUrl.trimEnd('/')}/github-token",
                ) {
                    header(HttpHeaders.Authorization, "Bearer $sessionToken")
                    accept(ContentType.Application.Json)
                }

                when (response.status.value) {
                    200 -> {
                        val body: GithubTokenDto = response.body()
                        GithubToken(
                            token = body.token,
                            scopes = body.scopes,
                            expiresAt = body.expiresAt,
                        )
                    }

                    401 -> throw ClerkAuthException("Your sign-in expired. Try again.")
                    404 -> throw GithubNotConnectedException()

                    409 -> throw ClerkAuthException(
                        "GitHub is refreshing the connection. Try again in a moment.",
                    )

                    else -> throw ClerkAuthException(
                        "The sign-in service is unavailable. Try again.",
                    )
                }
            }
        }
}

@Serializable
private data class GithubTokenDto(
    val token: String,
    val scopes: List<String> = emptyList(),
    val expiresAt: Long? = null,
)
