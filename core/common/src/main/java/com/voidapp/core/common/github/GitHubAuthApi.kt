package com.voidapp.core.common.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub's OAuth device flow. Abstracted so tests can fake it. */
interface GitHubAuthApi {
    suspend fun requestDeviceCode(): DeviceCode
    suspend fun pollAccessToken(deviceCode: String): TokenResult
}

/**
 * Ktor implementation against github.com. No client secret is used: the
 * device flow is designed for public clients.
 */
class KtorGitHubAuthApi(
    private val client: HttpClient,
    private val clientId: String,
    private val scopes: List<String> = listOf("repo", "read:user", "workflow"),
) : GitHubAuthApi {

    override suspend fun requestDeviceCode(): DeviceCode {
        val response: DeviceCodeResponse = client.submitForm(
            url = DEVICE_CODE_URL,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("scope", scopes.joinToString(" "))
            },
        ) {
            header(HttpHeaders.Accept, "application/json")
        }.body()

        return DeviceCode(
            deviceCode = response.deviceCode,
            userCode = response.userCode,
            verificationUri = response.verificationUri,
            expiresIn = response.expiresIn,
            interval = response.interval,
        )
    }

    override suspend fun pollAccessToken(deviceCode: String): TokenResult {
        val response: TokenResponse = client.submitForm(
            url = ACCESS_TOKEN_URL,
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("device_code", deviceCode)
                append("grant_type", DEVICE_GRANT_TYPE)
            },
        ) {
            header(HttpHeaders.Accept, "application/json")
        }.body()

        return when {
            !response.accessToken.isNullOrBlank() -> TokenResult.Success(response.accessToken!!)
            response.error == "authorization_pending" -> TokenResult.AuthorizationPending
            response.error == "slow_down" -> TokenResult.SlowDown
            response.error == "expired_token" -> TokenResult.Expired
            else -> TokenResult.Failed(
                response.errorDescription ?: response.error ?: "unknown_error",
            )
        }
    }

    private companion object {
        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    }
}

@Serializable
private data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
