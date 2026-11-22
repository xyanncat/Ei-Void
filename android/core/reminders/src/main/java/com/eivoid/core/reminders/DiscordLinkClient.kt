package com.eivoid.core.reminders

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** A Discord account linked to the signed-in Clerk user. */
@Serializable
data class DiscordIdentity(
    val id: String,
    val username: String = "",
)

/** The subset of `GET /api/v1/account` that Settings needs. */
@Serializable
data class EiVoidAccount(
    val email: String = "",
    val displayName: String = "",
    val discord: DiscordIdentity? = null,
)

@Serializable
private data class DiscordLinkRequest(val accessToken: String)

@Serializable
private data class DiscordLinkResponse(
    val ok: Boolean = false,
    val discord: DiscordIdentity? = null,
)

@Serializable
private data class OkResponse(val ok: Boolean = true)

/**
 * A Discord-link failure with the HTTP status and a short, safe message.
 * The caller never sees the access token or the Clerk token.
 */
class DiscordLinkException(
    val statusCode: Int,
    message: String,
) : IOException(message)

/** The Discord linking surface Settings depends on. */
interface DiscordLinkService {
    suspend fun link(accessToken: String): Result<DiscordIdentity>
    suspend fun unlink(): Result<Unit>
    suspend fun test(): Result<Unit>
    suspend fun account(): Result<EiVoidAccount>
}

/**
 * The app side of the Ei Snore notification connection.
 *
 * OAuth happens in [com.eivoid.feature.settings.DiscordAuthActivity]; this
 * client sends the resulting Discord access token to the Ei Void backend with
 * the Clerk session token. The backend verifies the Discord identity, checks
 * guild membership, and stores the link. The Discord token is used once and is
 * never persisted by this class.
 */
class DiscordLinkClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String? = { ReminderSyncClient.clerkToken() },
) : DiscordLinkService {

    override suspend fun link(accessToken: String): Result<DiscordIdentity> = call { token ->
        val response = httpClient.post(endpoint("/api/v1/discord/link")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(DiscordLinkRequest(accessToken))
        }
        ensureSuccess(response)
        val body: DiscordLinkResponse = response.body()
        body.discord ?: throw DiscordLinkException(502, "The server did not return the linked Discord account.")
    }

    override suspend fun unlink(): Result<Unit> = call { token ->
        val response = httpClient.delete(endpoint("/api/v1/discord/link")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        ensureSuccess(response)
    }

    override suspend fun test(): Result<Unit> = call { token ->
        val response = httpClient.post(endpoint("/api/v1/test/discord")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(OkResponse())
        }
        ensureSuccess(response)
    }

    override suspend fun account(): Result<EiVoidAccount> = call { token ->
        val response = httpClient.get(endpoint("/api/v1/account")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        ensureSuccess(response)
        response.body()
    }

    private suspend fun <T> call(block: suspend (String) -> T): Result<T> {
        if (baseUrl.isBlank()) {
            return Result.failure(
                DiscordLinkException(0, "Ei Void is not configured in this build."),
            )
        }
        val token = tokenProvider()
            ?: return Result.failure(
                DiscordLinkException(401, "Sign in with Clerk before connecting Discord."),
            )
        return try {
            Result.success(block(token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DiscordLinkException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(
                DiscordLinkException(0, e.message ?: "Could not reach the Ei Void service."),
            )
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + path

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val code = runCatching {
            Json.parseToJsonElement(response.bodyAsText())
                .let { element ->
                    (element as? kotlinx.serialization.json.JsonObject)
                        ?.get("error")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }
        }.getOrNull().orEmpty()
        throw DiscordLinkException(
            response.status.value,
            messageFor(response.status.value, code),
        )
    }

    private fun messageFor(status: Int, code: String): String = when {
        code == "guild_membership_required" ->
            "Join the Ei Void Discord server with this account, then connect again."
        code == "invalid_discord_token" -> "Discord rejected the authorization. Try again."
        code == "invalid_clerk_token" -> "Your Clerk session expired. Sign in again."
        code == "discord_not_linked" -> "Connect Discord first."
        code == "discord_test_unavailable" ->
            "The server does not have a Discord bot token configured yet."
        code == "discord_send_failed" -> "Discord could not deliver the test message."
        code == "discord_dm_blocked" || code == "discord_dm_unavailable" ->
            "Discord could not send a DM. The bot and this account must share the server."
        code == "discord_unreachable" || status == 503 ->
            "Discord is unavailable right now. Try again in a moment."
        status == 429 -> "Discord rate-limited the request. Wait a minute and retry."
        status == 401 -> "Your Clerk session expired. Sign in again."
        status == 403 -> "This account cannot link Discord."
        else -> "Discord request failed (HTTP $status)."
    }
}
