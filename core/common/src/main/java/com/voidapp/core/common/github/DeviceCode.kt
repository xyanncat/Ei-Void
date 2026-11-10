package com.voidapp.core.common.github

/** The code the user types into GitHub, plus the polling parameters. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int,
)

sealed interface TokenResult {
    data class Success(val token: String) : TokenResult
    data object AuthorizationPending : TokenResult
    data object SlowDown : TokenResult
    data object Expired : TokenResult
    data class Failed(val message: String) : TokenResult
}
